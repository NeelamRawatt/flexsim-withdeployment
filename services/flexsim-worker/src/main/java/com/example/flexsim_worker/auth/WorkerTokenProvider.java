package com.example.flexsim_worker.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Gives the worker a VALID login token, whenever it needs one.
 *
 * How it works (like a person with a 10-minute visitor pass):
 *   1. First call: log in to auth-service with the WORKER account (POST /api/auth/login).
 *   2. auth-service answers with the token in a cookie (flexsim_access_token) that lives 10 minutes.
 *   3. We KEEP the token in memory and give the same one again and again.
 *   4. 2 minutes BEFORE it runs out, we log in again and get a fresh one.
 *      (So a call never leaves with a token that dies on the way.)
 *   5. If a service still says 401 ("token not valid"), the caller calls invalidate(),
 *      and the next getToken() logs in again.
 *
 * Safety:
 *   - The password and the token are NEVER written to the log.
 *   - If the account is NOT a WORKER account (for example someone put an ADMIN login here),
 *     we refuse to use it: the worker must never run with more rights than it needs.
 *
 * "synchronized" = only ONE thread at a time may enter. The worker runs up to 4 jobs at the
 * same time (W3); without this, 4 threads could log in 4 times at the same moment.
 */
@Component
public class WorkerTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(WorkerTokenProvider.class);

    static final String ACCESS_TOKEN_COOKIE = "flexsim_access_token";
    static final String REQUIRED_ROLE = "WORKER";
    /** Log in again this long BEFORE the token runs out. */
    static final Duration REFRESH_MARGIN = Duration.ofMinutes(2);
    /** Used only if auth-service does not say how long the token lives. */
    static final Duration DEFAULT_LIFETIME = Duration.ofMinutes(5);

    private final RestClient authRestClient;
    private final String username;
    private final String password;
    private final Clock clock;

    private String token;        // the current token (null = we have none)
    private Instant refreshAt;   // when we must get a new one

    public WorkerTokenProvider(@Qualifier("authRestClient") RestClient authRestClient,
                               @Value("${app.worker.username}") String username,
                               @Value("${app.worker.password}") String password,
                               Clock clock) {
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("app.worker.username (APP_WORKER_USERNAME) is empty");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("app.worker.password (APP_WORKER_PASSWORD) is empty");
        }
        this.authRestClient = authRestClient;
        this.username = username;
        this.password = password;
        this.clock = clock;
    }

    /** A valid token. Logs in first if we have none, or if ours is about to run out. */
    public synchronized String getToken() {
        if (token == null || !clock.instant().isBefore(refreshAt)) {
            login();
        }
        return token;
    }

    /** Forget the token (a service said 401). The next getToken() logs in again. */
    public synchronized void invalidate() {
        token = null;
        refreshAt = null;
    }

    private void login() {
        ResponseEntity<LoginResponse> response;
        try {
            response = authRestClient.post()
                    .uri("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LoginRequest(username, password))
                    .retrieve()
                    .toEntity(LoginResponse.class);
        } catch (HttpClientErrorException e) {
            // 400 / 401 = auth-service answered "no": wrong username or password. Retrying won't help.
            throw new WorkerLoginException("auth-service refused the worker login (HTTP "
                    + e.getStatusCode().value() + "). Check APP_WORKER_USERNAME and APP_WORKER_PASSWORD.", true);
        } catch (RestClientException e) {
            // Could not reach auth-service (down, timeout, 5xx). Retrying later can help.
            throw new WorkerLoginException("auth-service could not be reached: " + e.getMessage(), e);
        }

        LoginResponse body = response.getBody();
        String role = body == null ? null : body.role();
        if (!REQUIRED_ROLE.equals(role)) {
            throw new WorkerLoginException("The account '" + username + "' has role " + role
                    + ", not WORKER. The worker refuses to use it.", true);
        }

        TokenCookie cookie = findAccessTokenCookie(response.getHeaders());
        if (cookie == null) {
            throw new WorkerLoginException("auth-service did not send the " + ACCESS_TOKEN_COOKIE + " cookie", true);
        }

        Duration lifetime = cookie.maxAgeSeconds() > 0 ? Duration.ofSeconds(cookie.maxAgeSeconds()) : DEFAULT_LIFETIME;
        // Short lifetimes: refresh at half time instead (so we never refresh on EVERY call).
        Duration margin = REFRESH_MARGIN.compareTo(lifetime.dividedBy(2)) < 0 ? REFRESH_MARGIN : lifetime.dividedBy(2);

        this.token = cookie.value();
        this.refreshAt = clock.instant().plus(lifetime).minus(margin);
        log.info("Worker logged in as {} (role {}). Token valid for {} s.", username, role, lifetime.toSeconds());
    }

    /**
     * Finds our cookie in the "Set-Cookie" answer lines. One line looks like:
     *   flexsim_access_token=eyJhbGci...; Path=/; Max-Age=600; Expires=Sun, 15 Nov 2026 05:10:00 GMT; HttpOnly; SameSite=Lax
     * We need the value (the token) and Max-Age (how many seconds it lives).
     */
    static TokenCookie findAccessTokenCookie(HttpHeaders headers) {
        List<String> lines = headers.get(HttpHeaders.SET_COOKIE);
        if (lines == null) {
            return null;
        }
        for (String line : lines) {
            String[] parts = line.split(";");
            String first = parts[0].trim();
            int eq = first.indexOf('=');
            if (eq <= 0 || !ACCESS_TOKEN_COOKIE.equals(first.substring(0, eq).trim())) {
                continue;
            }
            String value = first.substring(eq + 1).trim();
            if (value.isEmpty()) {
                continue;
            }
            long maxAge = -1;
            for (int i = 1; i < parts.length; i++) {
                String attribute = parts[i].trim();
                if (attribute.regionMatches(true, 0, "Max-Age=", 0, 8)) {
                    try {
                        maxAge = Long.parseLong(attribute.substring(8).trim());
                    } catch (NumberFormatException ignored) {
                        // keep -1: we use DEFAULT_LIFETIME
                    }
                }
            }
            return new TokenCookie(value, maxAge);
        }
        return null;
    }

    /** The JSON we send: {"username": "...", "password": "..."} */
    record LoginRequest(String username, String password) {
    }

    /** The JSON auth-service answers: {"message": "...", "username": "...", "role": "WORKER"} */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record LoginResponse(String message, String username, String role) {
    }

    record TokenCookie(String value, long maxAgeSeconds) {
        @Override
        public String toString() {
            return "TokenCookie[hidden]";   // never print the token by mistake
        }
    }
}
