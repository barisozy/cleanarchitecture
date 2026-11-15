package com.barisozy.cleanarchitecture.infrastructure.persistence.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barisozy.cleanarchitecture.application.PageResult;
import com.barisozy.cleanarchitecture.application.todo.TodoView;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

class TodoPersistenceAdapterTest {

    @Test
    void keepsTheRequestedPageAndAppliesTheFixedNewestFirstOrder() {
        TodoJpaRepository repository = mock(TodoJpaRepository.class);
        TodoPersistenceAdapter adapter = new TodoPersistenceAdapter(repository, new TodoJpaMapper());
        when(repository.findAll(ArgumentMatchers.<Specification<TodoJpaEntity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResult<TodoView> page = adapter.findPage(null, null, 1, 10);

        ArgumentCaptor<Pageable> applied = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(ArgumentMatchers.<Specification<TodoJpaEntity>>any(), applied.capture());
        assertThat(applied.getValue().getPageNumber()).isEqualTo(1);
        assertThat(applied.getValue().getPageSize()).isEqualTo(10);
        assertThat(applied.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        assertThat(page.totalElements()).isZero();
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(10);
    }
}
