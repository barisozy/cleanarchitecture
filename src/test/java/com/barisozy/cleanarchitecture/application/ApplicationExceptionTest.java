package com.barisozy.cleanarchitecture.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Covers the factories and the categories of the expected business-outcome exception. */
class ApplicationExceptionTest {

    @Test
    void everyFactorySelectsTheMatchingCategoryAndKeepsItsContract() {
        ApplicationException exception = ApplicationException.notFound("todo.not-found", "Todo not found.");

        assertThat(exception.code()).isEqualTo("todo.not-found");
        assertThat(exception.type()).isEqualTo(ErrorType.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("Todo not found.");
        assertThat(ApplicationException.validation("validation.failed", "Invalid.")
                        .type())
                .isEqualTo(ErrorType.VALIDATION);
        assertThat(ApplicationException.conflict("todo.already-completed", "Done.")
                        .type())
                .isEqualTo(ErrorType.CONFLICT);
        assertThat(ApplicationException.unauthorized("auth.invalid-credentials", "Nope.")
                        .type())
                .isEqualTo(ErrorType.UNAUTHORIZED);
        assertThat(ApplicationException.forbidden("security.forbidden", "Denied.")
                        .type())
                .isEqualTo(ErrorType.FORBIDDEN);
        assertThat(ErrorType.values()).hasSize(5);
    }
}
