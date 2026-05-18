# NirikshaAI SDK — Spring Boot Example

This example shows how to integrate the NirikshaAI Java SDK into a **Spring Boot 3** application.

## Prerequisites

- Java 17+
- Maven 3.8+
- A NirikshaAI account and project API key (`nai_...`)

## Setup

First, install the SDK into your local Maven repository:

```bash
cd ../../
mvn install -DskipTests
```

## Running

```bash
export NIRIKSHA_API_KEY=nai_your_key_here
mvn spring-boot:run
```

## Endpoints

```
GET http://localhost:8080/orders/{id}
GET http://localhost:8080/orders/{id}/items
```

Both endpoints create custom OpenTelemetry spans with structured attributes that appear
in NirikshaAI's trace explorer and AI query assistant.

## How it works

| File | Role |
|------|------|
| `NirikshaConfig.java` | `@Configuration` that calls `NirikshaAI.builder()...build()` and exposes the `ShutdownHook` as a managed bean |
| `OrderController.java` | REST controller that uses `GlobalOpenTelemetry.getTracer(...)` to create child spans |
| `application.properties` | All NirikshaAI settings in one place — override with env vars |

Spring manages the `ShutdownHook` bean lifecycle via `destroyMethod = "close"`, so telemetry
is flushed automatically before the JVM exits.
