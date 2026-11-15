package com.example.simulation_service.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The HTTP clients this service uses to call other services, each with TIMEOUTS:
 *   connect timeout = max time to open the connection   (2 s)
 *   read timeout    = max time to wait for the answer    (5 s)
 * If a service hangs, we give up and answer 503, instead of keeping the user waiting for ever.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient experimentRestClient(
            @Value("${app.services.experiment.base-url}") String baseUrl,
            @Value("${app.services.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${app.services.read-timeout-ms:5000}") long readTimeoutMs) {
        return build(baseUrl, connectTimeoutMs, readTimeoutMs);
    }

    @Bean
    public RestClient fileRestClient(
            @Value("${app.services.file.base-url}") String baseUrl,
            @Value("${app.services.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${app.services.read-timeout-ms:5000}") long readTimeoutMs) {
        return build(baseUrl, connectTimeoutMs, readTimeoutMs);
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