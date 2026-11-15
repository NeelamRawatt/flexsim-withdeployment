package com.example.flexsim_worker.config;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The HTTP clients the worker uses to call other services, each with TIMEOUTS:
 *   connect timeout = max time to open the connection   (2 s)
 *   read timeout    = max time to wait for the answer    (5 s)
 * Same idea as in simulation-service: if a service hangs, we give up instead of waiting for ever.
 */
@Configuration
public class RestClientConfig {

    /** Calls auth-service (only for the worker's own login). */
    @Bean
    public RestClient authRestClient(
            @Value("${app.services.auth.base-url}") String baseUrl,
            @Value("${app.services.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${app.services.read-timeout-ms:5000}") long readTimeoutMs) {
        return build(baseUrl, connectTimeoutMs, readTimeoutMs);
    }

    /** Calls simulation-service (read a run, report its status). */
    @Bean
    public RestClient simulationRestClient(
            @Value("${app.services.simulation.base-url}") String baseUrl,
            @Value("${app.services.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${app.services.read-timeout-ms:5000}") long readTimeoutMs) {
        return build(baseUrl, connectTimeoutMs, readTimeoutMs);
    }

    /**
     * The clock the worker uses to know "what time is it now".
     * Tests give a FAKE clock, so they can say "10 minutes later" without waiting 10 minutes.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    private static RestClient build(String baseUrl, long connectTimeoutMs, long readTimeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
