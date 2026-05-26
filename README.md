# NirikshaAI Java SDK

[![CI](https://github.com/san-data-systems/niriksha-sdk-java/actions/workflows/ci.yml/badge.svg)](https://github.com/san-data-systems/niriksha-sdk-java/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.san-data-systems/niriksha-sdk-java.svg)](https://central.sonatype.com/artifact/io.github.san-data-systems/niriksha-sdk-java)
[![Javadoc](https://javadoc.io/badge2/io.github.san-data-systems/niriksha-sdk-java/javadoc.svg)](https://javadoc.io/doc/io.github.san-data-systems/niriksha-sdk-java)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

Official Java SDK for [NirikshaAI](https://niriksha.ai) — the AI-native observability platform
for logs, metrics, traces, and AI insights.

The SDK wraps the [OpenTelemetry Java SDK](https://opentelemetry.io/docs/languages/java/) and
configures it to export traces, metrics, and logs to your NirikshaAI instance via OTLP/gRPC
with a single fluent builder call.

---

## 1. Installation

### Maven

```xml
<dependency>
  <groupId>io.github.san-data-systems</groupId>
  <artifactId>niriksha-sdk-java</artifactId>
  <version>0.1.0</version>
</dependency>
```

> **Logging:** This SDK uses SLF4J for logging. Add your preferred SLF4J binding to your project (e.g., Logback, Log4j2). For quick local testing, add `slf4j-simple`.

> Until the artifact is published to Maven Central, install it locally:
> ```bash
> git clone https://github.com/san-data-systems/niriksha-sdk-java.git
> cd niriksha-sdk-java
> mvn install -DskipTests
> ```

---

## 2. SaaS quick start

```java
import ai.niriksha.sdk.NirikshaAI;
import ai.niriksha.sdk.ShutdownHook;

ShutdownHook hook = NirikshaAI.builder()
    .endpoint("https://app.niriksha.ai")
    .otlpEndpoint("grpc-ingest.niriksha.ai:443")
    .apiKey(System.getenv("NIRIKSHA_API_KEY"))
    .serviceName("my-service")
    .environment("production")
    .build();

// ... your application code ...

hook.close(); // flush and shut down (or use try-with-resources)
```

`build()` registers the configured `TracerProvider`, `MeterProvider`, and `LoggerProvider` as
OpenTelemetry globals. Any instrumentation library on the classpath will automatically export
to NirikshaAI from this point forward.

---

## 3. Private Cloud

### 3a. Default CA (trusted by the JVM)

Your organisation already trusts the server certificate — nothing extra needed:

```java
ShutdownHook hook = NirikshaAI.builder()
    .endpoint("https://niriksha.internal.example.com")
    .apiKey(System.getenv("NIRIKSHA_API_KEY"))
    .serviceName("my-service")
    .build();
```

### 3b. Custom CA certificate

Your NirikshaAI instance uses a private CA that is not in the JVM trust store:

```java
ShutdownHook hook = NirikshaAI.builder()
    .endpoint("https://niriksha.internal.example.com")
    .apiKey(System.getenv("NIRIKSHA_API_KEY"))
    .serviceName("my-service")
    .caCertFile("/etc/ssl/certs/internal-ca.pem")   // PEM-encoded CA cert
    .build();
```

### 3c. Skip TLS verification (dev/test only)

Disables certificate validation entirely. **Do not use in production.**

```java
ShutdownHook hook = NirikshaAI.builder()
    .endpoint("https://niriksha.dev.internal")
    .apiKey(System.getenv("NIRIKSHA_API_KEY"))
    .serviceName("my-service")
    .tlsSkipVerify(true)
    .build();
```

### 3d. Plaintext gRPC (no TLS)

For environments where TLS is terminated by an in-cluster proxy (e.g. Istio, Envoy):

```java
ShutdownHook hook = NirikshaAI.builder()
    .endpoint("http://niriksha.svc.cluster.local")
    .otlpEndpoint("niriksha-ingest.svc.cluster.local:4317")
    .apiKey(System.getenv("NIRIKSHA_API_KEY"))
    .serviceName("my-service")
    .insecure(true)
    .build();
```

---

## 4. Configuration reference

| Builder method | Type | Default | Description |
|----------------|------|---------|-------------|
| `endpoint(String)` | `String` | — | REST base URL of the NirikshaAI instance, e.g. `https://app.niriksha.ai`. Used to derive the gRPC host when `otlpEndpoint` is not set. |
| `otlpEndpoint(String)` | `String` | derived from `endpoint` | gRPC ingest address, e.g. `grpc-ingest.niriksha.ai:443`. Overrides the auto-derived address. |
| `apiKey(String)` | `String` | — **(required)** | Project API key (`nai_...`). Sent as the `x-api-key` gRPC header. |
| `serviceName(String)` | `String` | `java-service` | OpenTelemetry `service.name` resource attribute. |
| `environment(String)` | `String` | `production` | OpenTelemetry `deployment.environment` resource attribute. |
| `otlpPort(int)` | `int` | `4317` | gRPC port used when deriving the OTLP address from `endpoint`. |
| `insecure(boolean)` | `boolean` | `false` | Use plaintext gRPC (no TLS). |
| `tlsSkipVerify(boolean)` | `boolean` | `false` | Skip TLS certificate validation. Dev/test only. |
| `caCertFile(String)` | `String` | `null` | Path to a PEM-encoded CA certificate. Installs a custom trust store. |
| `enableMetrics(boolean)` | `boolean` | `true` | Enable OTLP metric export. |
| `enableLogs(boolean)` | `boolean` | `true` | Enable OTLP log export. |
| `metricsExportInterval(Duration)` | `Duration` | `PT60S` | How often metrics are exported. |

---

## 5. What `build()` sets up

Calling `build()` performs the following in order:

1. Validates that `endpoint` or `otlpEndpoint` is set and `apiKey` is present.
2. Derives the gRPC address (scheme, host, port) from builder fields.
3. Builds an `OtlpGrpcSpanExporter` with the `x-api-key` header and appropriate TLS credentials.
4. Wraps it in a `BatchSpanProcessor` and registers it with `SdkTracerProvider`.
5. If `enableMetrics=true`: builds `OtlpGrpcMetricExporter` + `PeriodicMetricReader` (60 s default).
6. If `enableLogs=true`: builds `OtlpGrpcLogRecordExporter` + `BatchLogRecordProcessor`.
7. Assembles `OpenTelemetrySdk` with W3C TraceContext propagation and calls `buildAndRegisterGlobal()`.
8. Registers a JVM shutdown hook that flushes all providers on process exit.
9. Returns a `ShutdownHook` (implements `Closeable`) for explicit lifecycle control.

---

## 6. Using traces in your code

After `build()`, obtain a tracer from the global instance:

```java
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

Tracer tracer = GlobalOpenTelemetry.getTracer("com.example.my-service", "1.0.0");

Span span = tracer.spanBuilder("orders.process")
    .setAttribute("order.id", orderId)
    .startSpan();

try (Scope ignored = span.makeCurrent()) {
    // your logic
} catch (Exception e) {
    span.recordException(e);
    span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
    throw e;
} finally {
    span.end();
}
```

---

## 7. Examples

| Example | Description |
|---------|-------------|
| [`examples/spring-boot/`](examples/spring-boot/) | Spring Boot 3 application — `@Configuration` bean + `OrderController` with custom spans |
| [`examples/plain-java/`](examples/plain-java/) | No-framework HTTP server using `com.sun.net.httpserver` |

---

## License

Apache 2.0 — see [LICENSE](LICENSE).
