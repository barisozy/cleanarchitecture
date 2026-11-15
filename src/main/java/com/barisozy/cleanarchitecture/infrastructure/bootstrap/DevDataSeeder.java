package com.barisozy.cleanarchitecture.infrastructure.bootstrap;

import com.barisozy.cleanarchitecture.domain.user.Email;
import com.barisozy.cleanarchitecture.domain.user.PasswordHash;
import com.barisozy.cleanarchitecture.domain.user.RoleName;
import com.barisozy.cleanarchitecture.domain.user.User;
import com.barisozy.cleanarchitecture.domain.user.UserRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Local-only development accounts. Never enable this profile in a deployed environment.
 *
 * <p>Credentials are intentionally fixed for developer convenience. Neither the password nor the
 * hash is written to the log; the e-mail address is masked.
 */
@Component
@Profile("local")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final String DEMO_EMAIL = "demo@example.com";
    private static final String DEMO_PASSWORD = "DemoPassword123!";
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "AdminPassword123!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DevDataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfMissing(DEMO_EMAIL, DEMO_PASSWORD, RoleName.USER);
        createIfMissing(ADMIN_EMAIL, ADMIN_PASSWORD, RoleName.ADMIN);
    }

    private void createIfMissing(String email, String password, RoleName role) {
        Email address = Email.of(email);
        if (userRepository.findByEmail(address).isPresent()) {
            return;
        }
        User user = User.register(address, PasswordHash.of(passwordEncoder.encode(password)), role, clock.instant());
        userRepository.insert(user);
        log.info("Seeded local development user {}", mask(email));
    }

    /** Masks the local part of the address; the domain is not sensitive and stays readable. */
    private static String mask(String email) {
        int separator = email.indexOf('@');
        if (separator <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(separator);
    }
}
