package com.example.file_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.example.file_service.client.ExperimentClient;
import com.example.file_service.consumer.ExperimentDeletedListener;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import javax.sql.DataSource;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * INTEGRATION TEST: the real file-service + a real PostgreSQL (started in Docker only for this test).
 *
 * REAL here:   Spring Boot, Spring Security + the JWT filter, Flyway V1, Hibernate "validate",
 *              the upload URLs (real multipart requests), the Excel checks, DatabaseFileStorage,
 *              the outbox rows, the Kafka LISTENER method and PostgreSQL 16.
 * FAKE here:   - experiment-service (ExperimentClient is a mock: "is this experiment yours?")
 *              - Kafka itself: the outbox SENDER and the listener CONTAINER are switched off.
 *                We call the listener method directly with a message, like Kafka would.
 *              - the login: we make the tokens ourselves with a test secret.
 *
 * SAFETY: database, user, password and JWT secret are test values (@DynamicPropertySource).
 * Your real database and your .env are never used. The container is thrown away at the end.
 *
 * Every test uses its OWN experiment id, because all tests share one database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "server.port=0",
                "app.outbox.enabled=false",                    // no Kafka: don't try to send outbox rows
                "spring.kafka.listener.auto-startup=false",    // no Kafka: don't start the listener container
                "app.services.experiment.base-url=http://localhost:1"   // never called: ExperimentClient is a mock
        })
@EnabledIf(value = "dockerIsAvailable", disabledReason = "Docker is not running, so the PostgreSQL container cannot start")
class FileServiceIntegrationTest {

    // A FAKE secret used only in this test.
    private static final String TEST_JWT_SECRET = "integration-test-secret-integration-test-1";
    private static final String XLSX_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    static {
        // Same as main(): the database driver does not accept "Asia/Calcutta".
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    // Only DEFINED here. Started when Spring asks for the database address (see testSettings).
    private static final GenericContainer<?> POSTGRES = new GenericContainer<>(DockerImageName.parse("postgres:16"))
            .withEnv("POSTGRES_USER", "test")
            .withEnv("POSTGRES_PASSWORD", "test")
            .withEnv("POSTGRES_DB", "file_test")
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
                + POSTGRES.getMappedPort(5432) + "/file_test");
        registry.add("spring.datasource.username", () -> "test");
        registry.add("spring.datasource.password", () -> "test");
        registry.add("app.security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("app.security.allowed-origins", () -> "http://localhost:3000");
    }

    /** experiment-service is replaced by a fake. By default it says "yes, this experiment is yours". */
    @MockitoBean
    private ExperimentClient experimentClient;

    @Autowired
    private ExperimentDeletedListener experimentDeletedListener;

    @Autowired
    private DataSource dataSource;

    @Value("${local.server.port}")
    private int port;

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    private static final String OPERATOR_1 = token(101, "op1", "OPERATOR");
    private static final String OPERATOR_2 = token(102, "op2", "OPERATOR");
    private static final String ADMIN = token(103, "boss", "ADMIN");
    private static final String VIEWER = token(104, "viewer", "VIEWER");

    @BeforeEach
    void fakeExperimentService() {
        // Experiment 2000 belongs to someone else: experiment-service would say 403.
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to upload files for this experiment"))
                .when(experimentClient).requireOwnedBy(eq(2000L), any(), any());
    }

    // ================================================================ tests

    @Test
    void flywayCreatedTheTables() throws Exception {
        assertThat(queryLong("select count(*) from flyway_schema_history where success = true")).isEqualTo(1);
        assertThat(queryLong("select count(*) from information_schema.tables where table_schema = 'public' "
                + "and table_name in ('uploaded_file', 'file_content', 'outbox_event')")).isEqualTo(3);
    }

    @Test
    void withoutLoginEverythingIsBlocked() throws Exception {
        assertThat(get("/api/files/1", null).status()).isEqualTo(401);
        assertThat(get("/api/files/exists/1", null).status()).isEqualTo(401);
    }

