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
import java.util.concurrent.ConcurrentHashMap;
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

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // In-memory prompt cache with TTL
    private static final ConcurrentHashMap<String, _CacheEntry> CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    private record _CacheEntry(PromptResponse response, long expiresAt) {}

    private final String baseUrl;
    private final String apiKey;

    PromptClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.apiKey  = apiKey;
    }

    /**
     * Fetches a prompt by name, optionally pinning to a version and substituting variables.
     * Results are cached in-memory for {@value #CACHE_TTL_MS} ms.
     */
    PromptResponse getPrompt(String name, GetPromptOptions options) {
        String key = cacheKey(name, options);
        _CacheEntry cached = CACHE.get(key);
        if (cached != null && System.currentTimeMillis() < cached.expiresAt()) {
            return cached.response();
        }

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
        PromptResponse response = parsePromptResponse(body);
        CACHE.put(key, new _CacheEntry(response, System.currentTimeMillis() + CACHE_TTL_MS));
        return response;
    }

    /**
     * Lists all available prompts in this project.
     */
    List<PromptResponse> listPrompts() {
        String body = get(baseUrl + "/api/v1/prompts");
        return parsePromptList(body);
    }

    /**
     * Clears the in-memory prompt cache. Useful for testing or forced refresh scenarios.
     */
    public static void clearCache() {
        CACHE.clear();
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
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
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

    PromptResponse parsePromptResponse(String json) {
        String name        = extractString(json, "name");
        String text        = extractString(json, "text");
        String description = extractString(json, "description");
        String createdAt   = extractString(json, "created_at");
        String updatedAt   = extractString(json, "updated_at");
        int    version     = extractInt(json, "version");
        List<String> tags  = extractStringArray(json, "tags");
        return new PromptResponse(name, version, text, description, tags, createdAt, updatedAt);
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

    private List<String> extractStringArray(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher m = p.matcher(json);
        if (!m.find()) return List.of();
        String inner = m.group(1);
        List<String> result = new ArrayList<>();
        Matcher items = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(inner);
        while (items.find()) result.add(items.group(1));
        return result;
    }

    private int extractInt(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(json);
        if (m.find()) return Integer.parseInt(m.group(1));
        return 0;
    }

    private static String cacheKey(String name, GetPromptOptions opts) {
        String version = opts != null && opts.getVersion() != null ? opts.getVersion().toString() : "latest";
        String vars    = opts != null ? opts.getVariables().toString() : "{}";
        return name + ":" + version + ":" + vars;
    }
}
