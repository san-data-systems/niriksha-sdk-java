package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 3 demo application showing NirikshaAI SDK integration.
 *
 * <p>Set the environment variable {@code NIRIKSHA_API_KEY} to your project API key
 * before starting:
 * <pre>
 *   export NIRIKSHA_API_KEY=nai_your_key_here
 *   mvn spring-boot:run
 * </pre>
 *
 * <p>Then exercise the endpoints:
 * <pre>
 *   curl http://localhost:8080/orders/42
 *   curl http://localhost:8080/orders/42/items
 * </pre>
 *
 * <p>Traces, metrics, and logs will appear in your NirikshaAI dashboard within seconds.
 */
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
