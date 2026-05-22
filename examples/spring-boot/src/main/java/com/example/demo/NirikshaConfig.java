package com.example.demo;

import ai.niriksha.sdk.NirikshaAI;
import ai.niriksha.sdk.ShutdownHook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring {@link Configuration} that initialises the NirikshaAI SDK on startup.
 *
 * <p>The returned {@link ShutdownHook} bean implements {@link java.io.Closeable}; Spring
 * will call {@code close()} automatically during context shutdown, flushing all pending
 * telemetry before the JVM exits.
 *
 * <p>Configure the following properties in {@code application.properties} or via
 * environment variables:
 * <ul>
 *   <li>{@code niriksha.api-key} — your project API key ({@code nai_...})</li>
 *   <li>{@code niriksha.endpoint} — REST base URL (default: {@code https://app.niriksha.ai})</li>
 *   <li>{@code niriksha.otlp-endpoint} — gRPC ingest address (default: {@code grpc-ingest.niriksha.ai:443})</li>
 *   <li>{@code niriksha.service-name} — service name (default: {@code spring-boot-demo})</li>
 *   <li>{@code niriksha.environment} — deployment environment (default: {@code production})</li>
 * </ul>
 */
@Configuration
public class NirikshaConfig {

    @Value("${niriksha.api-key:${NIRIKSHA_API_KEY:}}")
    private String apiKey;

    @Value("${niriksha.endpoint:https://app.niriksha.ai}")
    private String endpoint;

    @Value("${niriksha.otlp-endpoint:grpc-ingest.niriksha.ai:443}")
    private String otlpEndpoint;

    @Value("${niriksha.service-name:spring-boot-demo}")
    private String serviceName;

    @Value("${niriksha.environment:production}")
    private String environment;

    /**
     * Initialises the NirikshaAI SDK and returns a {@link ShutdownHook}.
     *
     * <p>Spring manages the bean lifecycle — {@link ShutdownHook#close()} is invoked
     * automatically on application context shutdown.
     */
    @Bean(destroyMethod = "close")
    public ShutdownHook nirikshaAI() {
        return NirikshaAI.builder()
                .endpoint(endpoint)
                .otlpEndpoint(otlpEndpoint)
                .apiKey(apiKey)
                .serviceName(serviceName)
                .environment(environment)
                .build();
    }
}
