package com.barisozy.cleanarchitecture.web.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.barisozy.cleanarchitecture.web.config.properties.ApiProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;

/** Covers the metadata and the conditional bearer-auth advertisement of the OpenAPI document. */
class OpenApiConfigurationTest {

    private final OpenApiConfiguration configuration = new OpenApiConfiguration();

    @Test
    void publishesTheServerTagsAndBearerScheme() {
        OpenAPI openApi = configuration.applicationOpenApi(properties(), "cleanarchitecture");

        assertThat(openApi.getInfo().getTitle()).isEqualTo("cleanarchitecture API");
        assertThat(openApi.getServers())
                .singleElement()
                .satisfies(server -> assertThat(server.getUrl()).isEqualTo("https://api.example.com"));
        assertThat(openApi.getInfo().getVersion()).isEqualTo("1.0.0");
        assertThat(openApi.getTags()).extracting(tag -> tag.getName()).containsExactly("Auth", "Todos");
        SecurityScheme scheme = openApi.getComponents().getSecuritySchemes().get("bearerAuth");
        assertThat(scheme).isNotNull();
        assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
    }

    @Test
    void advertisesBearerAuthOnlyForProtectedOperations() throws Exception {
        Operation methodProtected =
                configure(configuration.bearerSecurityCustomizer(), ProtectedHandlers.class, "handle");
        Operation classProtected =
                configure(configuration.bearerSecurityCustomizer(), ClassProtectedHandlers.class, "handle");
        Operation publicOperation = configure(configuration.bearerSecurityCustomizer(), PublicHandlers.class, "handle");

        assertThat(methodProtected.getSecurity())
                .singleElement()
                .satisfies(requirement -> assertThat(requirement).containsKey("bearerAuth"));
        assertThat(classProtected.getSecurity())
                .singleElement()
                .satisfies(requirement -> assertThat(requirement).containsKey("bearerAuth"));
        assertThat(publicOperation.getSecurity()).isNull();

        Operation operation = new Operation();
        for (OperationCustomizer customizer : operationCustomizers()) {
            operation = customizer.customize(operation, handlerMethod(PublicHandlers.class, "handle"));
        }
        assertThat(operation.getParameters()).isNull();
    }

    /** Every customizer the configuration declares, so that a new one cannot slip in unnoticed. */
    private List<OperationCustomizer> operationCustomizers() throws ReflectiveOperationException {
        List<OperationCustomizer> customizers = new ArrayList<>();
        for (Method method : OpenApiConfiguration.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Bean.class)
                    && OperationCustomizer.class.isAssignableFrom(method.getReturnType())) {
                method.setAccessible(true);
                Object customizer = method.invoke(configuration);
                if (customizer instanceof OperationCustomizer operationCustomizer) {
                    customizers.add(operationCustomizer);
                }
            }
        }
        return customizers;
    }

    private static ApiProperties properties() {
        return new ApiProperties("https://example.test/problems", "https://api.example.com");
    }

    private static Operation configure(OperationCustomizer customizer, Class<?> handlerType, String methodName) {
        return customizer.customize(new Operation(), handlerMethod(handlerType, methodName));
    }

    private static HandlerMethod handlerMethod(Class<?> handlerType, String methodName) {
        try {
            Object bean = handlerType.getDeclaredConstructor().newInstance();
            Method method = handlerType.getDeclaredMethod(methodName);
            return new HandlerMethod(bean, method);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to create handler method", exception);
        }
    }

    static class PublicHandlers {
        void handle() {}
    }

    static class ProtectedHandlers {
        @PreAuthorize("isAuthenticated()")
        void handle() {}
    }

    @PreAuthorize("isAuthenticated()")
    static class ClassProtectedHandlers {
        void handle() {}
    }
}
