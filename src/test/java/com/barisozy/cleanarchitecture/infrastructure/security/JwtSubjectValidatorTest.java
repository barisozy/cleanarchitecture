package com.barisozy.cleanarchitecture.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtSubjectValidatorTest {

    private final JwtSubjectValidator validator = new JwtSubjectValidator();

    @Test
    void acceptsOnlyAUuidSubject() {
        assertThat(validator.validate(token(UUID.randomUUID().toString())).hasErrors())
                .isFalse();
        assertThat(validator.validate(token("user-1")).hasErrors()).isTrue();
        assertThat(validator.validate(token(null)).hasErrors()).isTrue();
    }

    private static Jwt token(@Nullable String subject) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.parse("2026-11-04T10:00:00Z"))
                .expiresAt(Instant.parse("2026-11-04T10:15:00Z"));
        if (subject != null) {
            builder.subject(subject);
        }
        return builder.build();
    }
}
