package ai.niriksha.sdk;

import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporterBuilder;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporterBuilder;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ResourceAttributes;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the NirikshaAI Java SDK.
 *
 * <p>Configures the OpenTelemetry Java SDK with OTLP gRPC exporters pointed at
 * your NirikshaAI ingest endpoint, then registers the providers as globals so that
 * all instrumentation libraries in the same JVM automatically export to NirikshaAI.
 *
 * <h2>Quick start (SaaS)</h2>
 * <pre>{@code
 * ShutdownHook hook = NirikshaAI.builder()
 *     .endpoint("https://app.niriksha.ai")
 *     .otlpEndpoint("grpc-ingest.niriksha.ai:443")
 *     .apiKey(System.getenv("NIRIKSHA_API_KEY"))
 *     .serviceName("my-service")
 *     .environment("production")
 *     .build();
 * // ... your application ...
 * hook.close(); // or use try-with-resources
 * }</pre>
 *
 * <h2>Private Cloud (trusted CA)</h2>
 * <pre>{@code
 * ShutdownHook hook = NirikshaAI.builder()
 *     .endpoint("https://niriksha.internal.example.com")
 *     .apiKey(System.getenv("NIRIKSHA_API_KEY"))
 *     .caCertFile("/etc/ssl/certs/internal-ca.pem")
 *     .serviceName("my-service")
 *     .build();
 * }</pre>
 */
public final class NirikshaAI {

    private static final Logger LOGGER = LoggerFactory.getLogger(NirikshaAI.class);

    // Shared state populated by Builder.build() — used by eval/prompt helpers.
    private static volatile EvalClient evalClient;
    private static volatile PromptClient promptClient;
    private static volatile boolean initialized;

    private NirikshaAI() {
        // utility class — use builder()
    }

    /**
     * Returns a new {@link Builder} for configuring the SDK.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns {@code true} if the SDK has been successfully initialised via
     * {@link Builder#build()}.
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Force-flushes all pending spans, metrics, and log records using a 5-second timeout.
     * Call before process exit in serverless or short-lived environments.
     */
    public static void flush() {
        flush(java.time.Duration.ofSeconds(5));
    }

