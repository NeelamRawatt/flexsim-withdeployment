package com.example.auth_service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * INTEGRATION TEST = starts the WHOLE service (Spring + Flyway + Hibernate +
 * security) against a REAL PostgreSQL database and calls it over HTTP, like a
 * real client would.
 *
 * HOW THE REAL DATABASE APPEARS (Testcontainers):
 * The datasource url below starts with "jdbc:tc:postgresql:16". "tc" means
 * Testcontainers. When the test starts, Testcontainers asks Docker to start
 * a fresh "postgres:16" container, and removes it when the tests end.
 * So: DOCKER DESKTOP MUST BE RUNNING when you run this test.
 *
 * Why not an in-memory database like H2? H2 behaves differently from
 * PostgreSQL, so a test could pass but production could fail.
 *
 * What this test proves:
 *  1. Flyway ran V1 and Hibernate "validate" accepted the tables
 *     (if the SQL and the entities did not match, the service would not start).
 *  2. Login, refresh, logout, /me and user management work end to end.
 *  3. Deleting a user who has refresh tokens works (the FK cascade fix).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:tc:postgresql:16:///auth_test",
                "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
                "spring.datasource.username=test",
                "spring.datasource.password=test",
                "POSTGRES_PASSWORD=unused",
                "app.security.jwt.secret=integration-test-secret-integration-test-1234",
                "app.security.bootstrap.developer.enabled=true",
                "app.security.bootstrap.developer.username=developer",
                "app.security.bootstrap.developer.password=Dev@12345"
        })
class AuthServiceIntegrationTest {

    // The test does not call main(), so we set UTC here. It runs when the class is
    // loaded, BEFORE Spring connects to the database. Reason: see AuthServiceApplication.main
    // (the PostgreSQL image refuses the time zone name "Asia/Calcutta").
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    // RANDOM_PORT = the service starts on a free port; Spring tells us which.
    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient client = HttpClient.newHttpClient();

    // ------------------------------------------------------------- helpers

