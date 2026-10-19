package com.example.experiment_service;

import static org.assertj.core.api.Assertions.assertThat;

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
 * INTEGRATION TEST: the real application + a real PostgreSQL (started in Docker just for this test).
 *
 * What is real here:   Spring Boot, Spring Security, the JWT filter, Flyway (V1 and V2), Hibernate "validate",
 *                      the controllers, services and repositories, and PostgreSQL 16.
 * What is NOT real:    the login. We make the tokens ourselves with the same test secret (auth-service is not needed).
 *
 * SAFETY: the database address, user, password and JWT secret are replaced below with test values
 * (@DynamicPropertySource). So this test NEVER touches your real database or your .env secrets.
 * The container is thrown away when the tests finish.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.port=0")
@EnabledIf(value = "dockerIsAvailable", disabledReason = "Docker is not running, so the PostgreSQL container cannot start")
class ExperimentServiceIntegrationTest {

    // A FAKE secret used only in this test (40+ characters, with "-" so it is read as plain text).
    private static final String TEST_JWT_SECRET = "integration-test-secret-integration-test-1";

    static {
        // Same as the main() method: the database driver does not accept "Asia/Calcutta".
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    // The container is only DEFINED here. It is started later, when Spring needs the database address.
    private static final GenericContainer<?> POSTGRES = new GenericContainer<>(DockerImageName.parse("postgres:16"))
            .withEnv("POSTGRES_USER", "test")
            .withEnv("POSTGRES_PASSWORD", "test")
            .withEnv("POSTGRES_DB", "experiment_test")
            .withExposedPorts(5432)
            // PostgreSQL writes "ready to accept connections" twice while starting (setup, then the real server).
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
                + POSTGRES.getMappedPort(5432) + "/experiment_test");
        registry.add("spring.datasource.username", () -> "test");
        registry.add("spring.datasource.password", () -> "test");
        registry.add("app.security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("app.security.allowed-origins", () -> "http://localhost:3000");
    }

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private DataSource dataSource;

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    // ----------------------------------------------------------- helpers