    /**
     * Force-flushes all pending spans, metrics, and log records within the given timeout.
     * Call before process exit in serverless or short-lived environments.
     *
     * @param timeout maximum time to wait for each provider to flush
     */
    public static void flush(java.time.Duration timeout) {
        long ms = timeout.toMillis();
        var otel = io.opentelemetry.api.GlobalOpenTelemetry.get();
        if (otel instanceof io.opentelemetry.sdk.OpenTelemetrySdk sdk) {
            sdk.getSdkTracerProvider()
                    .forceFlush()
                    .join(ms, java.util.concurrent.TimeUnit.MILLISECONDS);
            sdk.getSdkMeterProvider()
                    .forceFlush()
                    .join(ms, java.util.concurrent.TimeUnit.MILLISECONDS);
            sdk.getSdkLoggerProvider()
                    .forceFlush()
                    .join(ms, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    // -------------------------------------------------------------------------
    // Eval helpers
    // -------------------------------------------------------------------------

    /**
     * Submits a single LLM evaluation result to NirikshaAI.
     *
     * <p>Must be called after {@link Builder#build()} has been invoked.
     *
     * @throws IllegalStateException if the SDK has not been initialised yet
     * @throws NirikshaAIException   if the HTTP request fails
     */
    public static void submitEval(EvalInput input) {
        evalClient().submitEval(input);
    }

    /**
     * Submits a batch of LLM evaluation results in a single HTTP request.
     *
     * @throws IllegalStateException if the SDK has not been initialised yet
     * @throws NirikshaAIException   if the HTTP request fails
     */
    public static void submitEvalsBatch(java.util.List<EvalInput> inputs) {
        evalClient().submitEvalsBatch(inputs);
    }

    // -------------------------------------------------------------------------
    // Prompt helpers
    // -------------------------------------------------------------------------

    /**
     * Fetches the latest deployed version of the named prompt template.
     *
     * @param name prompt slug (e.g. {@code "customer-support-system"})
     * @throws IllegalStateException if the SDK has not been initialised yet
     * @throws NirikshaAIException   if the HTTP request fails
     */
    public static PromptResponse getPrompt(String name) {
        return promptClient().getPrompt(name, null);
    }

    /**
     * Fetches a prompt template with optional version pinning and variable substitution.
     *
     * @param name    prompt slug
     * @param options version + variables, or {@code null} for defaults
     * @throws IllegalStateException if the SDK has not been initialised yet
     * @throws NirikshaAIException   if the HTTP request fails
     */
    public static PromptResponse getPrompt(String name, GetPromptOptions options) {
        return promptClient().getPrompt(name, options);
    }

    /**
     * Lists all prompt templates available in the current project.
     *
     * @throws IllegalStateException if the SDK has not been initialised yet
     * @throws NirikshaAIException   if the HTTP request fails
     */
    public static java.util.List<PromptResponse> listPrompts() {
        return promptClient().listPrompts();
    }

    // -------------------------------------------------------------------------
    // Internal accessors
    // -------------------------------------------------------------------------

    /** Package-private — for test teardown only. Resets SDK state between tests. */
    static void resetForTest() {
        evalClient = null;
        promptClient = null;
        initialized = false;
    }

    private static EvalClient evalClient() {
        EvalClient c = evalClient;
        if (c == null) {
            throw new IllegalStateException(
                    "NirikshaAI: SDK not initialised — call NirikshaAI.builder()...build() first");
        }
        return c;
    }

    private static PromptClient promptClient() {
        PromptClient c = promptClient;
        if (c == null) {
            throw new IllegalStateException(
                    "NirikshaAI: SDK not initialised — call NirikshaAI.builder()...build() first");
        }
        return c;
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * Fluent builder for configuring and initializing the NirikshaAI SDK.
     */
    public static final class Builder {

        /** REST base URL, e.g. {@code https://app.niriksha.ai} */
        private String endpoint;

        /**
         * gRPC ingest address override, e.g. {@code grpc-ingest.niriksha.ai:443}.
         * When not set, the hostname is derived from {@link #endpoint} and
         * {@link #otlpPort}.
         */
        private String otlpEndpoint;

        /** Project API key ({@code nai_...}). */
        private String apiKey;

        /** OpenTelemetry {@code service.name} resource attribute. */
        private String serviceName = "my-service";

        /** OpenTelemetry {@code deployment.environment} resource attribute. */
        private String environment = "production";

        /** gRPC port when deriving the OTLP address from {@link #endpoint}. */
        private int otlpPort = 4317;

        /** When {@code true}, exporters use plaintext gRPC (no TLS). */
        private boolean insecure = false;

        /** When {@code true}, TLS certificate validation is disabled (dev/test only). */
        private boolean tlsSkipVerify = false;

        /**
         * Path to a PEM-encoded CA certificate file.
         * When set, the exporter trusts this CA instead of the JVM default trust store.
         */
        private String caCertFile;

        /** Metrics export interval. */
        private Duration metricsExportInterval = Duration.ofSeconds(60);

        /** Whether to enable OTLP metric export. */
        private boolean enableMetrics = true;

        /** Whether to enable OTLP log export. */
        private boolean enableLogs = true;

        /** Head-based trace sampling rate (0.0–1.0). Default: 1.0 (sample all traces). */
        private double sampleRate = 1.0;

        private Builder() {}

        // --- fluent setters ---

        /**
         * Sets the REST base URL of the NirikshaAI instance.
         * Example: {@code https://app.niriksha.ai}
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Overrides the gRPC ingest address.
         * Example: {@code grpc-ingest.niriksha.ai:443}
         * <p>When not set, the hostname is parsed from {@link #endpoint} and combined
         * with {@link #otlpPort}.
         */
        public Builder otlpEndpoint(String otlpEndpoint) {
            this.otlpEndpoint = otlpEndpoint;
            return this;
        }

        /**
         * Sets the project API key ({@code nai_...}).
         * Sent as the {@code x-api-key} gRPC metadata header on every request.
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the OpenTelemetry {@code service.name} resource attribute.
         * Defaults to {@code java-service}.
         */
        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        /**
         * Sets the OpenTelemetry {@code deployment.environment} resource attribute.
         * Defaults to {@code production}.
         */
        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        /**
         * Sets the gRPC port used when deriving the OTLP address from the REST
         * {@link #endpoint}. Defaults to {@code 4317}.
         */
        public Builder otlpPort(int otlpPort) {
            this.otlpPort = otlpPort;
            return this;
        }

        /**
         * When {@code true}, exporters use plaintext gRPC (no TLS).
         * Useful for local development or when a TLS-terminating proxy sits in front.
         */
        public Builder insecure(boolean insecure) {
            this.insecure = insecure;
            return this;
        }

        /**
         * When {@code true}, TLS certificate validation is skipped.
         * <b>Do not use in production.</b>
         */
        public Builder tlsSkipVerify(boolean tlsSkipVerify) {
            this.tlsSkipVerify = tlsSkipVerify;
            return this;
        }

        /**
         * Path to a PEM-encoded CA certificate file.
         * Use this when your NirikshaAI instance uses a private CA.
         */
        public Builder caCertFile(String caCertFile) {
            this.caCertFile = caCertFile;
            return this;
        }

        /**
         * Sets the metrics export interval. Defaults to {@code 60s}.
         */
        public Builder metricsExportInterval(Duration metricsExportInterval) {
            this.metricsExportInterval = metricsExportInterval;
            return this;
        }

        /**
         * Enables or disables OTLP metric export. Defaults to {@code true}.
         */
        public Builder enableMetrics(boolean enableMetrics) {
            this.enableMetrics = enableMetrics;
            return this;
        }

        /**
         * Enables or disables OTLP log export. Defaults to {@code true}.
         */
        public Builder enableLogs(boolean enableLogs) {
            this.enableLogs = enableLogs;
            return this;
        }

        /**
         * Sets the head-based trace sampling rate (0.0–1.0).
         * Use 0.1 to sample ~10% of traces. Default: 1.0 (sample all).
         */
        public Builder sampleRate(double sampleRate) {
            this.sampleRate = sampleRate;
            return this;
        }

        // --- build ---

        /**
         * Validates configuration, builds the OpenTelemetry SDK, registers it as the
         * global instance, and returns a {@link ShutdownHook} that flushes all pending
         * telemetry on close.
         *
         * @throws IllegalStateException if required fields are missing or configuration
         *                               is invalid
         */
        public ShutdownHook build() {
            if (endpoint == null && otlpEndpoint == null) {
                throw new IllegalStateException(
                        "NirikshaAI: either endpoint or otlpEndpoint must be set");
            }
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("NirikshaAI: apiKey must be set");
            }

            boolean useTls = resolveTls();
            String grpcAddress = resolveGrpcAddress(useTls);

            LOGGER.debug("NirikshaAI: initialising (service={}, env={}, grpc={}, tls={})",
                    serviceName, environment, grpcAddress, useTls);

            Resource resource = Resource.getDefault().merge(
                    Resource.builder()
                            .put(ResourceAttributes.SERVICE_NAME, serviceName)
                            .put(ResourceAttributes.DEPLOYMENT_ENVIRONMENT, environment)
                            .put(AttributeKey.stringKey("telemetry.sdk.version"), SdkVersion.VERSION)
                            .put(AttributeKey.stringKey("telemetry.sdk.language"), SdkVersion.LANGUAGE)
                            .build());

            // -- Trace exporter --
            OtlpGrpcSpanExporter spanExporter = buildSpanExporter(grpcAddress, useTls);

            io.opentelemetry.sdk.trace.samplers.Sampler sampler;
            if (sampleRate >= 1.0) {
                sampler = io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOn();
            } else if (sampleRate <= 0.0) {
                sampler = io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOff();
            } else {
                sampler = io.opentelemetry.sdk.trace.samplers.Sampler.parentBased(
                        io.opentelemetry.sdk.trace.samplers.Sampler.traceIdRatioBased(sampleRate));
            }

            SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                    .setResource(resource)
                    .setSampler(sampler)
                    .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                    .build();

            // -- Metrics exporter (optional) --
            SdkMeterProvider meterProvider = null;
            if (enableMetrics) {
                OtlpGrpcMetricExporter metricExporter = buildMetricExporter(grpcAddress, useTls);
                PeriodicMetricReader metricReader = PeriodicMetricReader.builder(metricExporter)
                        .setInterval(metricsExportInterval)
                        .build();
                meterProvider = SdkMeterProvider.builder()
                        .setResource(resource)
                        .registerMetricReader(metricReader)
                        .build();
            }

            // -- Log exporter (optional) --
            SdkLoggerProvider loggerProvider = null;
            if (enableLogs) {
                OtlpGrpcLogRecordExporter logExporter = buildLogExporter(grpcAddress, useTls);
                loggerProvider = SdkLoggerProvider.builder()
                        .setResource(resource)
                        .addLogRecordProcessor(
                                BatchLogRecordProcessor.builder(logExporter).build())
                        .build();
            }

            // -- Assemble SDK and register globals --
            var sdkBuilder = OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider);

            if (meterProvider != null) {
                sdkBuilder.setMeterProvider(meterProvider);
            }
            if (loggerProvider != null) {
                sdkBuilder.setLoggerProvider(loggerProvider);
            }

            OpenTelemetrySdk openTelemetry = sdkBuilder
                    .setPropagators(io.opentelemetry.context.propagation.ContextPropagators.create(
                            io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator.getInstance()))
                    .buildAndRegisterGlobal();

            // Wire eval and prompt helpers — use REST endpoint as the base URL.
            String restBase = endpoint != null ? endpoint : "https://app.niriksha.ai";
            NirikshaAI.evalClient = new EvalClient(restBase, apiKey);
            NirikshaAI.promptClient = new PromptClient(restBase, apiKey);
            NirikshaAI.initialized = true;

            // Quota errors are surfaced via the OpenTelemetry diagnostic logger
            // which emits to SLF4J automatically when slf4j-api is on the classpath

            return new ShutdownHook(openTelemetry);
        }

        // -----------------------------------------------------------------------
        // Internal helpers
        // -----------------------------------------------------------------------

        /**
         * Decides whether to use TLS.
         * Priority: {@code insecure} flag overrides, otherwise inferred from {@code endpoint}.
         */
        boolean resolveTls() {
            if (insecure) {
                return false;
            }
            if (endpoint != null) {
                return endpoint.startsWith("https");
            }
            // otlpEndpoint only — default to TLS on
            return true;
        }

        /**
         * Builds the gRPC address string that OTel Java exporters expect.
         * For plaintext: {@code http://host:port}
         * For TLS:       {@code https://host:port}
         */
        String resolveGrpcAddress(boolean useTls) {
            String hostAndPort;
            if (otlpEndpoint != null && !otlpEndpoint.isBlank()) {
                // Caller supplied explicit gRPC address; normalise to host:port
                hostAndPort = stripScheme(otlpEndpoint);
            } else {
                // Derive from REST endpoint
                String host = parseHost(endpoint);
                hostAndPort = host + ":" + otlpPort;
            }

            String scheme = useTls ? "https" : "http";
            return scheme + "://" + hostAndPort;
        }

        private String stripScheme(String addr) {
            if (addr.startsWith("https://")) {
                return addr.substring(8);
            }
            if (addr.startsWith("http://")) {
                return addr.substring(7);
            }
            return addr;
        }

        private String parseHost(String url) {
            try {
                return new URL(url).getHost();
            } catch (MalformedURLException e) {
                throw new IllegalStateException("NirikshaAI: invalid endpoint URL: " + url, e);
            }
        }

        private OtlpGrpcSpanExporter buildSpanExporter(String grpcAddress, boolean useTls) {
            OtlpGrpcSpanExporterBuilder b = OtlpGrpcSpanExporter.builder()
                    .setEndpoint(grpcAddress)
                    .addHeader("x-api-key", apiKey);
            applyTlsConfig(b, useTls);
            return b.build();
        }

        private OtlpGrpcMetricExporter buildMetricExporter(String grpcAddress, boolean useTls) {
            OtlpGrpcMetricExporterBuilder b = OtlpGrpcMetricExporter.builder()
                    .setEndpoint(grpcAddress)
                    .addHeader("x-api-key", apiKey);
            applyTlsConfig(b, useTls);
            return b.build();
        }

        private OtlpGrpcLogRecordExporter buildLogExporter(String grpcAddress, boolean useTls) {
            OtlpGrpcLogRecordExporterBuilder b = OtlpGrpcLogRecordExporter.builder()
                    .setEndpoint(grpcAddress)
                    .addHeader("x-api-key", apiKey);
            applyTlsConfig(b, useTls);
            return b.build();
        }

        /**
         * Applies TLS customisation to any OTLP exporter builder that exposes
         * {@code setSslContext(SSLContext, X509TrustManager)}.
         *
         * <ul>
         *   <li>{@code insecure=true}  — endpoint already uses {@code http://}, nothing extra needed.</li>
         *   <li>{@code tlsSkipVerify=true} — installs a trust-all {@link X509TrustManager}.</li>
         *   <li>{@code caCertFile!=null} — loads the PEM and installs a custom trust store.</li>
         * </ul>
         */
        private void applyTlsConfig(Object exporterBuilder, boolean useTls) {
            if (!useTls) {
                // plaintext — nothing to configure
                return;
            }

            if (tlsSkipVerify) {
                LOGGER.warn("TLS verification disabled — do not use in production");
                X509TrustManager trustAll = trustAllManager();
                SSLContext ctx = buildSslContext(trustAll);
                setExporterSsl(exporterBuilder, ctx, trustAll);
                return;
            }

            if (caCertFile != null) {
                try {
                    byte[] certBytes = Files.readAllBytes(Path.of(caCertFile));
                    X509TrustManager tm = trustManagerForCert(certBytes);
                    SSLContext ctx = buildSslContext(tm);
                    setExporterSsl(exporterBuilder, ctx, tm);
                } catch (Exception e) {
                    throw new IllegalStateException(
                            "NirikshaAI: failed to load CA cert from " + caCertFile, e);
                }
            }
            // else: use JVM default trust store — no customisation needed
        }

        private void setExporterSsl(Object builder, SSLContext ctx, X509TrustManager tm) {
            // OTel Java exporter builders share the same setSslContext API
            if (builder instanceof OtlpGrpcSpanExporterBuilder b) {
                b.setSslContext(ctx, tm);
            } else if (builder instanceof OtlpGrpcMetricExporterBuilder b) {
                b.setSslContext(ctx, tm);
            } else if (builder instanceof OtlpGrpcLogRecordExporterBuilder b) {
                b.setSslContext(ctx, tm);
            }
        }

        private SSLContext buildSslContext(X509TrustManager tm) {
            try {
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(null, new javax.net.ssl.TrustManager[]{tm}, null);
                return ctx;
            } catch (Exception e) {
                throw new IllegalStateException("NirikshaAI: failed to build SSLContext", e);
            }
        }

        /** Returns a trust manager that accepts all certificates. <b>Dev/test only.</b> */
        private X509TrustManager trustAllManager() {
            return new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    // intentionally empty — trust-all manager for dev/test only
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    // intentionally empty — trust-all manager for dev/test only
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            };
        }

        /** Builds a {@link X509TrustManager} that trusts the supplied PEM certificate. */
        private X509TrustManager trustManagerForCert(byte[] pemBytes) throws Exception {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert;
            try (InputStream is = new java.io.ByteArrayInputStream(pemBytes)) {
                cert = (X509Certificate) cf.generateCertificate(is);
            }

            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            ks.setCertificateEntry("niriksha-ca", cert);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);

            for (javax.net.ssl.TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager x) {
                    return x;
                }
            }
            throw new IllegalStateException("NirikshaAI: no X509TrustManager found");
        }
    }
}
