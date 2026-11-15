package com.barisozy.cleanarchitecture.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.barisozy.cleanarchitecture.domain.user.RoleName;
import com.barisozy.cleanarchitecture.domain.user.User;
import com.barisozy.cleanarchitecture.fixtures.UserTestData;
import com.barisozy.cleanarchitecture.web.config.properties.ApiProperties;
import com.barisozy.cleanarchitecture.web.config.properties.CorsProperties;
import com.barisozy.cleanarchitecture.web.error.ProblemDetailFactory;
import com.barisozy.cleanarchitecture.web.security.JwtAuthenticationConverter;
import com.barisozy.cleanarchitecture.web.security.SecurityErrorHandler;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

/**
 * Web slice setup that reuses the production security chain.
 *
 * <p>The slice never decodes a token: the request post processor installs an authentication that
 * the production converter created. The decoder bean therefore only satisfies the resource-server
 * configuration and fails loudly if a test ever sends an unsigned request by accident.
 */
@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties({ApiProperties.class, CorsProperties.class})
public class WebSliceTestConfiguration {

    @Bean
    Clock clock() {
        return UserTestData.CLOCK;
    }

    @Bean
    SecurityErrorHandler securityErrorHandler(ProblemDetailFactory factory, ObjectMapper objectMapper) {
        return new SecurityErrorHandler(factory, objectMapper);
    }

    @Bean
    JwtDecoder webSliceJwtDecoder() {
        return token -> {
            throw new JwtException("The web slice does not decode tokens.");
        };
    }

    /** A request that carries the access token of the given user. */
    public static RequestPostProcessor jwtFor(User user) {
        return jwtFor(user, user.role());
    }

    /**
     * A request that carries the access token of the given user with an explicit role.
     *
     * <p>The token is translated by the production converter, so the slice observes the same
     * principal and authorities as the running application. The token carries exactly one role.
     */
    public static RequestPostProcessor jwtFor(User user, RoleName role) {
        Jwt jwt = Jwt.withTokenValue("test-access-token")
                .header("alg", "none")
                .subject(user.id().value().toString())
                .claim("roles", List.of(role.name()))
                .build();
        return authentication(new JwtAuthenticationConverter().convert(jwt));
    }
}
