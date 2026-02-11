package com.banca.ledger.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@ActiveProfiles("test")
public abstract class BaseIT {

    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        postgres.start(); // <-- CLAVE: asegura que esté arriba antes de que Spring cree el DataSource
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void cleanDb() {
        jdbc.execute("TRUNCATE TABLE ledger_entries RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE ledger_operations RESTART IDENTITY CASCADE");
    }
}
