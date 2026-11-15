package com.barisozy.cleanarchitecture.domain.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barisozy.cleanarchitecture.domain.DomainException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class TodoTitleTest {

    @Test
    void trimsAndValidatesTheTitle() {
        assertThat(TodoTitle.of("  Write plan  ").value()).isEqualTo("Write plan");
        assertThat(TodoTitle.of("x".repeat(200)).value()).hasSize(200);

        assertInvalid("x".repeat(201), "Title must be between 1 and 200 characters.");
        assertInvalid("", "Title must be between 1 and 200 characters.");
        assertInvalid("   ", "Title must be between 1 and 200 characters.");
        assertInvalid("\t\n", "Title must be between 1 and 200 characters.");
        assertInvalid(null, "Title is required.");
    }

    private static void assertInvalid(@Nullable String value, String message) {
        assertThatThrownBy(() -> TodoTitle.of(value))
                .isInstanceOf(DomainException.class)
                .satisfies(exception ->
                        assertThat(((DomainException) exception).code()).isEqualTo("todo.title.invalid"))
                .hasMessage(message);
    }
}
