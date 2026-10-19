package com.example.experiment_service.security;

import java.util.Arrays;
import java.util.List;
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

/**
 * The rules of experiment-service: who may call which URL.
 *
 * "Secure by default": every URL needs a login, except the few we open on purpose below.
 * This is the opposite of the old monolith, which ended with "everything else is open".
 *
 *  - Not logged in          -> 401 Unauthorized
 *  - Logged in, wrong role  -> 403 Forbidden (done by @PreAuthorize on controller methods later)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // lets us write @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')") on methods
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // Websites (origins) that may call this service from a browser. Comma separated.
    @Value("${app.security.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF is off: we use no server session. The token cookie is SameSite=Lax (set by auth-service).
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                // STATELESS = the server remembers nothing between requests. Every request shows its token.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Not logged in -> plain 401.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        // Browsers send OPTIONS first ("am I allowed?"). It carries no cookie, so it must be open.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Kubernetes asks these two pages "are you alive?". They show no private data.
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // /error is where Spring sends error answers (like 404). If it needs a login,
                        // a real 404 would wrongly show up as 401.
                        .requestMatchers("/error").permitAll()
                        // Everything else needs a valid token.
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS = which website addresses may call this service from a browser.
     * Example: the React app on http://localhost:3000.
     * allowCredentials(true) lets the browser send the cookie. With credentials, a "*" origin is
     * NOT allowed, so we list the exact origins.
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

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}