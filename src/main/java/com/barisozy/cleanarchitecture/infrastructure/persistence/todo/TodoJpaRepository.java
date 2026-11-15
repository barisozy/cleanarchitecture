package com.barisozy.cleanarchitecture.infrastructure.persistence.todo;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface TodoJpaRepository extends JpaRepository<TodoJpaEntity, UUID>, JpaSpecificationExecutor<TodoJpaEntity> {}
