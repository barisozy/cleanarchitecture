package com.barisozy.cleanarchitecture.infrastructure.persistence.todo;

import com.barisozy.cleanarchitecture.application.ApplicationException;
import com.barisozy.cleanarchitecture.application.PageResult;
import com.barisozy.cleanarchitecture.application.todo.TodoQueryPort;
import com.barisozy.cleanarchitecture.application.todo.TodoView;
import com.barisozy.cleanarchitecture.domain.todo.Todo;
import com.barisozy.cleanarchitecture.domain.todo.TodoId;
import com.barisozy.cleanarchitecture.domain.todo.TodoRepository;
import com.barisozy.cleanarchitecture.domain.todo.TodoStatus;
import com.barisozy.cleanarchitecture.domain.user.UserId;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * The persistence adapter of the {@link Todo} aggregate. It implements both the write port and the
 * read-model port, because both are backed by the same table and the same mapper.
 *
 * <p>The adapter joins the transaction of its caller; it never starts one. The optimistic lock is
 * the {@code version} column of the row; a lost race is translated into an application conflict.
 */
@Component
public class TodoPersistenceAdapter implements TodoRepository, TodoQueryPort {

    /**
     * Newest first, with the identifier as a tie-breaker. Without the second key, two todos created
     * in the same millisecond could swap places between requests, and a client paging through the
     * list would see the same row twice or miss one.
     */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final TodoJpaRepository repository;
    private final TodoJpaMapper mapper;

    public TodoPersistenceAdapter(TodoJpaRepository repository, TodoJpaMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Todo> findById(TodoId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Todo insert(Todo todo) {
        TodoJpaEntity saved = repository.saveAndFlush(mapper.toEntity(todo));
        return mapper.toDomain(saved);
    }

    @Override
    public Todo update(Todo todo) {
        TodoJpaEntity entity = managed(todo.id());
        mapper.apply(entity, todo);
        try {
            TodoJpaEntity saved = repository.saveAndFlush(entity);
            return mapper.toDomain(saved);
        } catch (OptimisticLockingFailureException exception) {
            throw conflict();
        }
    }

    @Override
    public void delete(Todo todo) {
        try {
            repository.delete(managed(todo.id()));
            repository.flush();
        } catch (OptimisticLockingFailureException exception) {
            throw conflict();
        }
    }

    @Override
    public PageResult<TodoView> findPage(@Nullable UserId ownerId, @Nullable TodoStatus status, int page, int size) {
        Specification<TodoJpaEntity> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (ownerId != null) {
                predicates.add(criteriaBuilder.equal(root.get("ownerId"), ownerId.value()));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
        Page<TodoJpaEntity> entities = repository.findAll(specification, PageRequest.of(page, size, NEWEST_FIRST));
        List<TodoView> views =
                entities.getContent().stream().map(mapper::toView).toList();
        return new PageResult<>(views, page, size, entities.getTotalElements());
    }

    /** The persistence signal becomes part of the application contract at this boundary. */
    private static ApplicationException conflict() {
        return ApplicationException.conflict("concurrency.conflict", "The todo was modified by another request.");
    }

    /**
     * Returns the managed entity of the aggregate. A row that disappeared after the caller read it
     * is a concurrent delete, which the caller experiences exactly like a todo that does not exist.
     */
    private TodoJpaEntity managed(TodoId id) {
        return repository
                .findById(id.value())
                .orElseThrow(() -> ApplicationException.notFound("todo.not-found", "Todo not found."));
    }
}
