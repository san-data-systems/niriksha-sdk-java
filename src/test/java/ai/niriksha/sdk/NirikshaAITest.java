package ai.niriksha.sdk;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link NirikshaAI} builder.
 *
 * <p>These tests exercise configuration logic only — no real network calls are made.
 * The {@code build()} tests point at {@code localhost:4317} with {@code insecure=true}
 * so that the OTel exporter initialisation is exercised without needing a live server.
 */
class NirikshaAITest {

    private ShutdownHook hook;

    @AfterEach
    void tearDown() {
        if (hook != null) {
            hook.close();
            hook = null;
        }
        // Reset OTel global between tests
        io.opentelemetry.api.GlobalOpenTelemetry.resetForTest();
    }

    // -----------------------------------------------------------------------
    // Builder default values
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Builder has correct default field values before build()")
    void builderDefaults() {
        NirikshaAI.Builder builder = NirikshaAI.builder();

        // Reflectively read private fields via the package-private helper methods
        // used in the resolveXxx() path. We test them indirectly through
        // resolveTls() which is package-private for testability.

        // Default: insecure=false, no endpoint → tls defaults to true
        // (resolveTls() returns true when endpoint starts with "https")
        // We set endpoint to https to verify the positive path.
        builder.endpoint("https://app.niriksha.ai");
        assertTrue(builder.resolveTls(), "TLS should be on for https endpoint");

        // Default serviceName and environment are observable via resolveGrpcAddress
        // indirectly — we just verify no NPE and the address is well-formed.
        String addr = builder.resolveGrpcAddress(true);
        assertTrue(addr.startsWith("https://"), "Address should use https scheme");
        assertTrue(addr.contains("app.niriksha.ai"), "Address should contain hostname");
        assertTrue(addr.contains(":4317"), "Address should use default otlp port 4317");
    }

    // -----------------------------------------------------------------------
    // otlpEndpoint override
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Custom otlpEndpoint is used verbatim in the gRPC address")
    void otlpEndpointOverride() {
        NirikshaAI.Builder builder = NirikshaAI.builder()
                .endpoint("https://app.niriksha.ai")
                .otlpEndpoint("ingest.niriksha.ai:4317");

        String addr = builder.resolveGrpcAddress(true);

        assertEquals("https://ingest.niriksha.ai:4317", addr,
                "When otlpEndpoint is set it should override the derived address");
    }

    @Test
    @DisplayName("otlpEndpoint with http:// scheme is stripped and re-prefixed correctly")
    void otlpEndpointSchemeStripping() {
        NirikshaAI.Builder builder = NirikshaAI.builder()
                .endpoint("https://app.niriksha.ai")
                .otlpEndpoint("https://ingest.niriksha.ai:4317");

        // With insecure=false (TLS on), the scheme should be https://
        String addrTls = builder.resolveGrpcAddress(true);
        assertEquals("https://ingest.niriksha.ai:4317", addrTls);

        // With insecure=true (TLS off), the scheme should be http://
        String addrPlain = builder.resolveGrpcAddress(false);
        assertEquals("http://ingest.niriksha.ai:4317", addrPlain);
    }

    // -----------------------------------------------------------------------
    // insecure flag — full build() smoke test
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("insecure=true does not throw when building with localhost address")
    void insecureFlag() {
        assertDoesNotThrow(() -> {
            hook = NirikshaAI.builder()
                    .endpoint("http://localhost:8080")
                    .apiKey("nai_test_key")
                    .serviceName("test-service")
                    .environment("test")
                    .insecure(true)
                    .enableMetrics(false)   // skip metric exporter to keep test fast
                    .enableLogs(false)      // skip log exporter to keep test fast
                    .build();
        }, "build() with insecure=true should not throw");

        assertNotNull(hook, "ShutdownHook must not be null after successful build()");
        assertNotNull(hook.getOpenTelemetry(), "OpenTelemetrySdk must not be null");
    }

    // -----------------------------------------------------------------------
    // Validation — missing required fields
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("build() throws IllegalStateException when both endpoint and otlpEndpoint are missing")
    void missingEndpointThrows() {
        NirikshaAI.Builder builder = NirikshaAI.builder().apiKey("nai_test");

        assertThrows(IllegalStateException.class, builder::build,
                "build() should throw when no endpoint is configured");
    }

    @Test
    @DisplayName("build() throws IllegalStateException when apiKey is missing")
    void missingApiKeyThrows() {
        NirikshaAI.Builder builder = NirikshaAI.builder()
                .endpoint("https://app.niriksha.ai");

        assertThrows(IllegalStateException.class, builder::build,
                "build() should throw when apiKey is not set");
    }
}