    private static String token(long userId, String username, String role) {
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("uid", userId)
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private static final String OPERATOR_1 = token(101, "op1", "OPERATOR");
    private static final String OPERATOR_2 = token(102, "op2", "OPERATOR");
    private static final String ADMIN = token(103, "boss", "ADMIN");
    private static final String VIEWER = token(104, "viewer", "VIEWER");

    private record Reply(int status, String body) {
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

    /** Checks the status and, if it is wrong, shows the answer text in the failure message. */
    private static Reply expect(Reply reply, int status) {
        assertThat(reply.status()).as("answer was: " + reply.body()).isEqualTo(status);
        return reply;
    }

    private long queryLong(String sql) throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private String queryString(String sql) throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static long idOf(Reply reply) {
        Matcher m = Pattern.compile("\"experimentId\":(\\d+)").matcher(reply.body());
        assertThat(m.find()).as("no experimentId in: " + reply.body()).isTrue();
        return Long.parseLong(m.group(1));
    }

    private static int count(String text, String part) {
        return text.split(Pattern.quote(part), -1).length - 1;
    }

    private long createExperiment(String token) throws Exception {
        String json = """
                {"experimentName":"it-run","terminal":"TC1","sortingType":"A","useCaseId":1,
                 "selectedDate":"2026-10-19","startTime":"23:00:00","endTime":"06:30:00"}
                """;
        return idOf(expect(call("POST", "/api/experiments/createExperiment", token, json), 200));
    }

    // ------------------------------------------------------------ the tests

    @Test
    void flywayRanBothMigrationsAndSeededTheChutes() throws Exception {
        assertThat(queryLong("select count(*) from flyway_schema_history where success = true")).isEqualTo(2);
        assertThat(queryLong("select count(*) from chute_master")).isEqualTo(97);
    }

    // NOTE: there is no test for /actuator/health here on purpose. Inside @SpringBootTest of Spring Boot 4
    // that page answered 404 (the real running service answers 200). We check the REAL health page
    // later, when the service runs in Docker and Kubernetes, because that is where it matters.

    @Test
    void noTokenOrBadTokenGives401() throws Exception {
        expect(call("GET", "/api/experiments/history", null, null), 401);
        expect(call("GET", "/api/experiments/history", "not-a-real-token", null), 401);
        expect(call("GET", "/api/templates", null, null), 401);
    }

    @Test
    void viewerIsBlockedFromExperimentUrlsButCanReadTemplates() throws Exception {
        expect(call("GET", "/api/experiments/history", VIEWER, null), 403);
        expect(call("GET", "/api/templates", VIEWER, null), 200);
        expect(call("GET", "/api/chute-master/sortname-suggestions?query=201", VIEWER, null), 200);
    }

    @Test
    void realNotFoundIsNotTurnedInto401() throws Exception {
        expect(call("GET", "/api/experiments/999999999", OPERATOR_1, null), 404);
        expect(call("GET", "/api/this-url-does-not-exist", OPERATOR_1, null), 404);
    }

    @Test
    void experimentLifecycleWithOwnerRules() throws Exception {
        String createJson = """
                {"experimentName":"it-run","terminal":"TC1","sortingType":"A","useCaseId":1,
                 "selectedDate":"2026-10-19","startTime":"23:00:00","endTime":"06:30:00",
                 "flexibleResources":0,"phoneBoomChute":1,"minimumCageOccupancy":10,
                 "maximumCageOccupancy":95,"sorterFullPercent":150}
                """;
        Reply created = expect(call("POST", "/api/experiments/createExperiment", OPERATOR_1, createJson), 200);
        long id = idOf(created);

        assertThat(id).isGreaterThanOrEqualTo(1001);
        assertThat(created.body()).contains("\"status\":\"PENDING\"")
                .contains("\"phoneBoomChute\":0")
                .contains("\"minimumCageOccupancy\":50")
                .contains("\"maximumCageOccupancy\":90")
                .contains("\"sorterFullPercent\":100")
                .contains("\"startTime\":\"2026-10-19T23:00:00\"");
        // The owner was taken from the token, saved in the real database.
        assertThat(queryString("select user_id || ':' || owner_username from experiment where experiment_id = " + id))
                .isEqualTo("101:op1");

        expect(call("GET", "/api/experiments/" + id, OPERATOR_2, null), 200);   // everybody allowed may read

        String updateJson = """
                {"experimentName":"it-run-2","terminal":"TC1","sortingType":"A","useCaseId":1,
                 "selectedDate":"2026-10-19","startTime":"2026-10-19T22:00:00","endTime":"06:30:00"}
                """;
        expect(call("PUT", "/api/experiments/" + id, OPERATOR_2, updateJson), 403);      // not the owner
        Reply updated = expect(call("PUT", "/api/experiments/" + id, OPERATOR_1, updateJson), 200);
        assertThat(updated.body()).contains("\"experimentName\":\"it-run-2\"");

        Reply history = expect(call("GET", "/api/experiments/history?status=PENDING&sort=createdAt,desc", OPERATOR_1, null), 200);
        assertThat(history.body()).contains("\"experimentId\":" + id);
        expect(call("GET", "/api/experiments/history?sort=password,asc", OPERATOR_1, null), 400);

        expect(call("DELETE", "/api/experiments/" + id, OPERATOR_2, null), 403);          // not the owner
        assertThat(call("DELETE", "/api/experiments/" + id, ADMIN, null).status()).isIn(200, 204);   // admin may
        expect(call("GET", "/api/experiments/" + id, OPERATOR_1, null), 404);
    }

    @Test
    void resourceListsReplaceOldRowsAndTheDatabaseCascadesOnDelete() throws Exception {
        long id = createExperiment(OPERATOR_1);

        String twoZones = """
                [{"experimentId":%d,"shiftId":2,"zoneId":"Z1","resourceId":1,"chutes":"[7,8]"},
                 {"experimentId":%d,"shiftId":1,"zoneId":"Z1","resourceId":1,"chutes":"[1,2,3]"}]
                """.formatted(id, id);
        expect(call("POST", "/api/resourceDetails/setZoneDetails", OPERATOR_2, twoZones), 403);   // not the owner
        expect(call("POST", "/api/resourceDetails/setZoneDetails", OPERATOR_1, twoZones), 200);

        Reply zones = expect(call("GET", "/api/resourceDetails/getZoneDetails/" + id, OPERATOR_1, null), 200);
        assertThat(count(zones.body(), "\"zoneId\"")).isEqualTo(2);
        // shift 1 comes before shift 2 (sorted), even though we sent shift 2 first
        assertThat(zones.body().indexOf("\"chuteIds\":[1,2,3]")).isLessThan(zones.body().indexOf("\"chuteIds\":[7,8]"));

        // Saving again REPLACES the old rows (it does not add to them).
        String oneZone = """
                [{"experimentId":%d,"shiftId":1,"zoneId":"Z9","resourceId":1,"chutes":"5"}]
                """.formatted(id);
        expect(call("POST", "/api/resourceDetails/setZoneDetails", OPERATOR_1, oneZone), 200);
        Reply replaced = expect(call("GET", "/api/resourceDetails/getZoneDetails/" + id, OPERATOR_1, null), 200);
        assertThat(count(replaced.body(), "\"zoneId\"")).isEqualTo(1);

        String infeed = """
                [{"experimentId":%d,"shiftId":1,"zoneId":"Z1","infeed":"I1","tc":"TCA","noOfResources":3,"active":true}]
                """.formatted(id);
        expect(call("POST", "/api/resourceDetails/setInfeedDetails", OPERATOR_1, infeed), 200);
        assertThat(expect(call("GET", "/api/resourceDetails/getInfeedDetails/" + id, OPERATOR_1, null), 200).body())
                .contains("\"tc\":\"TCA\"");

        String plan = """
                [{"experimentId":%d,"oldSortName":"OLD","newSortName":"NEW"}]
                """.formatted(id);
        expect(call("POST", "/api/resourceDetails/setSortPlanDetails", OPERATOR_1, plan), 200);
        assertThat(expect(call("GET", "/api/resourceDetails/clone/getSortPlanDetails/" + id, OPERATOR_1, null), 200).body())
                .contains("\"newSortName\":\"NEW\"");

        String limits = """
                {"experimentId":%d,"minHeight":1,"maxHeight":50,"minLength":1,"maxLength":100,
                 "minWidth":1,"maxWidth":80,"minWeight":0.5,"maxWeight":30}
                """.formatted(id);
        expect(call("POST", "/api/acceptable-parcel-unit/save", OPERATOR_1, limits), 200);
        assertThat(expect(call("GET", "/api/acceptable-parcel-unit/byExperiment/" + id, OPERATOR_1, null), 200).body())
                .contains("\"maxWeight\":30");

        String childRows = "select (select count(*) from zone_resource_detail where exp_id = " + id + ")"
                + " + (select count(*) from infeed_resource_detail where exp_id = " + id + ")"
                + " + (select count(*) from sort_plan_detail where exp_id = " + id + ")"
                + " + (select count(*) from acceptable_parcel_unit where exp_id = " + id + ")";
        assertThat(queryLong(childRows)).isEqualTo(4);

        assertThat(call("DELETE", "/api/experiments/" + id, OPERATOR_1, null).status()).isIn(200, 204);
        assertThat(queryLong(childRows)).isEqualTo(0);   // the database removed the child rows (ON DELETE CASCADE)
    }

    @Test
    void badInputGivesCleanErrorsNotServerErrors() throws Exception {
        long id = createExperiment(OPERATOR_1);

        String badChutes = """
                [{"experimentId":%d,"shiftId":1,"zoneId":"Z1","resourceId":1,"chutes":"[1,abc]"}]
                """.formatted(id);
        expect(call("POST", "/api/resourceDetails/setZoneDetails", OPERATOR_1, badChutes), 400);

        String unknown = """
                [{"experimentId":999999999,"shiftId":1,"zoneId":"Z1","resourceId":1,"chutes":"1"}]
                """;
        expect(call("POST", "/api/resourceDetails/setZoneDetails", OPERATOR_1, unknown), 404);
        expect(call("POST", "/api/resourceDetails/setZoneDetails", OPERATOR_1, "[]"), 200);
        expect(call("POST", "/api/resourceDetails/setZoneDetails", OPERATOR_1, "this is not json"), 400);

        String minAboveMax = """
                {"experimentId":%d,"minHeight":10,"maxHeight":5}
                """.formatted(id);
        expect(call("POST", "/api/acceptable-parcel-unit/save", OPERATOR_1, minAboveMax), 400);
        expect(call("GET", "/api/acceptable-parcel-unit/byExperiment/" + id, OPERATOR_1, null), 204);
    }

    @Test
    void templatesFollowTheRoleRules() throws Exception {
        String json = """
                {"type":"infeed","templateName":"it-template","fileName":"a.xlsx",
                 "data":[{"shiftId":"1","tc":"TCA","resourceCount":3},{"shiftId":"2","tc":"TCB","resourceCount":4}]}
                """;
        expect(call("POST", "/api/templates", OPERATOR_1, json), 403);   // only ADMIN or DEVELOPER may save
        Reply saved = expect(call("POST", "/api/templates", ADMIN, json), 200);
        Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(saved.body());
        assertThat(m.find()).as(saved.body()).isTrue();
        String templateId = m.group(1);

        Reply list = expect(call("GET", "/api/templates", OPERATOR_2, null), 200);   // everybody logged in may read
        assertThat(list.body()).contains("\"name\":\"it-template\"").contains("\"tc\":\"TCA\"");

        String badRow = """
                {"type":"infeed","data":[{"shiftId":"1","resourceCount":3}]}
                """;
        expect(call("POST", "/api/templates", ADMIN, badRow), 400);

        // Only the person who saved it may delete it. Another admin-role user gets 404.
        String otherAdmin = token(105, "boss2", "ADMIN");
        expect(call("DELETE", "/api/templates/" + templateId, otherAdmin, null), 404);
        expect(call("DELETE", "/api/templates/" + templateId, ADMIN, null), 204);
        expect(call("DELETE", "/api/templates/" + templateId, ADMIN, null), 404);
        assertThat(queryLong("select count(*) from resource_template_row where template_id = " + templateId)).isEqualTo(0);
    }

    @Test
    void chuteSuggestionsWork() throws Exception {
        Reply spiral = expect(call("GET", "/api/chute-master/sortname-suggestions?query=201", OPERATOR_1, null), 200);
        assertThat(count(spiral.body(), "\"sortName\"")).isEqualTo(13);
        Reply limited = expect(call("GET", "/api/chute-master/sortname-suggestions?limit=5", OPERATOR_1, null), 200);
        assertThat(count(limited.body(), "\"sortName\"")).isEqualTo(5);
        expect(call("GET", "/api/chute-master/sortname-suggestions?query=201", null, null), 401);
    }
}