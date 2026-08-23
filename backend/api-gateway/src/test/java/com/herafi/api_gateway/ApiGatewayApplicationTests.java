package com.herafi.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"security.jwt.secret=7irafi-test-jwt-secret-that-is-at-least-32-bytes",
		"security.jwt.issuer=7irafi-auth-service-test",
		"gateway.shared-secret=test-gateway-key",
		"gateway.cors.allowed-origin=http://localhost:4200",
})
class ApiGatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}
