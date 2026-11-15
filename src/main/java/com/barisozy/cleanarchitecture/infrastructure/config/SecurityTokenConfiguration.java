package com.barisozy.cleanarchitecture.infrastructure.config;

import com.barisozy.cleanarchitecture.infrastructure.config.properties.JwtProperties;
import com.barisozy.cleanarchitecture.infrastructure.security.JwtRolesValidator;
import com.barisozy.cleanarchitecture.infrastructure.security.JwtSubjectValidator;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Publishes the HS256 key, encoder and decoder of the access token.
 *
 * <p>A single service that issues and verifies its own tokens does not need an asymmetric key pair
 * or a JWKS endpoint. The local profile generates an ephemeral key; every other profile reads the
 * configured secret, so a deployment without one cannot start.
 *
 * <p>The decoder validates issuer, audience, timestamps, subject and the single-role claim.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityTokenConfiguration {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** A key that lasts for one local run; a restart invalidates the issued tokens. */
    @Bean
    @Profile("local")
    SecretKey localJwtSecretKey() {
        byte[] secret = new byte[JwtProperties.MIN_SECRET_BYTES];
        SECURE_RANDOM.nextBytes(secret);
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    @Profile("!local")
    SecretKey jwtSecretKey(JwtProperties properties) {
        String secret = properties.secret();
        if (secret == null) {
            throw new IllegalStateException("app.security.jwt.secret is required outside the local profile.");
        }
        return new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSecretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD, audiences -> audiences != null && audiences.contains(properties.audience()));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                audience,
                new JwtSubjectValidator(),
                new JwtRolesValidator()));
        return decoder;
    }
}
