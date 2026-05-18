package ai.niriksha.sdk;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Internal HTTP client for submitting evaluation results to the NirikshaAI REST API.
 * Not part of the public API — use {@link NirikshaAI#submitEval} instead.
 */
final class EvalClient {

    private static final Logger LOGGER = Logger.getLogger(EvalClient.class.getName());

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String baseUrl;
    private final String apiKey;

    EvalClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.apiKey  = apiKey;
    }

    /**
     * Submits a single evaluation result.
     *
     * @throws NirikshaAIException if the server returns a non-2xx response
     */
    void submitEval(EvalInput input) {
        submitEvalsBatch(List.of(input));
    }

    /**
     * Submits a batch of evaluation results in a single HTTP request.
     *
     * @throws NirikshaAIException if the server returns a non-2xx response
     */
    void submitEvalsBatch(List<EvalInput> inputs) {
        if (inputs == null || inputs.isEmpty()) return;

        String body = buildBatchJson(inputs);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/evals/batch"))
                .header("Content-Type", "application/json")
                .header("X-API-Key", apiKey)
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> resp = sendWithRetry(req);
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                LOGGER.log(Level.WARNING,
                        "NirikshaAI: eval submission failed (status={0}): {1}",
                        new Object[]{resp.statusCode(), resp.body()});
                throw new NirikshaAIException(
                        "Eval submission failed with HTTP " + resp.statusCode() + ": " + resp.body());
            }
            LOGGER.fine(() -> "NirikshaAI: submitted " + inputs.size() + " eval(s)");
        } catch (NirikshaAIException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "NirikshaAI: eval submission error", e);
            throw new NirikshaAIException("Eval submission failed: " + e.getMessage(), e);
        }
    }

    private HttpResponse<String> sendWithRetry(HttpRequest req) throws Exception {
        int maxAttempts = 3;
        Exception lastEx = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() < 500) return resp; // success or 4xx (don't retry 4xx)
                LOGGER.warning("NirikshaAI: eval attempt " + attempt + " got HTTP " + resp.statusCode());
            } catch (Exception e) {
                LOGGER.warning("NirikshaAI: eval attempt " + attempt + " failed: " + e.getMessage());
                lastEx = e;
            }
            if (attempt < maxAttempts) Thread.sleep(attempt * 500L);
        }
        throw lastEx != null ? lastEx : new NirikshaAIException("Eval failed after " + maxAttempts + " attempts");
    }

    private String buildBatchJson(List<EvalInput> inputs) {
        StringBuilder sb = new StringBuilder("{\"evals\":[");
        for (int i = 0; i < inputs.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(toJson(inputs.get(i)));
        }
        sb.append("]}");
        return sb.toString();
    }

    private String toJson(EvalInput e) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"trace_id\":").append(jsonString(e.getTraceId())).append(',');
        sb.append("\"metric_name\":").append(jsonString(e.getMetricName())).append(',');
        sb.append("\"score\":").append(e.getScore()).append(',');
        sb.append("\"label\":").append(jsonString(e.getLabel())).append(',');
        sb.append("\"eval_type\":").append(jsonString(e.getEvalType()));
        if (e.getExplanation() != null) {
            sb.append(",\"explanation\":").append(jsonString(e.getExplanation()));
        }
        if (e.getExperimentId() != null) {
            sb.append(",\"experiment_id\":").append(jsonString(e.getExperimentId()));
        }
        if (e.getConfidence() != null) {
            sb.append(",\"confidence\":").append(e.getConfidence());
        }
        if (e.getMetadata() != null && !e.getMetadata().isEmpty()) {
            sb.append(",\"metadata\":{");
            boolean first = true;
            for (Map.Entry<String, String> entry : e.getMetadata().entrySet()) {
                if (!first) sb.append(',');
                sb.append(jsonString(entry.getKey())).append(':').append(jsonString(entry.getValue()));
                first = false;
            }
            sb.append('}');
        }
        if (e.getEvalTime() != null) {
            sb.append(",\"eval_time\":").append(jsonString(e.getEvalTime().toString()));
        }
        sb.append('}');
        return sb.toString();
    }

    private String jsonString(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
