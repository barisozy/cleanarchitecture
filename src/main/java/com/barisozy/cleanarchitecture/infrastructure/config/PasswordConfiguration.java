package com.barisozy.cleanarchitecture.infrastructure.config;

import com.barisozy.cleanarchitecture.infrastructure.config.properties.PasswordProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Publishes the password encoder of the application.
 *
 * <p>BCrypt is a deliberate choice: it is the algorithm the Spring Security ecosystem ships, it is
 * deliberately slow and salted, and it produces hashes that every other BCrypt implementation can
 * verify. The cost factor is configurable, so a deployment can trade login latency for work
 * without changing the code.
 */
@Configuration(proxyBeanMethods = false)
public class PasswordConfiguration {

    @Bean
    PasswordEncoder passwordEncoder(PasswordProperties properties) {
        return new BCryptPasswordEncoder(properties.bcryptStrength());
    }
}
