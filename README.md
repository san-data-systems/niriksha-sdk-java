# NirikshaAI Java SDK

[![CI](https://github.com/san-data-systems/niriksha-sdk-java/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/san-data-systems/niriksha-sdk-java/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.san-data-systems/niriksha-sdk-java.svg)](https://central.sonatype.com/artifact/io.github.san-data-systems/niriksha-sdk-java)
[![Java 17+](https://img.shields.io/badge/java-17%2B-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk17-archive.html)
[![Javadoc](https://javadoc.io/badge2/io.github.san-data-systems/niriksha-sdk-java/javadoc.svg)](https://javadoc.io/doc/io.github.san-data-systems/niriksha-sdk-java)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

Official Java SDK for [NirikshaAI](https://niriksha.ai) — the AI-native observability platform
for logs, metrics, traces, and AI insights.

The SDK wraps the [OpenTelemetry Java SDK](https://opentelemetry.io/docs/languages/java/) and
configures it to export traces, metrics, and logs to your NirikshaAI instance via OTLP/gRPC
with a single fluent builder call.

**Requires Java 17+** and **Maven 3.9+** (or Gradle 6.0+).

---

## 1. Installation

### Maven

```xml
<dependency>
  <groupId>io.github.san-data-systems</groupId>
  <artifactId>niriksha-sdk-java</artifactId>
  <version>0.0.1</version>
</dependency>
```

### Gradle

```groovy
implementation 'io.github.san-data-systems:niriksha-sdk-java:0.0.1'
```

> **Logging:** This SDK uses SLF4J for logging. Add your preferred SLF4J binding to your project (e.g., Logback, Log4j2). For quick local testing, add `slf4j-simple`:
> ```xml
> <dependency>
>   <groupId>org.slf4j</groupId>
>   <artifactId>slf4j-simple</artifactId>
>   <version>2.0.13</version>
> </dependency>
> ```

> **Spring Boot:** The SDK includes optional Spring Boot autoconfiguration. Add to your `pom.xml` as optional (already declared):
> ```xml
> <dependency>
>   <groupId>org.springframework.boot</groupId>
>   <artifactId>spring-boot-starter-web</artifactId>
> </dependency>
> ```
> The `NirikshaAutoConfiguration` class is auto-discovered if Spring Boot is present.

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
| `guardEndpoint(String)` | `String` | derived | Base URL of the guard endpoint — the gateway's HTTP listener. Derived from `otlpEndpoint`, or `endpoint` when that is unset. See [Inline guard](#8-inline-guard). |
| `guardFailMode(GuardFailMode)` | `GuardFailMode` | `OPEN` | What the guard does when the server is unreachable: `OPEN`, `CLOSED` or `SECRETS_CLOSED`. |
| `guardMode(String)` | `String` | `null` | Default mode for every guard call: `"monitor"` or `"block"`. |

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

## 7. Evals and prompts

Both helpers talk to the project-scoped SDK routes under `/api/v1/sdk/`, which are
the ones that accept a `nai_` API key.

### Submitting evals

```java
NirikshaAI.submitEval(EvalInput.builder()
    .traceId(Span.current().getSpanContext().getTraceId())
    .metricName("faithfulness")
    .score(0.92)
    .label("pass")
    .evalType("rule_based")
    .build());
```

`submitEvalsBatch(List<EvalInput>)` sends many in one request. A non-2xx response
throws `NirikshaAIException` — an eval that failed to record must not look like one
that succeeded.

### Rendering prompts

```java
PromptResponse p = NirikshaAI.getPrompt("product-description",
        GetPromptOptions.builder()
            .version(3)                        // omit to use the deployed version
            .variable("product_name", "Widget Pro")
            .build());

String prompt = p.getText();                   // variables already substituted
```

Resolution order on the server: an explicit `version` wins; otherwise the most
recently deployed version; otherwise the highest version, so a template that was
authored but never deployed is still usable.

Results are cached in-process for five minutes, keyed by name, version **and**
variables — two different variable sets are two different renders.

`listPrompts()` returns the available templates. The listing carries `id`, `name`,
`description` and `created_at` only, so `getText()` is **empty** on a listed entry;
call `getPrompt` to render one.

If a render returns no content, `getPrompt` throws `NirikshaAIException` naming the
prompt rather than handing back an empty string. An empty prompt sent to a model is
the worst available outcome: nothing errors, and the answer is nonsense.

---

## 8. Inline guard

Everything else in this SDK records what happened. The guard is enforcement: it
checks text **before** it reaches the model, so a prompt injection can be refused
and a leaked credential stripped rather than merely reported afterwards.

```java
import ai.niriksha.sdk.GuardBlockedException;
import ai.niriksha.sdk.GuardVerdict;
import ai.niriksha.sdk.NirikshaAI;

GuardVerdict verdict;
try {
    verdict = NirikshaAI.guardCheck(userPrompt);
} catch (GuardBlockedException e) {
    return "That request was refused: " + e.getVerdict().getFindings();
}

// On a redact verdict this returns the rewritten text; otherwise the original.
String response = llm.complete(verdict.safeText(userPrompt));
```

### Verdicts

| Action | What it means | What you should do |
|---|---|---|
| `ALLOW` | Nothing found | Proceed |
| `TAG` | Something found, not reliable enough to act on | **Proceed.** Record it |
| `REDACT` | Sensitive content found and removed | Proceed **with `verdict.safeText(text)`** |
| `BLOCK` | High-confidence attack | Do not send |

**Only a block throws.** A redact verdict returns normally, because a customer who
asked for PII stripping wants their data protected, not their application broken.
`GuardBlockedException` carries the verdict, so the reason, risk score and findings
are available without a second guard call.

The verdict also carries `getRiskScore()`, `getRiskSeverity()`, `getFindings()`,
`getReasons()`, `getPolicySource()` and `isPolicyEnforced()` — the last two tell you
whether your org's AIDR policy or the product default produced it. Only the former
is binding, and `guardMode("monitor")` cannot lift a block your org's policy
mandates.

### Tool calls

The check that can actually prevent an action, rather than describe it after the
fact:

```java
try {
    NirikshaAI.guardCheckTool("bash", "{\"cmd\":\"" + command + "\"}");
} catch (GuardBlockedException e) {
    return "That tool call was refused.";
}
run(command);
```

Covers file destruction, shell execution, destructive SQL, credential access,
network egress, and **a credential appearing in a tool argument** — the concrete
exfiltration path when an agent is persuaded to pass a key to an outbound tool.

### Whole conversations

```java
List<GuardBatchItem> items = messages.stream()
        .map(m -> GuardBatchItem.input(m.content()))
        .toList();
GuardBatchResult result = NirikshaAI.guardCheckBatch(items);
```

A per-string API is an N+1 for a multi-turn message array, which is every real chat
application. Up to 32 items; the returned action is the most severe of the set,
because one blocked message means the conversation must not be sent.

### When the guard is unreachable

| `guardFailMode(...)` | Behaviour |
|---|---|
| `GuardFailMode.OPEN` *(default)* | Allow the text through |
| `GuardFailMode.CLOSED` | Block everything |
| `GuardFailMode.SECRETS_CLOSED` | Allow everything **except** locally-detectable credentials |

Fail-open is the default because a guard outage must not take down your application
— but it is **never silent**. Every fall-back logs a warning, sets
`verdict.failedOpen()`, and increments a `guard.fail_open` counter. A silent
fail-open is a security hole wearing a reliability costume: the control appears to
work right up until the moment it is needed.

`SECRETS_CLOSED` is the mode worth using in production. Ten prefix-anchored secret
formats are embedded in the SDK — AWS, GitHub, Slack, Stripe, Google, OpenAI,
Anthropic, PEM private keys, NirikshaAI's own — so a server outage stops credential
exfiltration locally while everything else still flows. `CLOSED` is correct only for
a hard compliance boundary; for everyone else it converts a guard outage into an
application outage.

`NirikshaAI.localSecretFindings(text)` runs those same patterns directly, for data
you are about to log, where depending on the guard being reachable would be the
wrong trade.

### Where the guard lives

The guard endpoint is served by the **OTLP gateway**, not the REST API. In SaaS
those are different hosts, so the URL is derived from `otlpEndpoint` when you set
it, and from `endpoint` when you do not:

| `endpoint` | `otlpEndpoint` | Derived guard URL |
|---|---|---|
| `https://niriksha.internal` | *(unset)* | `https://niriksha.internal` |
| `https://app.niriksha.ai` | `grpc-ingest.niriksha.ai:443` | `https://grpc-ingest.niriksha.ai:443` |
| *(any)* | `niriksha.internal:4317` | `http://niriksha.internal:4318` |

The last row translates the gateway's default gRPC port to its default HTTP port. A
non-default port is used as configured, since guessing would be worse than reusing
what you already set. Call `guardEndpoint(...)` for anything this does not cover — a
wrong value shows up as "guard unreachable" on every call.

Requests time out after 3 seconds with **no retry**: this is on the critical path in
front of your model call, and retrying would turn a 3-second timeout into a 9-second
one. The fail mode is a better answer than a slower one.

---

## 9. Examples

| Example | Description |
|---------|-------------|
| [`examples/spring-boot/`](examples/spring-boot/) | Spring Boot 3 application — `@Configuration` bean + `OrderController` with custom spans |
| [`examples/plain-java/`](examples/plain-java/) | No-framework HTTP server using `com.sun.net.httpserver` |

---

## Contributing

For guidelines on branching, commits, and releases, see [CONTRIBUTING.md](CONTRIBUTING.md).

### Release Process

This SDK follows **semantic versioning** with automatic version bumping:

- **`develop` branch** — feature work merges here → triggers dev build (GitHub pre-release, no Maven Central publish)
- **`main` branch** — only `develop` can merge → triggers production release with auto semver bump

See [RELEASE.md](RELEASE.md) for the full release process, and [REGISTRY_SETUP.md](REGISTRY_SETUP.md) for Maven Central credentials setup.

### Security

Report security vulnerabilities via [SECURITY.md](SECURITY.md), not via public issues.

---

## License

Apache 2.0 — see [LICENSE](LICENSE).
