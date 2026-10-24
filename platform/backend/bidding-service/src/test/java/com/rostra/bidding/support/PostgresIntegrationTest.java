package com.rostra.bidding.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests that need a real database. One Postgres container is started per test JVM
 * and shared by every subclass (Testcontainers' Ryuk sidecar removes it when the JVM exits), and its URL is
 * wired into the Spring context with {@link DynamicPropertySource}.
 *
 * Needs a running Docker daemon. Subclasses add their own {@code @SpringBootTest}.
 */
public abstract class PostgresIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void applicationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> TestTokens.JWT_SECRET);
    }
}
