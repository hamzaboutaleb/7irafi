package com.herafi.notification_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Also the guard that the migrations still run on H2: the context starts Flyway
 * and Hibernate validates the entities against what it created.
 */
@ActiveProfiles("test")
@SpringBootTest
class NotificationServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
