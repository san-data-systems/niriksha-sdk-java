package ai.niriksha.sdk;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.trace.SdkTracerProvider;

import java.io.Closeable;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Returned by {@link NirikshaAI.Builder#build()}. Holds a reference to the configured
 * {@link OpenTelemetrySdk} and shuts it down gracefully when closed.
 *
 * <p>Usage:
 * <pre>{@code
 * try (ShutdownHook hook = NirikshaAI.builder()
 *         .endpoint("https://app.niriksha.ai")
 *         .apiKey(System.getenv("NIRIKSHA_API_KEY"))
 *         .serviceName("my-service")
 *         .build()) {
 *     // application runs here
 * }
 * }</pre>
 *
 * <p>A JVM shutdown hook is also registered automatically so that telemetry is
 * flushed even if the application exits without calling {@link #close()}.
 */
public final class ShutdownHook implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShutdownHook.class);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private final OpenTelemetrySdk openTelemetry;
    private final Thread jvmShutdownHook;

    ShutdownHook(OpenTelemetrySdk openTelemetry) {
        this.openTelemetry = openTelemetry;
        this.jvmShutdownHook = new Thread(this::shutdownProviders, "niriksha-otel-shutdown");
        Runtime.getRuntime().addShutdownHook(jvmShutdownHook);
    }

    /**
     * Returns the underlying {@link OpenTelemetrySdk} for advanced use cases.
     */
    public OpenTelemetrySdk getOpenTelemetry() {
        return openTelemetry;
    }

    /**
     * Force-flushes all pending telemetry. Delegates to {@link NirikshaAI#flush()}.
     */
    public void flush() {
        NirikshaAI.flush();
    }

    /**
     * Force-flushes all pending telemetry within the given timeout.
     * Delegates to {@link NirikshaAI#flush(java.time.Duration)}.
     *
     * @param timeout maximum time to wait for each provider to flush
     */
    public void flush(java.time.Duration timeout) {
        NirikshaAI.flush(timeout);
    }

    /**
     * Shuts down all telemetry providers, flushing any pending data.
     * Idempotent — safe to call more than once.
     */
    @Override
    public void close() {
        try {
            Runtime.getRuntime().removeShutdownHook(jvmShutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM is already shutting down — hook removal not possible, that's fine
        }
        shutdownProviders();
    }

    private void shutdownProviders() {
        LOGGER.debug("NirikshaAI: flushing and shutting down telemetry providers...");

        SdkTracerProvider tracerProvider = openTelemetry.getSdkTracerProvider();
        if (tracerProvider != null) {
            tracerProvider.forceFlush().join(SHUTDOWN_TIMEOUT.toMillis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            tracerProvider.shutdown().join(SHUTDOWN_TIMEOUT.toMillis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        SdkMeterProvider meterProvider = openTelemetry.getSdkMeterProvider();
        if (meterProvider != null) {
            meterProvider.forceFlush().join(SHUTDOWN_TIMEOUT.toMillis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            meterProvider.shutdown().join(SHUTDOWN_TIMEOUT.toMillis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        SdkLoggerProvider loggerProvider = openTelemetry.getSdkLoggerProvider();
        if (loggerProvider != null) {
            loggerProvider.forceFlush().join(SHUTDOWN_TIMEOUT.toMillis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            loggerProvider.shutdown().join(SHUTDOWN_TIMEOUT.toMillis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        LOGGER.debug("NirikshaAI: telemetry shutdown complete.");
    }
}
