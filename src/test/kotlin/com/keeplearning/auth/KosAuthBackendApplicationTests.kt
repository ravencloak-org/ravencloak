package com.keeplearning.auth

import org.junit.jupiter.api.Test

/**
 * Basic sanity test. Full context tests require Keycloak.
 * For integration testing, use @SpringBootTest with a running Keycloak instance.
 */
class KosAuthBackendApplicationTests {

	@Test
	fun sanityCheck() {
		// Basic test to ensure the test infrastructure works
		assert(true)
	}

}
