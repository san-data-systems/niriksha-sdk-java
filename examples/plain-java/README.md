# NirikshaAI SDK — Plain Java Example

Zero-framework HTTP server using only the JDK's built-in `com.sun.net.httpserver.HttpServer`.
No Spring, no Quarkus, no Micronaut — just the NirikshaAI SDK and standard Java 17.

## Prerequisites

- Java 17+
- Maven 3.8+
- A NirikshaAI API key (`nai_...`)

## Setup

Install the SDK into your local Maven repository:

```bash
cd ../../
mvn install -DskipTests
```

## Build and run

```bash
cd examples/plain-java
export NIRIKSHA_API_KEY=nai_your_key_here
mvn package -DskipTests
java -jar target/niriksha-plain-java-demo-0.1.0-jar-with-dependencies.jar
```

## Environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `NIRIKSHA_API_KEY` | (required) | Your project API key |
| `NIRIKSHA_ENDPOINT` | `https://app.niriksha.ai` | REST base URL |
| `NIRIKSHA_OTLP_ENDPOINT` | `grpc-ingest.niriksha.ai:443` | gRPC ingest address |
| `NIRIKSHA_SERVICE_NAME` | `plain-java-demo` | OTel `service.name` |
| `NIRIKSHA_ENVIRONMENT` | `production` | OTel `deployment.environment` |

## Endpoints

```
GET http://localhost:8080/       — health check
GET http://localhost:8080/ping   — liveness probe with a span event
```

## How it works

`Main.java` calls `NirikshaAI.builder()...build()` **before** starting the HTTP server so
that the global `TracerProvider` is registered first. Each request handler then calls
`GlobalOpenTelemetry.getTracer(...)` to obtain a tracer and wraps its logic in a named span.

The SDK registers a JVM shutdown hook that flushes pending telemetry automatically when
you press Ctrl+C.
