package com.example.experiment_service.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Tests the "security guard" with fake requests (no server, no database). */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-123456";

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(new JwtTokenVerifier(SECRET));

    @AfterEach
    void clean() {
        // The security context is kept per thread. Clear it so one test cannot affect the next.
        SecurityContextHolder.clearContext();
    }

    private static String goodToken() {
        return Jwts.builder()
                .subject("neelam")
                .claim("role", "ADMIN")
                .claim("uid", 7L)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private MockFilterChain run(MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }

    @Test
    void bearerHeaderLogsTheUserIn() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + goodToken());

        MockFilterChain chain = run(request);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(((AuthenticatedUser) auth.getPrincipal()).username()).isEqualTo("neelam");
        List<String> roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        assertThat(roles).containsExactly("ROLE_ADMIN");
        assertThat(chain.getRequest()).isNotNull();   // the request was passed on
    }

    @Test
    void cookieLogsTheUserIn() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE, goodToken()));

        run(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void badTokenLogsNobodyInButTheRequestContinues() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer not-a-real-token");

        MockFilterChain chain = run(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();   // the guard never blocks by itself
    }

    @Test
    void noTokenLogsNobodyIn() throws Exception {
        run(new MockHttpServletRequest());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void otherAuthorizationTypesAreIgnored() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        run(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}