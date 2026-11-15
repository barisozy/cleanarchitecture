package com.barisozy.cleanarchitecture.domain.user;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barisozy.cleanarchitecture.domain.DomainException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class RawPasswordTest {

    /** The letter "s with cedilla" — one character, two bytes in UTF-8. */
    private static final String TWO_BYTE_CHAR = String.valueOf((char) 0x015F);

    @Test
    void registrationEnforcesTheMinimumLengthButLoginDoesNot() {
        assertThat(RawPassword.forRegistration("123456789012").value()).hasSize(RawPassword.MIN_LENGTH);

        assertThatThrownBy(() -> RawPassword.forRegistration("12345678901"))
                .isInstanceOf(DomainException.class)
                .satisfies(exception ->
                        assertThat(((DomainException) exception).code()).isEqualTo("user.password.invalid"))
                .hasMessage("Password must be at least 12 characters.");

        // Login deliberately accepts a short password: accounts that were created before the rule
        // existed keep working.
        assertThat(RawPassword.forLogin("short").value()).isEqualTo("short");

        assertThatThrownBy(() -> RawPassword.forLogin(""))
                .isInstanceOf(DomainException.class)
                .satisfies(exception ->
                        assertThat(((DomainException) exception).code()).isEqualTo("user.password.invalid"))
                .hasMessage("Password is required.");

        @Nullable String missing = null;
        assertThatThrownBy(() -> RawPassword.forRegistration(missing))
                .isInstanceOf(DomainException.class)
                .satisfies(exception ->
                        assertThat(((DomainException) exception).code()).isEqualTo("user.password.invalid"))
                .hasMessage("Password is required.");
    }

    @Test
    void theUpperLimitCountsBytesAndNotCharacters() {
        String exactly72Bytes = "x".repeat(RawPassword.MAX_BYTES);
        assertThat(exactly72Bytes.getBytes(UTF_8)).hasSize(72);
        assertThat(RawPassword.forRegistration(exactly72Bytes)).isNotNull();
        assertThat(RawPassword.forLogin(exactly72Bytes)).isNotNull();

        // 36 characters of a two-byte character are exactly 72 bytes and are still accepted.
        String multiByte = TWO_BYTE_CHAR.repeat(36);
        assertThat(multiByte).hasSize(36);
        assertThat(multiByte.getBytes(UTF_8)).hasSize(72);
        assertThat(RawPassword.forRegistration(multiByte)).isNotNull();

        String seventyThreeBytes = "x".repeat(RawPassword.MAX_BYTES + 1);
        assertThat(seventyThreeBytes.getBytes(UTF_8)).hasSize(73);
        assertThatThrownBy(() -> RawPassword.forRegistration(seventyThreeBytes))
                .isInstanceOf(DomainException.class)
                .satisfies(exception ->
                        assertThat(((DomainException) exception).code()).isEqualTo("user.password.too-long"))
                .hasMessage("Password must not exceed 72 bytes.");

        String multiByteTooLong = TWO_BYTE_CHAR.repeat(36) + "a";
        assertThat(multiByteTooLong.getBytes(UTF_8)).hasSize(73);
        assertThatThrownBy(() -> RawPassword.forLogin(multiByteTooLong))
                .isInstanceOf(DomainException.class)
                .satisfies(exception ->
                        assertThat(((DomainException) exception).code()).isEqualTo("user.password.too-long"));
    }

    @Test
    void isNeverRendered() {
        assertThat(RawPassword.forRegistration("StrongPassword123!")).hasToString("RawPassword[********]");
    }
}
