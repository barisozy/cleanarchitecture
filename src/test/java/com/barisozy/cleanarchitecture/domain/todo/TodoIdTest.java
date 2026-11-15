package com.barisozy.cleanarchitecture.domain.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barisozy.cleanarchitecture.domain.DomainException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class TodoIdTest {

    private static final String CANONICAL_UUID = "33333333-3333-3333-3333-333333333333";

    @Test
    void createsParsesAndValidatesTheIdentity() {
        TodoId id = TodoId.newId();
        assertThat(id.value()).isNotNull();
        assertThat(TodoId.newId()).isNotEqualTo(id);

        assertThat(TodoId.from(CANONICAL_UUID).value()).isEqualTo(UUID.fromString(CANONICAL_UUID));

        assertThatThrownBy(() -> TodoId.from("not-a-uuid"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("must be a UUID");

        @Nullable UUID value = null;
        assertThatThrownBy(() -> new TodoId(value)).isInstanceOf(DomainException.class);
    }
}
