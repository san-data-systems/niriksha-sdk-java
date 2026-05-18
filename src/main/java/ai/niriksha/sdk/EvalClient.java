package ai.niriksha.sdk;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Internal HTTP client for submitting evaluation results to the NirikshaAI REST API.
 * Not part of the public API — use {@link NirikshaAI#submitEval} instead.
 */
final class EvalClient {

    private static final Logger LOGGER = Logger.getLogger(EvalClient.class.getName());

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient http;

    EvalClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.apiKey  = apiKey;
        this.http    = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
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
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
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
        sb.append('}');
        return sb.toString();
    }

    private String jsonString(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
