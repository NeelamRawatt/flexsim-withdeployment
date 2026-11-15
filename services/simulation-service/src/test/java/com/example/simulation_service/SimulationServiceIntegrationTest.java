package com.example.simulation_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.simulation_service.client.ExperimentClient;
import com.example.simulation_service.client.ExperimentSummary;
import com.example.simulation_service.client.FileClient;
import com.example.simulation_service.consumer.ExperimentDeletedListener;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * INTEGRATION TEST: the real simulation-service + a real PostgreSQL (in Docker, only for this test).
 *
 * REAL here:   Spring Boot, Security + JWT filter, Flyway V1 (incl. the "one active run" index and the
 *              status CHECK rule), Hibernate validate, the @Version column, controllers, services,
 *              the outbox rows, the experiment.deleted listener method, PostgreSQL 16.
 * FAKE here:   experiment-service and file-service (ExperimentClient / FileClient are mocks),
 *              Kafka (outbox sender + listener containers off; the listener method is called directly),
 *              the login (tokens made here with a test secret).
 *
 * SAFE: test database, test user, test secret. Your .env and real database are never used.
 * Every test uses its OWN experiment id, because all tests share one database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "server.port=0",
                "app.outbox.enabled=false",                    // no Kafka: don't send outbox rows
                "spring.kafka.listener.auto-startup=false",    // no Kafka: don't start the listeners
                "app.services.experiment.base-url=http://localhost:1",   // never called: mocks
                "app.services.file.base-url=http://localhost:1"
        })
@EnabledIf(value = "dockerIsAvailable", disabledReason = "Docker is not running, so the PostgreSQL container cannot start")
class SimulationServiceIntegrationTest {

    private static final String TEST_JWT_SECRET = "integration-test-secret-integration-test-1";