    @Test
    void fullUploadFlow() throws Exception {
        long exp = 3001;

        // 1. INFEED -> 201, not ready yet (zone file missing)
        Reply infeed = expect(upload("infeed", OPERATOR_1, "infeed.xlsx", validInfeedFile(), exp), 201);
        assertThat(infeed.body()).contains("\"valid\":true").contains("\"filesReady\":false");
        assertThat(queryLong("select count(*) from uploaded_file where experiment_id = " + exp)).isEqualTo(1);
        assertThat(queryLong("select count(*) from file_content where storage_key like 'experiments/" + exp + "/%'")).isEqualTo(1);
        // ... and the Kafka message waits in the outbox (sender is off in this test)
        assertThat(queryLong("select count(*) from outbox_event where topic = 'file.validated' and event_key = '" + exp
                + "' and sent_at is null")).isEqualTo(1);

        // 2. ZONE_RESOURCE -> 201, now ready
        Reply zone = expect(upload("zone-resource", OPERATOR_1, "zones.xlsx", validZoneFile(), exp), 201);
        assertThat(zone.body()).contains("\"filesReady\":true");
        assertThat(expect(get("/api/files/exists/" + exp, OPERATOR_1), 200).body()).isEqualTo("true");

        // 3. INFEED again -> REPLACES the old one (still 2 files, still 2 contents)
        expect(upload("infeed", OPERATOR_1, "infeed-v2.xlsx", validInfeedFile(), exp), 201);
        assertThat(queryLong("select count(*) from uploaded_file where experiment_id = " + exp)).isEqualTo(2);
        assertThat(queryLong("select count(*) from file_content where storage_key like 'experiments/" + exp + "/%'")).isEqualTo(2);
        assertThat(queryLong("select count(*) from outbox_event where topic = 'file.validated' and event_key = '" + exp + "'"))
                .isEqualTo(3);

        // 4. list + details
        Reply list = expect(get("/api/files/byExperiment/" + exp, OPERATOR_2), 200);   // any logged-in user may read
        assertThat(list.body()).contains("INFEED").contains("ZONE_RESOURCE").contains("infeed-v2.xlsx");

        // 5. download: uploader and ADMIN yes, another operator no
        long infeedId = queryLong("select id from uploaded_file where experiment_id = " + exp + " and file_category = 'INFEED'");
        byte[] downloaded = download("/api/files/" + infeedId + "/content", OPERATOR_1);
        assertThat(downloaded).isNotEmpty();
        assertThat(downloaded[0]).isEqualTo((byte) 0x50);   // starts with "PK" = a real .xlsx
        assertThat(get("/api/files/" + infeedId + "/content", ADMIN).status()).isEqualTo(200);
        assertThat(get("/api/files/" + infeedId + "/content", OPERATOR_2).status()).isEqualTo(403);
    }

    @Test
    void aFileWithMistakesIsRefusedAndNothingIsSaved() throws Exception {
        long exp = 3002;

        Reply wrong = expect(upload("infeed", OPERATOR_1, "infeed.xlsx", infeedFileWithMistakes(), exp), 400);
        assertThat(wrong.body()).contains("\"valid\":false").contains("Invalid TC 'KIP99' at row 2");

        Reply notExcel = expect(upload("infeed", OPERATOR_1, "fake.xlsx",
                "just text".getBytes(StandardCharsets.UTF_8), exp), 400);
        assertThat(notExcel.body()).contains("The file content is not a real Excel file.");

        assertThat(queryLong("select count(*) from uploaded_file where experiment_id = " + exp)).isZero();
        assertThat(queryLong("select count(*) from outbox_event where event_key = '" + exp + "'")).isZero();
    }

    @Test
    void onlyTheOwnerAndAllowedRolesMayUpload() throws Exception {
        // experiment-service says "not yours" -> 403, nothing saved
        expect(upload("infeed", OPERATOR_1, "infeed.xlsx", validInfeedFile(), 2000), 403);
        assertThat(queryLong("select count(*) from uploaded_file where experiment_id = 2000")).isZero();

        // VIEWER role may not upload at all
        expect(upload("infeed", VIEWER, "infeed.xlsx", validInfeedFile(), 3004), 403);
    }

