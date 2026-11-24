package com.example.people;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class DatabaseTestSupport {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String host = env("APP_DB_HOST", "127.0.0.1");
        String port = env("APP_DB_PORT", "5432");
        String application = env("APP_DB_NAME", "person_training");
        String test = env("APP_TEST_DB_NAME", "person_training_test");
        if ((!host.equals("127.0.0.1") && !host.equals("localhost")) || !port.matches("[0-9]{1,5}") || !test.matches("[a-z][a-z0-9_]*_test") || test.equals(application)) {
            throw new IllegalStateException("Integration checks require a separate local database ending in _test");
        }
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + host + ":" + port + "/" + test);
        registry.add("app.seed-demo-data", () -> false);
    }

    private static String env(String key, String fallback) {
        return System.getenv().getOrDefault(key, fallback);
    }
}
