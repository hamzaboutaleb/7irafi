package com.herafi.auth_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// "test" profile: uses application-test.properties (H2, test keys) instead of real env vars
@ActiveProfiles("test")
@SpringBootTest
class AuthServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
