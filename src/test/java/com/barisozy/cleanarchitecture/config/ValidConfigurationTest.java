package com.barisozy.cleanarchitecture.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.barisozy.cleanarchitecture.infrastructure.config.ClockConfiguration;
import com.barisozy.cleanarchitecture.infrastructure.config.PasswordConfiguration;
import com.barisozy.cleanarchitecture.infrastructure.config.SecurityTokenConfiguration;
import com.barisozy.cleanarchitecture.infrastructure.config.properties.JwtProperties;
import com.barisozy.cleanarchitecture.infrastructure.config.properties.PasswordProperties;
import com.barisozy.cleanarchitecture.web.config.properties.ApiProperties;
import com.barisozy.cleanarchitecture.web.config.properties.CorsProperties;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

/** Verifies that the shipped configuration starts and publishes the expected beans. */
class ValidConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ShippedConfiguration.class)
            .withPropertyValues(
                    "spring.profiles.active=local",
                    "spring.docker.compose.enabled=false",
                    // A developer's optional `.env` must not change what this test asserts.
                    "app.api.problem-base-uri=https://api.example.com/problems",
                    "app.api.public-base-url=http://localhost:8080",
                    "app.security.jwt.issuer=https://api.example.com",
                    "app.security.jwt.audience=cleanarchitecture-api",
                    "app.security.jwt.access-token-ttl=PT1H");

    @Test
    void startsAndPublishesTheExpectedBeans() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(PasswordEncoder.class)).isInstanceOf(BCryptPasswordEncoder.class);
            assertThat(context.getBean(JwtEncoder.class)).isNotNull();
            assertThat(context.getBean(JwtDecoder.class)).isNotNull();
            assertThat(context.getBean(Clock.class)).isNotNull();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
        ApiProperties.class,
        JwtProperties.class,
        PasswordProperties.class,
        CorsProperties.class
    })
    @Import({PasswordConfiguration.class, SecurityTokenConfiguration.class, ClockConfiguration.class})
    static class ShippedConfiguration {}
}
