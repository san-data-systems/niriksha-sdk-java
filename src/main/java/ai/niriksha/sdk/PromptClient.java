package ai.niriksha.sdk;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Internal HTTP client for fetching versioned prompts from the NirikshaAI prompt vault.
 * Not part of the public API — use {@link NirikshaAI#getPrompt} and
 * {@link NirikshaAI#listPrompts} instead.
 */
final class PromptClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PromptClient.class);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // In-memory prompt cache with TTL
    private static final ConcurrentHashMap<String, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    private record CacheEntry(PromptResponse response, long expiresAt) {}

    private final String baseUrl;
    private final String apiKey;

    PromptClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.apiKey  = apiKey;
    }

    /**
     * Renders a prompt by name, optionally pinning to a version and substituting variables.
     * Results are cached in-memory for {@value #CACHE_TTL_MS} ms.
     *
     * <p>This is a {@code POST} to {@code /api/v1/sdk/prompts/render} with a JSON body.
     * It previously issued {@code GET /api/v1/prompts/{name}?version=&var[k]=v}, which
     * the server has never served: wrong path, wrong method, and a query-parameter
     * encoding no handler reads. Every call 404'd.
     */
    PromptResponse getPrompt(String name, GetPromptOptions options) {
        String key = cacheKey(name, options);
        CacheEntry cached = CACHE.get(key);
        if (cached != null && System.currentTimeMillis() < cached.expiresAt()) {
            return cached.response();
        }

        Integer version = options != null ? options.getVersion() : null;
        Map<String, String> variables = options != null ? options.getVariables() : Map.of();
        String body = post(baseUrl + "/api/v1/sdk/prompts/render",
                buildRenderRequest(name, version, variables));

        PromptResponse response = parseRenderResponse(body, name, version);
        CACHE.put(key, new CacheEntry(response, System.currentTimeMillis() + CACHE_TTL_MS));
        return response;
    }

    /**
     * Lists all available prompts in this project.
     *
     * <p>The listing carries {@code id}, {@code name}, {@code description} and
     * {@code created_at} only — not the prompt text. {@link PromptResponse#getText()}
     * is therefore empty on a listed entry; call {@link #getPrompt} to render one.
     */
    List<PromptResponse> listPrompts() {
        String body = get(baseUrl + "/api/v1/sdk/prompts");
        return parsePromptList(body);
    }

    /** Builds the render request body. */
    String buildRenderRequest(String name, Integer version, Map<String, String> variables) {
        StringBuilder sb = new StringBuilder("{\"name\":").append(jsonString(name));
        if (version != null) {
            sb.append(",\"version\":").append(version);
        }
        if (variables != null && !variables.isEmpty()) {
            sb.append(",\"variables\":{");
            boolean first = true;
            for (Map.Entry<String, String> e : variables.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                sb.append(jsonString(e.getKey())).append(':').append(jsonString(e.getValue()));
                first = false;
            }
            sb.append('}');
        }
        return sb.append('}').toString();
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

    private String post(String url, String jsonBody) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return send(req, "Prompt render");
    }

    private String get(String url) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-API-Key", apiKey)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        return send(req, "Prompt request");
    }

    private String send(HttpRequest req, String what) {
        try {
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new NirikshaAIException(
                        what + " failed with HTTP " + resp.statusCode() + ": " + resp.body());
            }
            return resp.body();
        } catch (NirikshaAIException e) {
            throw e;
        } catch (InterruptedException e) {
            // Restoring the flag is required: swallowing it leaves the thread
            // uninterruptible for whatever runs next on it.
            Thread.currentThread().interrupt();
            throw new NirikshaAIException(what + " interrupted", e);
        } catch (Exception e) {
            LOGGER.warn("NirikshaAI: prompt request error", e);
            throw new NirikshaAIException(what + " failed: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Minimal JSON parsing (no external dependency)
    // -----------------------------------------------------------------------

    /**
     * Parses a render response.
     *
     * <p>The server returns {@code {"success":true,"data":{"content":"..."}}} — the
     * rendered text under {@code content}, and nothing else. The previous
     * implementation looked for {@code "text"}, {@code "version"} and {@code "tags"},
     * none of which that response carries, so {@link PromptResponse#getText()} came
     * back empty and the caller sent an empty prompt to the model. An empty prompt is
     * the worst possible failure here, because nothing errors.
     *
     * <p>The requested name and version are carried through from the call, since the
     * response does not echo them.
     */
    PromptResponse parseRenderResponse(String json, String name, Integer version) {
        String content = extractString(json, "content");
        if (content.isEmpty()) {
            // Distinguish "the prompt is genuinely empty" from "the response did not
            // contain what we expected", which is what the old code silently did.
            throw new NirikshaAIException(
                    "Prompt render returned no content for \'" + name + "\'; response: " + json);
        }
        return new PromptResponse(name, version != null ? version : 0, content, "",
                List.of(), "", "");
    }

    /**
     * Parses one entry of the prompt listing.
     *
     * <p>The listing carries {@code id}, {@code name}, {@code description} and
     * {@code created_at}. There is no text, version or tag data in it, so those come
     * back as empty — call {@link #getPrompt} to render a prompt.
     */
    PromptResponse parsePromptResponse(String json) {
        String name        = extractString(json, "name");
        String description = extractString(json, "description");
        String createdAt   = extractString(json, "created_at");
        int    version     = extractInt(json, "version");
        return new PromptResponse(name, version, "", description, List.of(), createdAt, "");
    }

    List<PromptResponse> parsePromptList(String json) {
        List<PromptResponse> result = new ArrayList<>();
        // Find the first '[' to locate the array, then parse each {...} within it
        int arrayStart = json.indexOf('[');
        if (arrayStart < 0) {
            return result;
        }
        int depth = 0;
        int start = -1;
        for (int i = arrayStart + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == ']' && depth == 0) {
                break; // end of array
            } else if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
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
        if (m.find()) {
            return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return "";
    }

    private int extractInt(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return 0;
    }

    /** Minimal JSON string escaping, matching EvalClient's. */
    private static String jsonString(String s) {
        if (s == null) {
            return "null";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String cacheKey(String name, GetPromptOptions opts) {
        String version = opts != null && opts.getVersion() != null ? opts.getVersion().toString() : "latest";
        String vars    = opts != null ? opts.getVariables().toString() : "{}";
        return name + ":" + version + ":" + vars;
    }
}
