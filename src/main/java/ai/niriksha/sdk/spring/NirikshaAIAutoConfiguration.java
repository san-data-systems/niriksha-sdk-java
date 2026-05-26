package ai.niriksha.sdk.spring;

import ai.niriksha.sdk.NirikshaAI;
import ai.niriksha.sdk.ShutdownHook;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot autoconfiguration for NirikshaAI.
 *
 * Activated automatically when {@code nirikshaai.api-key} is set in application properties.
 * Can be disabled with {@code nirikshaai.enabled=false}.
 */
@AutoConfiguration
@EnableConfigurationProperties(NirikshaAIProperties.class)
@ConditionalOnProperty(prefix = "nirikshaai", name = "api-key")
public class NirikshaAIAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ShutdownHook nirikshaAIShutdownHook(NirikshaAIProperties props) {
        NirikshaAI.Builder builder = NirikshaAI.builder()
                .endpoint(props.getEndpoint())
                .apiKey(props.getApiKey())
                .serviceName(props.getServiceName())
                .environment(props.getEnvironment())
                .enableMetrics(props.isEnableMetrics())
                .enableLogs(props.isEnableLogs())
                .sampleRate(props.getSampleRate())
                .insecure(props.isInsecure())
                .tlsSkipVerify(props.isTlsSkipVerify());

        if (props.getOtlpEndpoint() != null) {
            builder.otlpEndpoint(props.getOtlpEndpoint());
        }
        if (props.getCaCertFile() != null) {
            builder.caCertFile(props.getCaCertFile());
        }

        return builder.build();
    }
}
