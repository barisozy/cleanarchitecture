package com.barisozy.cleanarchitecture.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.barisozy.cleanarchitecture.fixtures.AuthTestData;
import com.barisozy.cleanarchitecture.infrastructure.config.SecurityTokenConfiguration;
import com.barisozy.cleanarchitecture.infrastructure.config.properties.JwtProperties;
import com.barisozy.cleanarchitecture.web.config.properties.ApiProperties;
import com.barisozy.cleanarchitecture.web.config.properties.CorsProperties;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Verifies the production profile: every value that identifies the deployment is mandatory and the
 * API explorer is off.
 */
class ProductionConfigurationTest {

    private static final Map<String, String> REQUIRED = Map.of(
            "DB_URL", "jdbc:postgresql://localhost:5432/cleanarchitecture",
            "DB_USERNAME", "app",
            "DB_PASSWORD", "app",
            "JWT_SECRET", AuthTestData.SECRET,
            "JWT_ISSUER", "https://api.example.com",
            "JWT_AUDIENCE", "cleanarchitecture-api",
            "PROBLEM_BASE_URI", "https://api.example.com/problems",
            "PUBLIC_BASE_URL", "https://api.example.com",
            "CORS_ALLOWED_ORIGINS", "https://app.example.com");

    @Test
    void startsWithEveryProductionValueAndDisablesTheApiExplorer() {
        runner(REQUIRED).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("springdoc.api-docs.enabled", Boolean.class))
                    .isFalse();
            assertThat(context.getEnvironment().getProperty("springdoc.scalar.enabled", Boolean.class))
                    .isFalse();
            assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://localhost:5432/cleanarchitecture");
        });
    }

    @Test
    void refusesToStartWhenARequiredValueIsMissing() {
        for (String variable : List.of(
                "JWT_SECRET",
                "JWT_ISSUER",
                "JWT_AUDIENCE",
                "PROBLEM_BASE_URI",
                "PUBLIC_BASE_URL",
                "CORS_ALLOWED_ORIGINS")) {
            Map<String, String> properties = new LinkedHashMap<>(REQUIRED);
            properties.remove(variable);
            runner(properties)
                    .run(context -> assertThat(context)
                            .as("a production context without %s must fail", variable)
                            .hasFailed());
        }
    }

    @Test
    void requiresTheDatabaseSettingsFromTheEnvironment() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application-prod.yaml", new ClassPathResource("application-prod.yaml"));
        PropertySource<?> production = sources.getFirst();

        assertThat(production.getProperty("spring.datasource.url")).isEqualTo("${DB_URL}");
        assertThat(production.getProperty("spring.datasource.username")).isEqualTo("${DB_USERNAME}");
        assertThat(production.getProperty("spring.datasource.password")).isEqualTo("${DB_PASSWORD}");
    }

    private static ApplicationContextRunner runner(Map<String, String> properties) {
        String[] values = properties.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(ShippedProductionConfiguration.class)
                .withPropertyValues("spring.profiles.active=prod", "spring.docker.compose.enabled=false")
                .withPropertyValues(values);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({ApiProperties.class, JwtProperties.class, CorsProperties.class})
    @Import(SecurityTokenConfiguration.class)
    static class ShippedProductionConfiguration {}
}
