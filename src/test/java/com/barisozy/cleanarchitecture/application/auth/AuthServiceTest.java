package com.barisozy.cleanarchitecture.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.barisozy.cleanarchitecture.application.ApplicationException;
import com.barisozy.cleanarchitecture.application.ErrorType;
import com.barisozy.cleanarchitecture.application.security.AuthenticatedUser;
import com.barisozy.cleanarchitecture.domain.DomainException;
import com.barisozy.cleanarchitecture.domain.user.Email;
import com.barisozy.cleanarchitecture.domain.user.PasswordHash;
import com.barisozy.cleanarchitecture.domain.user.RoleName;
import com.barisozy.cleanarchitecture.domain.user.User;
import com.barisozy.cleanarchitecture.fixtures.FakeTokenIssuer;
import com.barisozy.cleanarchitecture.fixtures.FakeUserRepository;
import com.barisozy.cleanarchitecture.fixtures.FixedClock;
import com.barisozy.cleanarchitecture.fixtures.UserTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    /** A password of exactly 72 bytes, the largest input BCrypt can consume. */
    private static final String PASSWORD_72_BYTES = "0123456789abcdef".repeat(4) + "01234567";

    private FakeUserRepository userRepository;
    private CountingPasswordEncoder passwordEncoder;
    private FakeTokenIssuer tokenIssuer;
    private AuthService service;

    @BeforeEach
    void setUp() {
        userRepository = new FakeUserRepository();
        passwordEncoder = new CountingPasswordEncoder();
        tokenIssuer = new FakeTokenIssuer();
        service = new AuthService(userRepository, passwordEncoder, tokenIssuer, new FixedClock(UserTestData.NOW));
    }

    @Test
    void registersTheAccountWithTheUserRole() {
        AuthResult result = service.register(UserTestData.USER_EMAIL, UserTestData.RAW_PASSWORD);

        User stored = onlyStoredUser();
        assertThat(stored.role()).isEqualTo(RoleName.USER);
        assertThat(stored.email().value()).isEqualTo(UserTestData.USER_EMAIL);
        assertThat(stored.createdAt()).isEqualTo(UserTestData.NOW);

        // The password is stored as a hash, never as the raw value.
        assertThat(stored.passwordHash().value()).isNotEqualTo(UserTestData.RAW_PASSWORD);
        assertThat(passwordEncoder.matches(
                        UserTestData.RAW_PASSWORD, stored.passwordHash().value()))
                .isTrue();

        assertThat(result.user().id()).isEqualTo(stored.id());
        assertThat(result.user().role()).isEqualTo(RoleName.USER);
        assertThat(result.accessToken()).isEqualTo(FakeTokenIssuer.TOKEN);
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(tokenIssuer.lastUser()).isEqualTo(stored);
    }

    @Test
    void rejectsPasswordsThatBreakTheRules() {
        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> service.register(UserTestData.USER_EMAIL, "Short123!"))
                .satisfies(error -> assertThat(error.code()).isEqualTo("user.password.invalid"));

        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> service.register(UserTestData.USER_EMAIL, "a".repeat(73)))
                .satisfies(error -> assertThat(error.code()).isEqualTo("user.password.too-long"));

        assertThat(userRepository.users()).isEmpty();
    }

    @Test
    void issuesATokenForValidCredentials() {
        userRepository.insert(UserTestData.user());

        AuthResult result = service.login(UserTestData.USER_EMAIL, UserTestData.RAW_PASSWORD);

        assertThat(result.accessToken()).isEqualTo(FakeTokenIssuer.TOKEN);
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.user().email()).isEqualTo(UserTestData.USER_EMAIL);
        assertThat(result.user().role()).isEqualTo(RoleName.USER);
        assertThat(tokenIssuer.lastUser().id()).isEqualTo(result.user().id());
        assertThat(passwordEncoder.matchCalls()).isEqualTo(1);

        // A password of exactly 72 bytes is still accepted: the limit is inclusive.
        userRepository.insert(userWithPassword("other@example.com", PASSWORD_72_BYTES));

        AuthResult longest = service.login("other@example.com", PASSWORD_72_BYTES);

        assertThat(longest.accessToken()).isNotBlank();
        assertThat(passwordEncoder.matchCalls()).isEqualTo(2);
    }

    @Test
    void rejectsInvalidCredentialsWithoutRevealingWhetherTheAccountExists() {
        // Unknown e-mail: the dummy hash keeps the answer time of a missing account equal to the
        // one of a wrong password, so the verification still runs.
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> service.login(UserTestData.USER_EMAIL, UserTestData.RAW_PASSWORD))
                .satisfies(error -> {
                    assertThat(error.code()).isEqualTo("auth.invalid-credentials");
                    assertThat(error.type()).isEqualTo(ErrorType.UNAUTHORIZED);
                    assertThat(error.getMessage()).isEqualTo("Invalid email or password.");
                });
        assertThat(passwordEncoder.matchCalls()).isEqualTo(1);

        // A wrong password for an existing account produces the very same error.
        userRepository.insert(UserTestData.user());

        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> service.login(UserTestData.USER_EMAIL, "WrongPassword123!"))
                .satisfies(error -> {
                    assertThat(error.code()).isEqualTo("auth.invalid-credentials");
                    assertThat(error.type()).isEqualTo(ErrorType.UNAUTHORIZED);
                });
        assertThat(passwordEncoder.matchCalls()).isEqualTo(2);
    }

    @Test
    void rejectsInputThatTheEncoderMustNeverSee() {
        // A malformed address is rejected before the encoder is called.
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> service.login("not-an-email", UserTestData.RAW_PASSWORD))
                .satisfies(error -> assertThat(error.code()).isEqualTo("auth.invalid-credentials"));
        assertThat(passwordEncoder.matchCalls()).isZero();

        // A password of 73 bytes shares its first 72 bytes with the stored hash, so the encoder
        // alone would accept it: Spring Security truncates the input at 72 bytes. The type level
        // guard is what rejects it.
        User user = userWithPassword(UserTestData.USER_EMAIL, PASSWORD_72_BYTES);
        userRepository.insert(user);
        String tooLong = PASSWORD_72_BYTES + "X";

        assertThat(new BCryptPasswordEncoder(4)
                        .matches(tooLong, user.passwordHash().value()))
                .isTrue();

        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> service.login(UserTestData.USER_EMAIL, tooLong))
                .satisfies(error -> assertThat(error.code()).isEqualTo("auth.invalid-credentials"));
        assertThat(passwordEncoder.matchCalls()).isZero();
    }

    @Test
    void returnsTheProfileOfTheAuthenticatedUser() {
        User user = UserTestData.user();
        userRepository.insert(user);

        UserView view = service.currentUser(new AuthenticatedUser(user.id(), RoleName.USER));

        assertThat(view.id()).isEqualTo(user.id());
        assertThat(view.email()).isEqualTo(UserTestData.USER_EMAIL);
        assertThat(view.role()).isEqualTo(RoleName.USER);
    }

    @Test
    void rejectsAnIdentityThatNoLongerExists() {
        assertThatExceptionOfType(ApplicationException.class)
                .isThrownBy(() -> service.currentUser(new AuthenticatedUser(UserTestData.USER_ID, RoleName.USER)))
                .satisfies(error -> {
                    assertThat(error.code()).isEqualTo("security.unauthorized");
                    assertThat(error.type()).isEqualTo(ErrorType.UNAUTHORIZED);
                });
    }

    private User onlyStoredUser() {
        assertThat(userRepository.users()).hasSize(1);
        return userRepository.users().values().iterator().next();
    }

    private static User userWithPassword(String email, String password) {
        PasswordHash hash = PasswordHash.of(new BCryptPasswordEncoder(4).encode(password));
        return User.register(Email.of(email), hash, RoleName.USER, UserTestData.NOW);
    }

    /**
     * A real encoder that counts the verifications. The count proves that login verified a password
     * even when the account does not exist.
     */
    private static final class CountingPasswordEncoder implements PasswordEncoder {

        private final PasswordEncoder delegate = new BCryptPasswordEncoder(4);

        private int matchCalls;

        @Override
        public String encode(CharSequence rawPassword) {
            return delegate.encode(rawPassword);
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            matchCalls++;
            return delegate.matches(rawPassword, encodedPassword);
        }

        int matchCalls() {
            return matchCalls;
        }
    }
}
