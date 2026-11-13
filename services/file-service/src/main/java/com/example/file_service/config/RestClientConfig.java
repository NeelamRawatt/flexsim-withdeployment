package com.example.file_service.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds the HTTP client that calls experiment-service.
 *
 * RestClient = Spring's tool to call another service's URL from Java.
 * TIMEOUTS (production must-have): if experiment-service hangs, we give up after a few seconds
 * instead of keeping the user (and one of our threads) waiting forever.
 *   connect timeout = max time to open the connection   (2 s)
 *   read timeout    = max time to wait for the answer    (5 s)
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient experimentRestClient(
            @Value("${app.services.experiment.base-url}") String baseUrl,
            @Value("${app.services.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${app.services.read-timeout-ms:5000}") long readTimeoutMs) {

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}