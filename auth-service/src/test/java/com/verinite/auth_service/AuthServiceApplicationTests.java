package com.verinite.auth_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest  // Now uses src/test/resources/application.yml → H2, no RabbitMQ, no Eureka
class AuthServiceApplicationTests {

	@Test
	void contextLoads() {
		// Verifies Spring context starts cleanly — will now complete in seconds
	}
}