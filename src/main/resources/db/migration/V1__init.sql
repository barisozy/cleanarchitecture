-- The schema of the template: one table for the accounts and one for the todos.
--
-- The check constraint mirrors the domain invariant of the todo aggregate: the status is the
-- source of truth and the completion timestamp is set exactly when the todo is completed. The two
-- fields therefore cannot drift apart, in the database either.

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE todos (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    due_date DATE,
    status VARCHAR(16) NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_todos_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_todos_status_completed_at CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status = 'PENDING' AND completed_at IS NULL)
    )
);

-- The regular list reads one owner's todos; these two indexes serve the unfiltered and the
-- status-filtered page without a sort. The identifier is part of the key so that the order of the
-- ties on created_at is stable across pages.
CREATE INDEX idx_todos_owner_created_at ON todos (owner_id, created_at DESC, id DESC);
CREATE INDEX idx_todos_owner_status_created_at ON todos (owner_id, status, created_at DESC, id DESC);

-- The administrator list has no owner predicate, so the owner-first indexes cannot serve it.
-- These two indexes cover the same newest-first read model across every owner.
CREATE INDEX idx_todos_created_at ON todos (created_at DESC, id DESC);
CREATE INDEX idx_todos_status_created_at ON todos (status, created_at DESC, id DESC);
