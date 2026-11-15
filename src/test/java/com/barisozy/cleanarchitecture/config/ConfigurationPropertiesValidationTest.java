package com.barisozy.cleanarchitecture.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.barisozy.cleanarchitecture.fixtures.AuthTestData;
import com.barisozy.cleanarchitecture.infrastructure.config.properties.JwtProperties;
import com.barisozy.cleanarchitecture.infrastructure.config.properties.PasswordProperties;
import com.barisozy.cleanarchitecture.web.config.properties.ApiProperties;
import com.barisozy.cleanarchitecture.web.config.properties.CorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Guards the fail-fast configuration contract of the property groups. */
class ConfigurationPropertiesValidationTest {

    @Test
    void rejectsIncompleteContractsAndNonPositiveTokenLifetimes() {
        runner(ApiPropertiesConfiguration.class)
                .withPropertyValues("app.api.problem-base-uri=https://api.example.com/problems")
                .run(context -> assertThat(context).hasFailed());
        runner(ApiPropertiesConfiguration.class)
                .withPropertyValues(
                        "app.api.problem-base-uri=https://api.example.com/problems",
                        "app.api.public-base-url=not-a-url")
                .run(context -> assertThat(context).hasFailed());
        runner(ApiPropertiesConfiguration.class)
                .withPropertyValues(
                        "app.api.problem-base-uri=ftp://api.example.com/problems",
                        "app.api.public-base-url=https://api.example.com")
                .run(context -> assertThat(context).hasFailed());

        jwtRunner()
                .withPropertyValues(
                        "app.security.jwt.audience=cleanarchitecture-api", "app.security.jwt.access-token-ttl=PT15M")
                .run(context -> assertThat(context).hasFailed());
        jwtRunner()
                .withPropertyValues(
                        "app.security.jwt.issuer=https://api.example.com", "app.security.jwt.access-token-ttl=PT15M")
                .run(context -> assertThat(context).hasFailed());
        jwtRunner()
                .withPropertyValues(
                        "app.security.jwt.issuer=https://api.example.com",
                        "app.security.jwt.audience=cleanarchitecture-api",
                        "app.security.jwt.access-token-ttl=PT0S")
                .run(context -> assertThat(context).hasFailed());
        jwtRunner()
                .withPropertyValues(
                        "app.security.jwt.issuer=https://api.example.com",
                        "app.security.jwt.audience=cleanarchitecture-api",
                        "app.security.jwt.access-token-ttl=PT-1S")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void validatesTheSecretWhenItIsSupplied() {
        jwtRunner()
                .withPropertyValues(
                        "app.security.jwt.issuer=https://api.example.com",
                        "app.security.jwt.audience=cleanarchitecture-api",
                        "app.security.jwt.access-token-ttl=PT15M",
                        "app.security.jwt.secret=" + AuthTestData.tooShortSecret())
                .run(context -> assertThat(context).hasFailed());
        jwtRunner()
                .withPropertyValues(
                        "app.security.jwt.issuer=https://api.example.com",
                        "app.security.jwt.audience=cleanarchitecture-api",
                        "app.security.jwt.access-token-ttl=PT15M",
                        "app.security.jwt.secret=this-is-not-base64!")
                .run(context -> assertThat(context).hasFailed());
        jwtRunner()
                .withPropertyValues(
                        "app.security.jwt.issuer=https://api.example.com",
                        "app.security.jwt.audience=cleanarchitecture-api",
                        "app.security.jwt.access-token-ttl=PT15M",
                        "app.security.jwt.secret=" + AuthTestData.SECRET)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void validatesTheBcryptCostFactor() {
        runner(PasswordPropertiesConfiguration.class)
                .withPropertyValues("app.security.password.bcrypt-strength=3")
                .run(context -> assertThat(context).hasFailed());
        runner(PasswordPropertiesConfiguration.class)
                .withPropertyValues("app.security.password.bcrypt-strength=32")
                .run(context -> assertThat(context).hasFailed());
        runner(PasswordPropertiesConfiguration.class)
                .withPropertyValues("app.security.password.bcrypt-strength=12")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void rejectsAWildcardCorsOriginWithCredentials() {
        runner(CorsPropertiesConfiguration.class)
                .withPropertyValues(
                        "app.security.cors.allowed-origins=*",
                        "app.security.cors.allow-credentials=true",
                        "app.security.cors.max-age=PT1H")
                .run(context -> assertThat(context).hasFailed());

        runner(CorsPropertiesConfiguration.class)
                .withPropertyValues(
                        "app.security.cors.allowed-origins=*",
                        "app.security.cors.allow-credentials=false",
                        "app.security.cors.max-age=PT1H")
                .run(context -> assertThat(context).hasNotFailed());
        runner(CorsPropertiesConfiguration.class)
                .withPropertyValues(
                        "app.security.cors.allowed-origins=https://app.example.com",
                        "app.security.cors.allow-credentials=true",
                        "app.security.cors.max-age=PT1H")
                .run(context -> assertThat(context).hasNotFailed());
    }

    private static ApplicationContextRunner jwtRunner() {
        return runner(JwtPropertiesConfiguration.class);
    }

    private static ApplicationContextRunner runner(Class<?> configuration) {
        return new ApplicationContextRunner().withUserConfiguration(configuration);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ApiProperties.class)
    static class ApiPropertiesConfiguration {}

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class JwtPropertiesConfiguration {}

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PasswordProperties.class)
    static class PasswordPropertiesConfiguration {}

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CorsProperties.class)
    static class CorsPropertiesConfiguration {}
}
