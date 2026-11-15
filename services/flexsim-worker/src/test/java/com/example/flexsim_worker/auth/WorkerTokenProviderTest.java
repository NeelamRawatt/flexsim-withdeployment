package com.example.flexsim_worker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Tests for the worker login, WITHOUT a real auth-service:
 * MockRestServiceServer plays auth-service and answers what we tell it to.
 */
class WorkerTokenProviderTest {

    private static final String LOGIN_URL = "http://auth.test/api/auth/login";
    private static final String PASSWORD = "test-only-password-not-a-real-secret-0123456789";

    private MockRestServiceServer authServer;
    private MutableClock clock;
    private WorkerTokenProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://auth.test");
        authServer = MockRestServiceServer.bindTo(builder).build();
        clock = new MutableClock(Instant.parse("2026-11-15T05:00:00Z"));
        provider = new WorkerTokenProvider(builder.build(), "flexsim-worker", PASSWORD, clock);
    }

    /** What auth-service really answers: JSON body + two Set-Cookie lines. */
    private void expectLogin(String token, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, "flexsim_access_token=" + token
                + "; Path=/; Max-Age=600; Expires=Sun, 15 Nov 2026 05:10:00 GMT; HttpOnly; SameSite=Lax");
        headers.add(HttpHeaders.SET_COOKIE, "flexsim_refresh_token=refresh-xyz"
                + "; Path=/; Max-Age=3600; HttpOnly; SameSite=Lax");
        authServer.expect(once(), requestTo(LOGIN_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"message\":\"ok\",\"username\":\"flexsim-worker\",\"role\":\"" + role + "\"}",
                        MediaType.APPLICATION_JSON).headers(headers));
    }

    @Test
    void firstCallLogsInWithTheWorkerAccountAndReturnsTheToken() {
        authServer.expect(once(), requestTo(LOGIN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"username\":\"flexsim-worker\",\"password\":\"" + PASSWORD + "\"}"))
                .andRespond(withSuccess("{\"message\":\"ok\",\"username\":\"flexsim-worker\",\"role\":\"WORKER\"}",
                        MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.SET_COOKIE, "flexsim_access_token=token-1; Path=/; Max-Age=600; HttpOnly"));

        assertThat(provider.getToken()).isEqualTo("token-1");
        authServer.verify();
    }

    @Test
    void sameTokenIsReusedWhileItIsStillFresh() {
        expectLogin("token-1", "WORKER");   // "once": a second login would fail the test

        assertThat(provider.getToken()).isEqualTo("token-1");
        clock.advance(Duration.ofMinutes(7));
        assertThat(provider.getToken()).isEqualTo("token-1");
        authServer.verify();
    }

    @Test
    void newLoginTwoMinutesBeforeTheTokenRunsOut() {
        expectLogin("token-1", "WORKER");
        expectLogin("token-2", "WORKER");

        assertThat(provider.getToken()).isEqualTo("token-1");
        clock.advance(Duration.ofMinutes(8));   // 10 min lifetime - 2 min margin
        assertThat(provider.getToken()).isEqualTo("token-2");
        authServer.verify();
    }

    @Test
    void invalidateForcesANewLogin() {
        expectLogin("token-1", "WORKER");
        expectLogin("token-2", "WORKER");

        assertThat(provider.getToken()).isEqualTo("token-1");
        provider.invalidate();
        assertThat(provider.getToken()).isEqualTo("token-2");
        authServer.verify();
    }

    @Test
    void wrongPasswordIsAConfigProblem() {
        authServer.expect(once(), requestTo(LOGIN_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"field\":\"password\",\"message\":\"Invalid Password\"}"));

        assertThatThrownBy(() -> provider.getToken())
                .isInstanceOf(WorkerLoginException.class)
                .hasMessageContaining("HTTP 401")
                .hasMessageNotContaining(PASSWORD)
                .matches(e -> ((WorkerLoginException) e).isConfigProblem());
    }

    @Test
    void accountThatIsNotWorkerIsRefused() {
        expectLogin("admin-token", "ADMIN");

        assertThatThrownBy(() -> provider.getToken())
                .isInstanceOf(WorkerLoginException.class)
                .hasMessageContaining("not WORKER")
                .matches(e -> ((WorkerLoginException) e).isConfigProblem());
    }

    @Test
    void authServiceDownIsNotAConfigProblem() {
        authServer.expect(once(), requestTo(LOGIN_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> provider.getToken())
                .isInstanceOf(WorkerLoginException.class)
                .matches(e -> !((WorkerLoginException) e).isConfigProblem());
    }

    @Test
    void missingTokenCookieIsRefused() {
        authServer.expect(once(), requestTo(LOGIN_URL))
                .andRespond(withSuccess("{\"message\":\"ok\",\"username\":\"flexsim-worker\",\"role\":\"WORKER\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.getToken())
                .isInstanceOf(WorkerLoginException.class)
                .hasMessageContaining("flexsim_access_token");
    }

    @Test
    void cookieReaderFindsTheTokenAndMaxAge() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, "flexsim_refresh_token=r1; Path=/; Max-Age=3600");
        headers.add(HttpHeaders.SET_COOKIE,
                "flexsim_access_token=abc.def.ghi; Path=/; Max-Age=600; Expires=Sun, 15 Nov 2026 05:10:00 GMT; HttpOnly");

        WorkerTokenProvider.TokenCookie cookie = WorkerTokenProvider.findAccessTokenCookie(headers);

        assertThat(cookie).isNotNull();
        assertThat(cookie.value()).isEqualTo("abc.def.ghi");
        assertThat(cookie.maxAgeSeconds()).isEqualTo(600);
        assertThat(cookie.toString()).doesNotContain("abc.def.ghi");
    }

    @Test
    void emptyPasswordStopsTheWorkerAtStart() {
        RestClient client = RestClient.builder().baseUrl("http://auth.test").build();

        assertThatThrownBy(() -> new WorkerTokenProvider(client, "flexsim-worker", " ", clock))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_WORKER_PASSWORD");
    }
}
