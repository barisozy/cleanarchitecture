package com.barisozy.cleanarchitecture.config;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/** Guards the framework settings and defaults shipped in {@code application.yaml}. */
class ApplicationConfigurationTest {

    private final PropertySource<?> application = load("application.yaml");

    @Test
    void enablesProblemDetailsAndUsesSafePersistenceDefaults() {
        assertThat(application.getProperty("spring.mvc.problemdetails.enabled")).isEqualTo(true);
        assertThat(application.getProperty("spring.jpa.open-in-view")).isEqualTo(false);
        assertThat(application.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @Test
    void runsRequestsOnVirtualThreads() {
        assertThat(application.getProperty("spring.threads.virtual.enabled")).isEqualTo(true);
    }

    @Test
    void doesNotShipAnImplicitSpringProfile() {
        assertThat(application.getProperty("spring.profiles.default")).isNull();
    }

    @Test
    void leavesTheDeploymentIdentityToTheActiveProfile() {
        assertThat(application.getProperty("app.api.problem-base-uri")).isNull();
        assertThat(application.getProperty("app.api.public-base-url")).isNull();
        assertThat(application.getProperty("app.security.jwt.issuer")).isNull();
        assertThat(application.getProperty("app.security.jwt.audience")).isNull();
    }

    private static PropertySource<?> load(String resource) {
        try {
            List<PropertySource<?>> sources =
                    new YamlPropertySourceLoader().load(resource, new ClassPathResource(resource));
            return requireNonNull(sources.getFirst(), "application.yaml must contain a property source");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load " + resource, exception);
        }
    }
}
