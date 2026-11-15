package com.barisozy.cleanarchitecture.web.config;

import com.barisozy.cleanarchitecture.web.config.properties.ApiProperties;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;

/**
 * The OpenAPI document of the application.
 *
 * <p>The bearer scheme is advertised only on the operations that actually require it. The
 * customizer looks for {@link PreAuthorize}, so a public endpoint never claims that the caller has
 * to present a token.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI applicationOpenApi(ApiProperties properties, @Value("${spring.application.name}") String applicationName) {
        return new OpenAPI()
                .info(new Info()
                        .title(applicationName + " API")
                        .description("Java 25 + Spring Boot Clean Architecture template API.")
                        .version("1.0.0"))
                .servers(List.of(new Server().url(properties.publicBaseUrl()).description("Public API")))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_AUTH,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("HS256 access token")))
                .tags(List.of(
                        new Tag().name("Auth").description("Registration, login and the current profile"),
                        new Tag().name("Todos").description("Todo management")));
    }

    /**
     * Marks an operation as requiring bearer authentication only when its handler declares
     * {@link PreAuthorize}. Public endpoints therefore never advertise the security scheme.
     */
    @Bean
    OperationCustomizer bearerSecurityCustomizer() {
        return (operation, handlerMethod) -> {
            if (isProtected(handlerMethod)) {
                operation.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
            }
            return operation;
        };
    }

    private static boolean isProtected(HandlerMethod handlerMethod) {
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), PreAuthorize.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), PreAuthorize.class);
    }
}