    private HttpResponse<String> send(String method, String path, String jsonBody, String cookieHeader) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (cookieHeader != null) {
            builder.header("Cookie", cookieHeader);
        }
        HttpRequest.BodyPublisher body = jsonBody == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(jsonBody);
        return client.send(builder.method(method, body).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> login(String username, String password) throws Exception {
        return send("POST", "/api/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}", null);
    }

    /** Finds a cookie like "flexsim_access_token=abc..." in the Set-Cookie headers. */
    private String cookie(HttpResponse<String> response, String name) {
        List<String> all = response.headers().allValues("Set-Cookie");
        return all.stream()
                .filter(value -> value.startsWith(name + "="))
                .map(value -> value.substring(0, value.indexOf(';') < 0 ? value.length() : value.indexOf(';')))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cookie not found: " + name + " in " + all));
    }

    private String developerCookies() throws Exception {
        HttpResponse<String> response = login("developer", "Dev@12345");
        assertThat(response.statusCode()).isEqualTo(200);
        return cookie(response, "flexsim_access_token");
    }

    // --------------------------------------------------------------- tests

    @Test
    @DisplayName("Flyway ran V1 and created the three tables")
    void flywayCreatedTables() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class);
        Integer tables = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' "
                        + "and table_name in ('app_user','auth_refresh_token','security_audit_log')", Integer.class);

        assertThat(applied).isGreaterThanOrEqualTo(1);
        assertThat(tables).isEqualTo(3);
    }

    @Test
    @DisplayName("login works and sets two HttpOnly cookies")
    void loginSetsCookies() throws Exception {
        HttpResponse<String> response = login("developer", "Dev@12345");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Login successful").contains("developer").contains("DEVELOPER");
        List<String> setCookies = response.headers().allValues("Set-Cookie");
        assertThat(setCookies).anyMatch(c -> c.startsWith("flexsim_access_token=") && c.contains("HttpOnly"));
        assertThat(setCookies).anyMatch(c -> c.startsWith("flexsim_refresh_token=") && c.contains("HttpOnly"));
    }

    @Test
    @DisplayName("wrong password gives 401")
    void wrongPasswordGives401() throws Exception {
        HttpResponse<String> response = login("developer", "totally-wrong");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("Invalid Password");
    }

    @Test
    @DisplayName("unknown user gives 401")
    void unknownUserGives401() throws Exception {
        assertThat(login("nobody-" + UUID.randomUUID(), "whatever").statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("/api/auth/me needs a login: 401 without cookie, 200 with cookie")
    void meNeedsLogin() throws Exception {
        assertThat(send("GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);

        HttpResponse<String> me = send("GET", "/api/auth/me", null, developerCookies());
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains("developer");
    }

    @Test
    @DisplayName("health page is open (Kubernetes needs it)")
    void healthIsOpen() throws Exception {
        HttpResponse<String> response = send("GET", "/actuator/health", null, null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP");
    }

    @Test
    @DisplayName("refresh gives a new session; after logout the old refresh cookie is dead")
    void refreshAndLogout() throws Exception {
        HttpResponse<String> login = login("developer", "Dev@12345");
        String refreshCookie = cookie(login, "flexsim_refresh_token");

        HttpResponse<String> refreshed = send("POST", "/api/auth/refresh", null, refreshCookie);
        assertThat(refreshed.statusCode()).isEqualTo(200);
        assertThat(refreshed.body()).contains("Session refreshed");

        // Every refresh gives a NEW refresh token and kills the old one (rotation).
        String newRefreshCookie = cookie(refreshed, "flexsim_refresh_token");
        assertThat(send("POST", "/api/auth/refresh", null, refreshCookie).statusCode()).isEqualTo(401);

        // Logout revokes the current refresh token.
        assertThat(send("POST", "/api/auth/logout", null, newRefreshCookie).statusCode()).isEqualTo(204);
        assertThat(send("POST", "/api/auth/refresh", null, newRefreshCookie).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("user management needs a login, and a DEVELOPER can create and delete a user who has logged in")
    void createLoginAndDeleteUser() throws Exception {
        // 1. Not logged in -> 401.
        assertThat(send("GET", "/api/users", null, null).statusCode()).isEqualTo(401);

        // 2. The developer creates an OPERATOR user.
        String devCookie = developerCookies();
        String username = "operator-" + UUID.randomUUID().toString().substring(0, 8);
        HttpResponse<String> created = send("POST", "/api/users",
                "{\"username\":\"" + username + "\",\"password\":\"Op@12345\",\"role\":\"OPERATOR\"}", devCookie);
        assertThat(created.statusCode()).isEqualTo(200);
        assertThat(created.body()).contains(username).contains("OPERATOR");

        Matcher idMatcher = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(created.body());
        assertThat(idMatcher.find()).isTrue();
        String userId = idMatcher.group(1);

        // 3. The new user logs in (this creates a refresh token row for them).
        HttpResponse<String> operatorLogin = login(username, "Op@12345");
        assertThat(operatorLogin.statusCode()).isEqualTo(200);

        // 4. An OPERATOR may NOT manage users: 403 Forbidden.
        String operatorCookie = cookie(operatorLogin, "flexsim_access_token");
        assertThat(send("GET", "/api/users", null, operatorCookie).statusCode()).isEqualTo(403);

        // 5. The developer deletes the operator. This works only because the
        //    refresh token table has ON DELETE CASCADE (see V1 migration).
        HttpResponse<String> deleted = send("DELETE", "/api/users/" + userId, null, devCookie);
        assertThat(deleted.statusCode()).isEqualTo(200);
        assertThat(deleted.body()).contains("User deleted successfully");

        // 6. The deleted user can no longer log in.
        assertThat(login(username, "Op@12345").statusCode()).isEqualTo(401);
    }
}
