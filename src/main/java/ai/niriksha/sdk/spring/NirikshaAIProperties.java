package ai.niriksha.sdk.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for NirikshaAI Spring Boot autoconfiguration.
 *
 * Example {@code application.properties}:
 * <pre>
 * nirikshaai.endpoint=https://app.niriksha.ai
 * nirikshaai.otlp-endpoint=grpc-ingest.niriksha.ai:443
 * nirikshaai.api-key=nai_...
 * nirikshaai.service-name=my-spring-app
 * nirikshaai.environment=production
 * nirikshaai.enable-metrics=true
 * nirikshaai.enable-logs=true
 * nirikshaai.sample-rate=1.0
 * nirikshaai.insecure=false
 * nirikshaai.tls-skip-verify=false
 * </pre>
 */
@ConfigurationProperties(prefix = "nirikshaai")
public class NirikshaAIProperties {

    private String endpoint = "https://app.niriksha.ai";
    private String otlpEndpoint;
    private String apiKey;
    private String serviceName = "my-service";
    private String environment = "production";
    private boolean enableMetrics = true;
    private boolean enableLogs = true;
    private double sampleRate = 1.0;
    private boolean insecure = false;
    private boolean tlsSkipVerify = false;
    private String caCertFile;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getOtlpEndpoint() {
        return otlpEndpoint;
    }

    public void setOtlpEndpoint(String otlpEndpoint) {
        this.otlpEndpoint = otlpEndpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public boolean isEnableMetrics() {
        return enableMetrics;
    }

    public void setEnableMetrics(boolean enableMetrics) {
        this.enableMetrics = enableMetrics;
    }

    public boolean isEnableLogs() {
        return enableLogs;
    }

    public void setEnableLogs(boolean enableLogs) {
        this.enableLogs = enableLogs;
    }

    public double getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(double sampleRate) {
        this.sampleRate = sampleRate;
    }

    public boolean isInsecure() {
        return insecure;
    }

    public void setInsecure(boolean insecure) {
        this.insecure = insecure;
    }

    public boolean isTlsSkipVerify() {
        return tlsSkipVerify;
    }

    public void setTlsSkipVerify(boolean tlsSkipVerify) {
        this.tlsSkipVerify = tlsSkipVerify;
    }

    public String getCaCertFile() {
        return caCertFile;
    }

    public void setCaCertFile(String caCertFile) {
        this.caCertFile = caCertFile;
    }
}
