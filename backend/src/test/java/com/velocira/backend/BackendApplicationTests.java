package com.velocira.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test to verify the Spring application context loads.
 */
@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
