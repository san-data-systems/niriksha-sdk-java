package com.example;

import ai.niriksha.sdk.NirikshaAI;
import ai.niriksha.sdk.ShutdownHook;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * Minimal HTTP server (no framework) that demonstrates the NirikshaAI Java SDK.
 *
 * <p>Uses {@link com.sun.net.httpserver.HttpServer} from the JDK — zero additional
 * dependencies beyond the SDK itself.
 *
 * <h2>Running</h2>
 * <pre>
 *   # Install SDK first
 *   cd ../../  &amp;&amp;  mvn install -DskipTests
 *
 *   # Build and run
 *   cd examples/plain-java
 *   export NIRIKSHA_API_KEY=nai_your_key_here
 *   mvn package -DskipTests
 *   java -jar target/niriksha-plain-java-demo-0.1.0-jar-with-dependencies.jar
 * </pre>
 *
 * <h2>Endpoints</h2>
 * <pre>
 *   GET http://localhost:8080/       — health check
 *   GET http://localhost:8080/ping   — liveness probe
 * </pre>
 *
 * Both endpoints create OpenTelemetry spans visible in your NirikshaAI dashboard.
 */
public final class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        // ------------------------------------------------------------------
        // 1. Initialise NirikshaAI — must happen before any instrumented code
        // ------------------------------------------------------------------
        String apiKey = System.getenv("NIRIKSHA_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("ERROR: NIRIKSHA_API_KEY environment variable is not set.");
            System.exit(1);
        }

        String endpoint    = envOrDefault("NIRIKSHA_ENDPOINT",      "https://app.niriksha.ai");
        String otlpAddr    = envOrDefault("NIRIKSHA_OTLP_ENDPOINT", "grpc-ingest.niriksha.ai:443");
        String serviceName = envOrDefault("NIRIKSHA_SERVICE_NAME",  "plain-java-demo");
        String environment = envOrDefault("NIRIKSHA_ENVIRONMENT",   "production");

        ShutdownHook nirikshaHook = NirikshaAI.builder()
                .endpoint(endpoint)
                .otlpEndpoint(otlpAddr)
                .apiKey(apiKey)
                .serviceName(serviceName)
                .environment(environment)
                .metricsExportInterval(Duration.ofSeconds(30))
                .build();

        // ------------------------------------------------------------------
        // 2. Grab a global tracer — do this AFTER build() registers the SDK
        // ------------------------------------------------------------------
        Tracer tracer = GlobalOpenTelemetry.getTracer("com.example.plain-java-demo", "0.1.0");

        // ------------------------------------------------------------------
        // 3. Set up the HTTP server
        // ------------------------------------------------------------------
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), /*backlog=*/0);

        // Route: GET /
        server.createContext("/", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            handleWithSpan(tracer, "http.get /", exchange, () -> {
                Span current = Span.current();
                current.setAttribute("http.route", "/");
                current.setAttribute(AttributeKey.stringKey("custom.handler"), "root");

                String body = "{\"status\":\"ok\",\"service\":\"" + serviceName + "\"}";
                sendJson(exchange, 200, body);
            });
        });

        // Route: GET /ping
        server.createContext("/ping", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            handleWithSpan(tracer, "http.get /ping", exchange, () -> {
                Span current = Span.current();
                current.setAttribute("http.route", "/ping");
                current.setAttribute(AttributeKey.stringKey("custom.handler"), "ping");

                // Simulate a small unit of work and record it as a span event
                long start = System.nanoTime();
                doWork();
                long elapsed = System.nanoTime() - start;
                current.addEvent("work.complete",
                        io.opentelemetry.api.common.Attributes.of(
                                AttributeKey.longKey("elapsed_ns"), elapsed));

                sendJson(exchange, 200, "{\"pong\":true}");
            });
        });

        server.setExecutor(null); // use the default executor
        server.start();

        LOGGER.info("Server started on http://localhost:" + PORT);
        LOGGER.info("Press Ctrl+C to stop.");

        // ------------------------------------------------------------------
        // 4. Block until the JVM receives a signal.
        //    The NirikshaAI SDK has registered its own shutdown hook — it will
        //    flush all pending telemetry before the JVM exits.
        //    We also register an explicit hook to stop the HTTP server cleanly.
        // ------------------------------------------------------------------
        final HttpServer finalServer = server;
        final ShutdownHook finalHook = nirikshaHook;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Stopping HTTP server...");
            finalServer.stop(1 /*delay seconds*/);
            // Close the SDK explicitly so telemetry is flushed before the JVM hooks run
            finalHook.close();
        }, "http-server-shutdown"));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Wraps a handler in a named span, sets HTTP attributes, and records any
     * thrown exception as an error on the span before re-throwing.
     */
    private static void handleWithSpan(Tracer tracer,
                                       String spanName,
                                       HttpExchange exchange,
                                       ThrowingRunnable handler) {
        Span span = tracer.spanBuilder(spanName)
                .setAttribute("http.method",  exchange.getRequestMethod())
                .setAttribute("http.target",  exchange.getRequestURI().getPath())
                .setAttribute("net.peer.name", exchange.getRemoteAddress().getHostString())
                .startSpan();

        try (Scope ignored = span.makeCurrent()) {
            handler.run();
            span.setAttribute("http.status_code", 200L);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            span.setAttribute("http.status_code", 500L);
            try {
                sendJson(exchange, 500, "{\"error\":\"internal server error\"}");
            } catch (IOException ignored) { /* best-effort */ }
        } finally {
            span.end();
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** Simulates a unit of work so we have something interesting in the trace. */
    private static void doWork() {
        long sum = 0;
        for (int i = 0; i < 100_000; i++) {
            sum += i;
        }
        // prevent JIT from eliminating the loop
        if (sum < 0) throw new IllegalStateException("unexpected");
    }

    private static String envOrDefault(String name, String defaultValue) {
        String v = System.getenv(name);
        return (v != null && !v.isBlank()) ? v : defaultValue;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
