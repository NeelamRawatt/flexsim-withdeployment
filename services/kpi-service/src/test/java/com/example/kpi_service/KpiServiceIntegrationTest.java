package com.example.kpi_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kpi_service.event.SimulationKpiEvent;
import com.example.kpi_service.kpi.SimulationKpi;
import com.example.kpi_service.service.KpiCleanupService;
import com.example.kpi_service.service.KpiIngestService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * INTEGRATION TEST: the real kpi-service + a real PostgreSQL in Docker (Flyway, JSONB, security, URLs).
 * Kafka is switched off here; the test calls the saving service directly, exactly like the listener does.
 * All secrets are test-only values; your real database is never touched.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.port=0", "spring.kafka.listener.auto-startup=false"})
@EnabledIf(value = "dockerIsAvailable", disabledReason = "Docker is not running, so the PostgreSQL container cannot start")
class KpiServiceIntegrationTest {

    private static final String TEST_JWT_SECRET = "integration-test-secret-integration-test-1";

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    private static final GenericContainer<?> POSTGRES = new GenericContainer<>(DockerImageName.parse("postgres:16"))
            .withEnv("POSTGRES_USER", "test")
            .withEnv("POSTGRES_PASSWORD", "test")
            .withEnv("POSTGRES_DB", "kpi_test")
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
                + POSTGRES.getMappedPort(5432) + "/kpi_test");
        registry.add("spring.datasource.username", () -> "test");
        registry.add("spring.datasource.password", () -> "test");
        registry.add("app.security.jwt.secret", () -> TEST_JWT_SECRET);
    }

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private KpiIngestService ingestService;

    @Autowired
    private KpiCleanupService cleanupService;

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    private static String token(String role) {
        return Jwts.builder()
                .subject("tester")
                .claim("role", role)
                .claim("uid", 7)
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(30)).GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static SimulationKpiEvent event(String eventId, long experimentId, double time, int blocked) {
        SimulationKpi kpi = new SimulationKpi(experimentId, time, Map.of("CH1", (int) time), Map.of(), null, null,
                null, null, null, null, Map.of("BOOM", 3), null, null, Map.of("CH1", blocked),
                null, null, null, null);
        return new SimulationKpiEvent(eventId, 9L, experimentId, "2026-11-16T05:00:00Z", kpi);
    }

    @Test
    void snapshotsAreSavedOnceAndReadBackInTimeOrder() throws Exception {
        long exp = 5001;
        assertThat(ingestService.save(event("e-2", exp, 120, 1))).isTrue();
        assertThat(ingestService.save(event("e-1", exp, 60, 0))).isTrue();
        assertThat(ingestService.save(event("e-1", exp, 60, 0))).isFalse();   // same message again: skipped

        HttpResponse<String> history = get("/api/metrics/" + exp, token("VIEWER"));

        assertThat(history.statusCode()).isEqualTo(200);
        assertThat(history.body()).contains("\"chutetypeThroughput\":{\"Boom\":3}");
        assertThat(history.body().indexOf("\"simulationTime\":60.0")).isLessThan(history.body().indexOf("\"simulationTime\":120.0"));
        assertThat(history.body().split("\"simulationTime\":60.0", -1)).hasSize(2);   // only ONCE

        HttpResponse<String> intervals = get("/api/metrics/blockage-intervals/" + exp, token("OPERATOR"));
        assertThat(intervals.statusCode()).isEqualTo(200);
        assertThat(intervals.body()).contains("\"chuteId\":\"CH1\"", "\"fromSimSec\":120.0");
    }

    @Test
    void noLoginIs401AndNoDataIs204() throws Exception {
        assertThat(get("/api/metrics/5002", null).statusCode()).isEqualTo(401);
        assertThat(get("/api/metrics/blockage-intervals/5002", token("VIEWER")).statusCode()).isEqualTo(204);
        assertThat(get("/api/metrics/5002", token("VIEWER")).body()).isEqualTo("[]");
    }

    @Test
    void deletedExperimentLosesItsKpis() throws Exception {
        long exp = 5003;
        ingestService.save(event("d-1", exp, 60, 0));
        ingestService.save(event("d-2", exp, 120, 0));

        assertThat(cleanupService.deleteAllOfExperiment(exp)).isEqualTo(2);
        assertThat(get("/api/metrics/" + exp, token("VIEWER")).body()).isEqualTo("[]");
        assertThat(cleanupService.deleteAllOfExperiment(exp)).isZero();   // second time: nothing left, no error
    }
}