    static {
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
        registry.add("app.security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("app.security.allowed-origins", () -> "http://localhost:3000");
    }

    @MockitoBean
    private ExperimentClient experimentClient;

    @MockitoBean
    private FileClient fileClient;

    @Autowired
    private ExperimentDeletedListener experimentDeletedListener;

    @Autowired
    private DataSource dataSource;

    @Value("${local.server.port}")
    private int port;

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    private static final String OPERATOR = token(101, "op1", "OPERATOR");
    private static final String OTHER_OPERATOR = token(102, "op2", "OPERATOR");
    private static final String VIEWER = token(104, "viewer", "VIEWER");
    private static final String WORKER = token(900, "flexsim-worker", "WORKER");

    // ================================================================ tests

    @Test
    void flywayCreatedTheTables() throws Exception {
        assertThat(queryLong("select count(*) from flyway_schema_history where success = true")).isEqualTo(1);
        assertThat(queryLong("select count(*) from information_schema.tables where table_schema = 'public' "
                + "and table_name in ('simulation_run', 'outbox_event')")).isEqualTo(2);
    }

    @Test
    void withoutLoginEverythingIsBlocked() throws Exception {
        assertThat(call("GET", "/api/simulation/runs/1", null, null).status()).isEqualTo(401);
        assertThat(call("POST", "/api/simulation/startSimulation", null, "{\"experimentId\":1}").status()).isEqualTo(401);
    }

    @Test
    void fullRunLifecycle() throws Exception {
        long exp = 5001;
        experimentIsReady(exp);

        // 1. start -> 202 QUEUED, 1 run row, 2 outbox rows (job + QUEUED status)
        Reply started = expect(start(exp, OPERATOR), 202);
        assertThat(started.body()).contains("\"status\":\"QUEUED\"").contains("\"requestedBy\":\"op1\"");
        long runId = runId(started.body());
        assertThat(queryLong("select count(*) from outbox_event where event_key = '" + exp + "' "
                + "and topic = 'simulation.trigger.requested'")).isEqualTo(1);
        assertThat(queryLong("select count(*) from outbox_event where event_key = '" + exp + "' "
                + "and topic = 'simulation.status'")).isEqualTo(1);

        // 2. a second start while QUEUED -> 409
        expect(start(exp, OPERATOR), 409);

        // 3. the worker reports progress
        expect(status(runId, WORKER, "{\"status\":\"RUNNING\",\"stage\":\"OPENING_FLEXSIM\",\"message\":\"Opening FlexSim\"}"), 200);
        expect(status(runId, WORKER, "{\"status\":\"RUNNING\",\"stage\":\"LOADING_PARCELS\",\"message\":\"Loading parcels\"}"), 200);
        Reply done = expect(status(runId, WORKER, "{\"status\":\"COMPLETED\",\"message\":\"Simulation finished\"}"), 200);
        assertThat(done.body()).contains("\"status\":\"COMPLETED\"");
        assertThat(queryLong("select count(*) from simulation_run where id = " + runId
                + " and started_at is not null and finished_at is not null")).isEqualTo(1);

        // 4. the same COMPLETED again -> ignored (no new outbox row); RUNNING after COMPLETED -> 409
        long statusRows = queryLong("select count(*) from outbox_event where event_key = '" + exp + "' and topic = 'simulation.status'");
        expect(status(runId, WORKER, "{\"status\":\"COMPLETED\",\"message\":\"again\"}"), 200);
        assertThat(queryLong("select count(*) from outbox_event where event_key = '" + exp + "' and topic = 'simulation.status'"))
                .isEqualTo(statusRows);   // QUEUED + RUNNING + RUNNING + COMPLETED = 4, unchanged
        assertThat(statusRows).isEqualTo(4);
        expect(status(runId, WORKER, "{\"status\":\"RUNNING\",\"stage\":\"X\"}"), 409);

        // 5. finished -> a new run is allowed; read URLs work
        expect(start(exp, OPERATOR), 202);
        assertThat(expect(call("GET", "/api/simulation/runs/byExperiment/" + exp, OTHER_OPERATOR, null), 200).body())
                .contains("COMPLETED").contains("QUEUED");
        assertThat(expect(call("GET", "/api/simulation/runs/latest/" + exp, OPERATOR, null), 200).body())
                .contains("\"status\":\"QUEUED\"");
    }

    @Test
    void filesNotReadyGives400AndNothingIsSaved() throws Exception {
        long exp = 5002;
        when(experimentClient.requireOwnedBy(eq(exp), any(), any())).thenReturn(summary(exp));
        when(fileClient.filesReady(eq(exp), any())).thenReturn(false);

        assertThat(expect(start(exp, OPERATOR), 400).body()).contains("Required files are not uploaded yet");
        assertThat(queryLong("select count(*) from simulation_run where experiment_id = " + exp)).isZero();
        assertThat(queryLong("select count(*) from outbox_event where event_key = '" + exp + "'")).isZero();
    }

    @Test
    void rolesAreChecked() throws Exception {
        long exp = 5003;
        experimentIsReady(exp);

        expect(start(exp, VIEWER), 403);                       // a viewer may not start runs
        long runId = runId(expect(start(exp, OPERATOR), 202).body());
        expect(status(runId, OPERATOR, "{\"status\":\"RUNNING\",\"stage\":\"X\"}"), 403);   // only the worker reports status
        expect(call("POST", "/api/simulation/runs/" + runId + "/cancel", OTHER_OPERATOR, null), 403);  // not the starter

        Reply cancelled = expect(call("POST", "/api/simulation/runs/" + runId + "/cancel", OPERATOR, null), 200);
        assertThat(cancelled.body()).contains("\"status\":\"CANCELLED\"");
    }

    @Test
    void theDatabaseItselfAllowsOnlyOneActiveRunPerExperiment() throws Exception {
        long exp = 5004;
        execute("insert into simulation_run (experiment_id, status, requested_by, queued_at, updated_at) "
                + "values (" + exp + ", 'QUEUED', 'x', now(), now())");
        boolean refused = false;
        try {
            execute("insert into simulation_run (experiment_id, status, requested_by, queued_at, updated_at) "
                    + "values (" + exp + ", 'RUNNING', 'x', now(), now())");
        } catch (java.sql.SQLException e) {
            refused = true;   // uk_simulation_run_one_active_per_experiment
        }
        assertThat(refused).isTrue();
    }

    @Test
    void anExperimentDeletedMessageDeletesAllItsRuns() throws Exception {
        long exp = 5005;
        experimentIsReady(exp);
        long runId = runId(expect(start(exp, OPERATOR), 202).body());
        expect(status(runId, WORKER, "{\"status\":\"FAILED\",\"message\":\"test\"}"), 200);
        expect(start(exp, OPERATOR), 202);
        assertThat(queryLong("select count(*) from simulation_run where experiment_id = " + exp)).isEqualTo(2);

        String message = "{\"eventId\":\"it-1\",\"experimentId\":" + exp + ",\"deletedBy\":\"op1\"}";
        experimentDeletedListener.onExperimentDeleted(
                new ConsumerRecord<>("experiment.deleted", 0, 0L, String.valueOf(exp), message));
        assertThat(queryLong("select count(*) from simulation_run where experiment_id = " + exp)).isZero();

        // the same message again: no error
        experimentDeletedListener.onExperimentDeleted(
                new ConsumerRecord<>("experiment.deleted", 0, 1L, String.valueOf(exp), message));
        assertThat(call("GET", "/api/simulation/runs/" + runId, OPERATOR, null).status()).isEqualTo(404);
    }

    // ================================================================ helpers

    private void experimentIsReady(long exp) {
        when(experimentClient.requireOwnedBy(eq(exp), any(), any())).thenReturn(summary(exp));
        when(fileClient.filesReady(eq(exp), any())).thenReturn(true);
    }

    private static ExperimentSummary summary(long exp) {
        return new ExperimentSummary(exp, "op1", "IT experiment", "2026-10-21T23:00:00", "2026-10-21T06:30:00", 3,
                0, 1, 1, 0, 0, 90, 65, 75);
    }

    private static String token(long userId, String username, String role) {
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("uid", userId)
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private record Reply(int status, String body) {
    }

    private static Reply expect(Reply reply, int status) {
        assertThat(reply.status()).as("answer was: " + reply.body()).isEqualTo(status);
        return reply;
    }

    private Reply start(long exp, String token) throws Exception {
        return call("POST", "/api/simulation/startSimulation", token, "{\"experimentId\":" + exp + "}");
    }

    private Reply status(long runId, String token, String json) throws Exception {
        return call("POST", "/api/simulation/runs/" + runId + "/status", token, json);
    }

    private Reply call(String method, String path, String token, String json) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(30));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (json != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(json));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), response.body());
    }

    private static long runId(String body) {
        Matcher m = Pattern.compile("\"runId\"\\s*:\\s*(\\d+)").matcher(body);
        assertThat(m.find()).as("runId in: " + body).isTrue();
        return Long.parseLong(m.group(1));
    }

    private long queryLong(String sql) throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void execute(String sql) throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }
}
