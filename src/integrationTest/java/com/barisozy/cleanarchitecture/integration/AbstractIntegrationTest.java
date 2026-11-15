package com.barisozy.cleanarchitecture.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.barisozy.cleanarchitecture.domain.user.Email;
import com.barisozy.cleanarchitecture.domain.user.PasswordHash;
import com.barisozy.cleanarchitecture.domain.user.RoleName;
import com.barisozy.cleanarchitecture.domain.user.User;
import com.barisozy.cleanarchitecture.domain.user.UserRepository;
import com.barisozy.cleanarchitecture.fixtures.UserTestData;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class of the integration tests: the whole application, a real PostgreSQL 18 container and
 * the HTTP adapter.
 *
 * <p>The container uses the singleton pattern so that every test class shares one instance and
 * Spring's application context cache stays effective.
 *
 * <p>Every test starts with an empty database. The template has exactly two tables, so the reset
 * is two deletes; the order matters, because the todos reference their owner.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    static {
        POSTGRES.start();
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetState() {
        jdbcTemplate.update("DELETE FROM todos");
        jdbcTemplate.update("DELETE FROM users");
    }

    /** A fresh e-mail address, so that no two tests share an account. */
    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    /** The password of the test accounts. */
    protected static String password() {
        return UserTestData.RAW_PASSWORD;
    }

    /** Registers a user and returns its access token. */
    protected String register(String email) {
        MvcTestResult result = mvc.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password()))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return tokenOf(result);
    }

    /** Logs in with the given credentials and returns the access token. */
    protected String login(String email, String rawPassword) {
        MvcTestResult result = mvc.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, rawPassword))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        return tokenOf(result);
    }

    /**
     * Creates an administrator and returns its access token.
     *
     * <p>The registration endpoint always creates a regular user, so the administrator is written
     * through the domain model and then authenticates over HTTP like every other account.
     */
    protected String createAdmin(String prefix) {
        String email = uniqueEmail(prefix);
        User admin = User.register(
                Email.of(email), PasswordHash.of(passwordEncoder.encode(password())), RoleName.ADMIN, Instant.now());
        userRepository.insert(admin);
        return login(email, password());
    }

    /** Reads a value from the JSON body of a response. */
    protected static String jsonString(MvcTestResult result, String path) {
        String content = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        Object value = JsonPath.read(content, path);
        return Objects.requireNonNull(value, "the response body must contain '" + path + "'")
                .toString();
    }

    private static String tokenOf(MvcTestResult result) {
        return jsonString(result, "$.accessToken");
    }
}