    @Test
    void anExperimentDeletedMessageDeletesAllFilesOfThatExperiment() throws Exception {
        long exp = 3003;
        expect(upload("infeed", OPERATOR_1, "infeed.xlsx", validInfeedFile(), exp), 201);
        expect(upload("zone-resource", OPERATOR_1, "zones.xlsx", validZoneFile(), exp), 201);
        assertThat(queryLong("select count(*) from uploaded_file where experiment_id = " + exp)).isEqualTo(2);

        // Kafka would call the listener like this:
        String message = "{\"eventId\":\"it-1\",\"experimentId\":" + exp
                + ",\"deletedBy\":\"op1\",\"occurredAt\":\"2026-11-11T10:00:00Z\"}";
        experimentDeletedListener.onExperimentDeleted(
                new ConsumerRecord<>("experiment.deleted", 0, 0L, String.valueOf(exp), message));

        assertThat(queryLong("select count(*) from uploaded_file where experiment_id = " + exp)).isZero();
        assertThat(queryLong("select count(*) from file_content where storage_key like 'experiments/" + exp + "/%'")).isZero();

        // The SAME message again (Kafka may deliver twice): no error, nothing changes
        experimentDeletedListener.onExperimentDeleted(
                new ConsumerRecord<>("experiment.deleted", 0, 1L, String.valueOf(exp), message));
        assertThat(queryLong("select count(*) from uploaded_file where experiment_id = " + exp)).isZero();
    }

    // ================================================================ helpers

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

    private HttpRequest.Builder request(String path, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(30));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private Reply get(String path, String token) throws Exception {
        HttpResponse<String> response = client.send(request(path, token).GET().build(), HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), response.body());
    }

    private byte[] download(String path, String token) throws Exception {
        HttpResponse<byte[]> response = client.send(request(path, token).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    /**
     * Sends a REAL multipart upload, like a browser or Postman does:
     * the form fields and the file, separated by a "boundary" text.
     */
    private Reply upload(String kind, String token, String fileName, byte[] content, long experimentId) throws Exception {
        String boundary = "----FlexsimTestBoundary" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"experimentId\"\r\n\r\n"
                + experimentId + "\r\n");
        write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"fileUploadDate\"\r\n\r\n2026-11-11\r\n");
        write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName
                + "\"\r\nContent-Type: " + XLSX_TYPE + "\r\n\r\n");
        body.write(content);
        write(body, "\r\n--" + boundary + "--\r\n");

        HttpRequest httpRequest = request("/api/files/validate/" + kind, token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), response.body());
    }

    private static void write(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    private long queryLong(String sql) throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    // ---- real Excel files, made in memory ----

    private static byte[] xlsx(List<String[]> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < rows.get(r).length; c++) {
                    row.createCell(c).setCellValue(rows.get(r)[c]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static final List<String> TCS = List.of("KIP1-KIP2", "KIP3-KIP4", "KIP5-KIP6", "KIP7-KIP8",
            "KIP9-KIP10", "B9-B10-B11", "B14-B15", "B113-B116");

    private static byte[] validInfeedFile() throws IOException {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Shift_ID", "TC", "No of Resources"});
        for (int shift = 1; shift <= 8; shift++) {
            for (String tc : TCS) {
                rows.add(new String[] {String.valueOf(shift), tc, "2"});
            }
        }
        return xlsx(rows);
    }

    private static byte[] infeedFileWithMistakes() throws IOException {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Shift_ID", "TC", "No of Resources"});
        rows.add(new String[] {"1", "KIP99", "2"});
        return xlsx(rows);
    }

    private static byte[] validZoneFile() throws IOException {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Shift ID", "Zone ID", "Resource", "Chute_ID"});
        for (int shift = 1; shift <= 8; shift++) {
            rows.add(new String[] {String.valueOf(shift), "ZONE-1", "1", "252"});
            rows.add(new String[] {String.valueOf(shift), "ZONE-2", "1", "[101,102,103]"});
            rows.add(new String[] {String.valueOf(shift), "ZONE-3", "1", "152"});
            rows.add(new String[] {String.valueOf(shift), "ZONE-4", "1", "[221,222]"});
        }
        return xlsx(rows);
    }
}