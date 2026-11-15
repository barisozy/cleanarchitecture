package com.barisozy.cleanarchitecture.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barisozy.cleanarchitecture.domain.DomainException;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EmailTest {

    @Test
    void normalizesAndAcceptsValidAddresses() {
        assertThat(Email.of("  User.Name+tag@Example.COM  ").value()).isEqualTo("user.name+tag@example.com");
        assertThat(Email.of("user@example.com").value()).isEqualTo("user@example.com");

        String longest =
                "a".repeat(64) + "@" + String.join(".", "b".repeat(63), "c".repeat(63), "d".repeat(63), "e".repeat(63));
        assertThat(longest).hasSize(320);
        assertThat(Email.of(longest).value()).hasSize(320);
    }

    @ParameterizedTest
    @MethodSource("invalidAddresses")
    void rejectsInvalidAddresses(@Nullable String value, String message) {
        assertThatThrownBy(() -> Email.of(value))
                .isInstanceOf(DomainException.class)
                .satisfies(exception ->
                        assertThat(((DomainException) exception).code()).isEqualTo("user.email.invalid"))
                .hasMessage(message);
    }

    /** Every invalid address with the message of the rule that rejects it. */
    private static Stream<Arguments> invalidAddresses() {
        return Stream.of(
                Arguments.of(null, "Email is required."),
                Arguments.of("", "Email is not valid."),
                Arguments.of("   ", "Email is not valid."),
                Arguments.of("not-an-email", "Email is not valid."),
                Arguments.of("@example.com", "Email is not valid."),
                Arguments.of("user@", "Email is not valid."),
                Arguments.of("user@example", "Email is not valid."),
                Arguments.of("user @example.com", "Email is not valid."),
                Arguments.of(".user@example.com", "Email is not valid."),
                Arguments.of("user.@example.com", "Email is not valid."),
                Arguments.of("user..name@example.com", "Email is not valid."),
                Arguments.of("a".repeat(65) + "@example.com", "Email is not valid."),
                Arguments.of("a".repeat(309) + "@example.com", "Email is not valid."));
    }
}
