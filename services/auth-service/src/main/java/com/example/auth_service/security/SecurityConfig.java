package com.example.auth_service.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * The security rules of auth-service: who may call which URL.
 *
 * "SECURE BY DEFAULT": the last rule is anyRequest().authenticated(), which means
 * "every URL that is not listed below needs a login". The old monolith ended with
 * "permitAll" (everything else open to everybody). We do the opposite.
 *
 * Status codes you will see:
 *   401 Unauthorized = you are not logged in.
 *   403 Forbidden    = you are logged in, but your role is not allowed.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // lets us write @PreAuthorize("hasAnyRole(...)") on controller methods
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.security.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF protection is OFF because we do not use browser sessions.
                // Login uses tokens in cookies with SameSite=Lax.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                // STATELESS = the server remembers nothing between requests.
                // Every request proves who it is with its token.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Not logged in -> answer 401.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        // Browsers send OPTIONS first ("am I allowed?"). Always allow it.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Login and refresh: nobody is logged in yet, so they must be open.
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh").permitAll()
                        // Logout must work even when the short token has expired,
                        // otherwise the cookies would never be cleared.
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                        // Kubernetes asks these pages "are you alive?".
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // "/error" is the page Spring uses INTERNALLY to send an error answer
                        // (403, 404, 409...). It must be open. If it needs a login, every error
                        // turns into a wrong 401. The real status code is kept.
                        .requestMatchers("/error").permitAll()
                        // Everything else (/api/auth/me, /api/users/...) needs a login.
                        // Role checks are written on the controller methods (batch C).
                        .anyRequest().authenticated()
                )
                // Put our guard BEFORE Spring's own username/password filter.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS = which website addresses may call this service from a browser.
     * Example: your React app on http://localhost:3000.
     * The list comes from APP_ALLOWED_ORIGINS (in production: your real website address).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(List.of("Set-Cookie"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}