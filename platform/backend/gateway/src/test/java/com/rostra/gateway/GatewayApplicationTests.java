package com.rostra.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "JWT_SECRET=test-only-secret-key-not-for-production-use-32bytes+")
class GatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}
