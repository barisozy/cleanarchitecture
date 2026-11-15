package com.barisozy.cleanarchitecture.domain.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barisozy.cleanarchitecture.domain.DomainException;
import org.junit.jupiter.api.Test;

class TodoDescriptionTest {

    @Test
    void normalizesBlankInputToNullAndValidatesTheLength() {
        assertThat(TodoDescription.ofNullable(null)).isNull();
        assertThat(TodoDescription.ofNullable("   ")).isNull();
        assertThat(TodoDescription.ofNullable("x")).isEqualTo(new TodoDescription("x"));
        assertThat(TodoDescription.ofNullable("  details  ")).isEqualTo(new TodoDescription("details"));

        String longest = "x".repeat(2000);
        assertThat(TodoDescription.ofNullable(longest)).isEqualTo(new TodoDescription(longest));

        assertThatThrownBy(() -> new TodoDescription("   "))
                .isInstanceOf(DomainException.class)
                .hasMessage("Description must not be blank; use ofNullable.");
        assertThatThrownBy(() -> TodoDescription.ofNullable("x".repeat(2001)))
                .isInstanceOf(DomainException.class)
                .satisfies(exception ->
                        assertThat(((DomainException) exception).code()).isEqualTo("todo.description.invalid"));
    }
}
