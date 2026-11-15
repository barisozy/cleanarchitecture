package com.barisozy.cleanarchitecture.application.auth;

import com.barisozy.cleanarchitecture.application.ApplicationException;
import com.barisozy.cleanarchitecture.application.security.AuthenticatedUser;
import com.barisozy.cleanarchitecture.domain.DomainException;
import com.barisozy.cleanarchitecture.domain.user.Email;
import com.barisozy.cleanarchitecture.domain.user.PasswordHash;
import com.barisozy.cleanarchitecture.domain.user.RawPassword;
import com.barisozy.cleanarchitecture.domain.user.RoleName;
import com.barisozy.cleanarchitecture.domain.user.User;
import com.barisozy.cleanarchitecture.domain.user.UserRepository;
import java.time.Clock;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The authentication use cases: registration, login and the current profile.
 *
 * <p>The class is not final on purpose: {@link Transactional} is applied through a CGLIB proxy.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final Clock clock;

    /**
     * A valid hash of a value nobody knows. The login flow verifies it when the account does not
     * exist, so both failure paths take the same time.
     */
    private final String dummyHash;

    public AuthService(
            UserRepository userRepository, PasswordEncoder passwordEncoder, TokenIssuer tokenIssuer, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("timing-equalisation-only");
    }

    /**
     * Registers a new account and issues its first access token.
     *
     * <p>The e-mail address is not checked up front: the unique constraint of the database is the
     * authority, so there is no time-of-check/time-of-use window. The adapter translates the
     * constraint violation into a conflict.
     */
    public AuthResult register(String email, String rawPassword) {
        Email address = Email.of(email);
        RawPassword password = RawPassword.forRegistration(rawPassword);
        PasswordHash hash = PasswordHash.of(passwordEncoder.encode(password.value()));
        User user = User.register(address, hash, RoleName.USER, clock.instant());
        return tokenIssuer.issue(userRepository.insert(user));
    }

    /**
     * Verifies the credentials and issues an access token.
     *
     * <p>A malformed address, an oversized password and a wrong credential all produce the same
     * {@code 401}: a rejected login must not reveal which part was wrong.
     */
    public AuthResult login(String email, String rawPassword) {
        Email address;
        RawPassword password;
        try {
            address = Email.of(email);
            password = RawPassword.forLogin(rawPassword);
        } catch (DomainException exception) {
            throw invalidCredentials();
        }
        @Nullable User user = userRepository.findByEmail(address).orElse(null);
        String hash = user == null ? dummyHash : user.passwordHash().value();
        boolean matches = passwordEncoder.matches(password.value(), hash);
        if (user == null || !matches) {
            throw invalidCredentials();
        }
        return tokenIssuer.issue(user);
    }

    /** Reads the profile of the authenticated caller. */
    @Transactional(readOnly = true)
    public UserView currentUser(AuthenticatedUser caller) {
        User user = userRepository
                .findById(caller.id())
                .orElseThrow(() -> ApplicationException.unauthorized(
                        "security.unauthorized", "The authenticated user no longer exists."));
        return UserView.from(user);
    }

    private static ApplicationException invalidCredentials() {
        return ApplicationException.unauthorized("auth.invalid-credentials", "Invalid email or password.");
    }
}
