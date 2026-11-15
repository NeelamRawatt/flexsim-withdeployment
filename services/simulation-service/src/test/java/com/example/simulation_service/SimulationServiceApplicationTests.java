package com.example.simulation_service;

import java.time.Duration;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Checks that the whole application starts (Flyway + Hibernate "validate" + Security)
 * against a REAL PostgreSQL that is started in Docker only for this test.
 * Skipped when Docker is not running. Test values only; your .env and database are not used.
 */
@SpringBootTest(properties = {
		"server.port=0",
		"app.outbox.enabled=false", // no Kafka: do not try to send outbox rows
		"spring.kafka.listener.auto-startup=false" // no Kafka: do not start the Kafka listeners
})
@EnabledIf(value = "dockerIsAvailable", disabledReason = "Docker is not running, so the PostgreSQL container cannot start")
class SimulationServiceApplicationTests {

	static {
		// Same as the main() method: the database driver does not accept "Asia/Calcutta".
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	private static final GenericContainer<?> POSTGRES = new GenericContainer<>(DockerImageName.parse("postgres:16"))
			.withEnv("POSTGRES_USER", "test")
			.withEnv("POSTGRES_PASSWORD", "test")
			.withEnv("POSTGRES_DB", "simulation_test")
			.withExposedPorts(5432)
			.waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2))
			.withStartupTimeout(Duration.ofSeconds(180));

	static boolean dockerIsAvailable() {
		try {
			return DockerClientFactory.instance().isDockerAvailable();
		} catch (Exception e) {
			return false;
		}
	}

	@DynamicPropertySource
	static void testSettings(DynamicPropertyRegistry registry) {
		if (!POSTGRES.isRunning()) {
			POSTGRES.start();
		}
		registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + POSTGRES.getHost() + ":"
				+ POSTGRES.getMappedPort(5432) + "/simulation_test");
		registry.add("spring.datasource.username", () -> "test");
		registry.add("spring.datasource.password", () -> "test");
		registry.add("app.security.jwt.secret", () -> "integration-test-secret-integration-test-1");
	}

	@Test
	void contextLoads() {
	}

}
