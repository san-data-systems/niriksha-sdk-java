package ai.niriksha.sdk;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Internal HTTP client for fetching versioned prompts from the NirikshaAI prompt vault.
 * Not part of the public API — use {@link NirikshaAI#getPrompt} and
 * {@link NirikshaAI#listPrompts} instead.
 */
final class PromptClient {

    private static final Logger LOGGER = Logger.getLogger(PromptClient.class.getName());

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient http;

    PromptClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.apiKey  = apiKey;
        this.http    = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Fetches a prompt by name, optionally pinning to a version and substituting variables.
     */
    PromptResponse getPrompt(String name, GetPromptOptions options) {
        StringBuilder url = new StringBuilder(baseUrl)
                .append("/api/v1/prompts/")
                .append(URLEncoder.encode(name, StandardCharsets.UTF_8));

        boolean first = true;
        if (options != null) {
            if (options.getVersion() != null) {
                url.append(first ? "?" : "&").append("version=").append(options.getVersion());
                first = false;
            }
            for (Map.Entry<String, String> e : options.getVariables().entrySet()) {
                url.append(first ? "?" : "&")
                   .append("var[").append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                   .append("]=").append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
                first = false;
            }
        }

        String body = get(url.toString());
        return parsePromptResponse(body);
    }

    /**
     * Lists all available prompts in this project.
     */
    List<PromptResponse> listPrompts() {
        String body = get(baseUrl + "/api/v1/prompts");
        return parsePromptList(body);
    }

    // -----------------------------------------------------------------------
    // HTTP helpers
    // -----------------------------------------------------------------------

    private String get(String url) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-API-Key", apiKey)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new NirikshaAIException(
                        "Prompt request failed with HTTP " + resp.statusCode() + ": " + resp.body());
            }
            return resp.body();
        } catch (NirikshaAIException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "NirikshaAI: prompt request error", e);
            throw new NirikshaAIException("Prompt request failed: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Minimal JSON parsing (no external dependency)
    // -----------------------------------------------------------------------

    private static final Pattern STRING_FIELD =
            Pattern.compile("\"(\\w+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern INT_FIELD =
            Pattern.compile("\"(\\w+)\"\\s*:\\s*(\\d+)");

    PromptResponse parsePromptResponse(String json) {
        String name        = extractString(json, "name");
        String text        = extractString(json, "text");
        String description = extractString(json, "description");
        int    version     = extractInt(json, "version");
        return new PromptResponse(name, version, text, description);
    }

    List<PromptResponse> parsePromptList(String json) {
        List<PromptResponse> result = new ArrayList<>();
        // Split on top-level objects — find each {...} block in the data array
        int depth = 0;
        int start = -1;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    String obj = json.substring(start, i + 1);
                    try {
                        result.add(parsePromptResponse(obj));
                    } catch (Exception ignored) {
                        // skip malformed entries
                    }
                    start = -1;
                }
            }
        }
        return result;
    }

    private String extractString(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        return "";
    }

    private int extractInt(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(json);
        if (m.find()) return Integer.parseInt(m.group(1));
        return 0;
    }
}
