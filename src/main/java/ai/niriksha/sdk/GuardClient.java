package ai.niriksha.sdk;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inline AI security guard — checks text before it reaches the model.
 *
 * <p>The rest of this SDK is observability: it records what happened. This is
 * enforcement. It calls the gateway's synchronous {@code /v1/guard} endpoint, which
 * returns a verdict in single-digit milliseconds, so a prompt injection can be
 * refused and a leaked credential stripped before the provider call is made.
 *
 * <p>Not part of the public API — use {@link NirikshaAI#guardCheck} and friends.
 *
 * <p>Three deliberate differences from how comparable SDKs behave, each because the
 * obvious choice is worse:
 *
 * <ol>
 *   <li>A redact verdict returns normally. Only a block throws
 *       {@link GuardBlockedException}. Throwing on both means a customer who asked
 *       for PII stripping gets their application broken instead of their data
 *       protected.</li>
 *   <li>Fail-open stays the default but stops being silent. Every fall-back logs a
 *       warning and increments {@code guard.fail_open}.</li>
 *   <li>{@link GuardFailMode#SECRETS_CLOSED} embeds the secret patterns here, so
 *       when the server is unreachable credential exfiltration is still blocked
 *       locally while everything else fails open.</li>
 * </ol>
 */
final class GuardClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuardClient.class);

    /**
     * Requests are bounded because this sits on the caller's critical path. A guard
     * that hangs is worse than one that is absent: the absent one fails fast.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    /**
     * Mirrors the server's cap, so an oversized batch fails here with a clear
     * message rather than as a 400 from the gateway.
     */
    static final int MAX_BATCH = 32;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    private final String baseUrl;
    private final String apiKey;
    private final GuardFailMode failMode;
    private final String mode;

    private volatile LongCounter failOpenCounter;

    GuardClient(String baseUrl, String apiKey, GuardFailMode failMode, String mode) {
        this.baseUrl  = baseUrl.replaceAll("/$", "");
        this.apiKey   = apiKey;
        this.failMode = failMode != null ? failMode : GuardFailMode.OPEN;
        this.mode     = mode;
    }

    // ── public surface (package-private; NirikshaAI is the entry point) ──────

    GuardVerdict check(String text, String direction) {
        String dir = direction == null || direction.isBlank() ? "input" : direction;
        StringBuilder body = new StringBuilder("{\"text\":").append(jsonString(text))
                .append(",\"direction\":").append(jsonString(dir));
        appendMode(body);
        body.append('}');

        GuardVerdict v = evaluate(baseUrl + "/v1/guard", body.toString(), text);
        throwIfBlocked(v);
        return v;
    }

    GuardVerdict checkTool(String name, String argumentsJson) {
        String args = argumentsJson != null ? argumentsJson : "";
        StringBuilder body = new StringBuilder("{\"tool\":{\"name\":").append(jsonString(name))
                .append(",\"arguments\":").append(jsonString(args)).append('}');
        appendMode(body);
        body.append('}');

        // The fallback text for the secrets-closed local check includes the
        // arguments: a credential passed to an outbound tool is the concrete
        // exfiltration path, so it is exactly what must still be inspected when the
        // server is unreachable.
        GuardVerdict v = evaluate(baseUrl + "/v1/guard", body.toString(), name + "\n" + args);
        throwIfBlocked(v);
        return v;
    }

    GuardBatchResult checkBatch(List<GuardBatchItem> items) {
        if (items == null || items.isEmpty()) {
            throw new NirikshaAIException("guardCheckBatch requires at least one item");
        }
        if (items.size() > MAX_BATCH) {
            throw new NirikshaAIException(
                    "guardCheckBatch accepts at most " + MAX_BATCH + " items, got " + items.size());
        }

        StringBuilder body = new StringBuilder("{\"items\":[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                body.append(',');
            }
            GuardBatchItem item = items.get(i);
            String dir = item.getDirection() == null || item.getDirection().isBlank()
                    ? "input" : item.getDirection();
            body.append("{\"text\":").append(jsonString(item.getText()))
                    .append(",\"direction\":").append(jsonString(dir));
            appendMode(body);
            body.append('}');
        }
        body.append("]}");

        String response = post(baseUrl + "/v1/guard/batch", body.toString());
        if (response == null) {
            List<GuardVerdict> verdicts = new ArrayList<>(items.size());
            for (GuardBatchItem item : items) {
                verdicts.add(failVerdict(item.getText()));
            }
            GuardBatchResult result = new GuardBatchResult(worst(verdicts), verdicts);
            throwIfBlocked(result);
            return result;
        }

        List<GuardVerdict> verdicts = parseResults(response);
        GuardAction action = parseAggregateAction(response, verdicts);
        GuardBatchResult result = new GuardBatchResult(action, verdicts);
        throwIfBlocked(result);
        return result;
    }

    // ── internals ───────────────────────────────────────────────────────────

    private void appendMode(StringBuilder body) {
        if (mode != null && !mode.isBlank()) {
            body.append(",\"mode\":").append(jsonString(mode));
        }
    }

    private void throwIfBlocked(GuardVerdict v) {
        if (v.getAction() != GuardAction.BLOCK) {
            return;
        }
        throw new GuardBlockedException(v, ruleList(v));
    }

    private void throwIfBlocked(GuardBatchResult result) {
        if (result.getAction() != GuardAction.BLOCK) {
            return;
        }
        // One blocked message means the conversation must not be sent, so the
        // aggregate is what throws — carrying the offending verdict when there is
        // one, since a policy-mandated block can arrive with no findings.
        for (GuardVerdict v : result.getVerdicts()) {
            if (v.isBlocked()) {
                throw new GuardBlockedException(v, ruleList(v));
            }
        }
        throw new GuardBlockedException(
                new GuardVerdict(GuardAction.BLOCK, null, null, "", 0, "", "", false, false), "");
    }

    private static String ruleList(GuardVerdict v) {
        List<String> rules = new ArrayList<>();
        for (GuardFinding f : v.getFindings()) {
            if (!f.getRule().isEmpty()) {
                rules.add(f.getRule());
            }
        }
        return String.join(", ", rules);
    }

    private GuardVerdict evaluate(String url, String body, String fallbackText) {
        String response = post(url, body);
        return response == null ? failVerdict(fallbackText) : parseVerdict(response);
    }

    /**
     * POSTs to the guard. Returns {@code null} when the guard could not be consulted.
     *
     * <p>Deliberately no retry. This is a synchronous call in front of the caller's
     * model request: retrying turns a 3-second timeout into a 9-second one, and the
     * fail mode is a better answer than a slower one.
     */
    private String post(String url, String body) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            LOGGER.warn("NirikshaAI guard: refusing non-http(s) URL");
            return null;
        }
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("X-API-Key", apiKey)
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                // A 4xx is a client bug — a bad key, a malformed body — and means the
                // guard has never worked rather than that it is briefly down.
                if (resp.statusCode() < 500) {
                    LOGGER.error("NirikshaAI guard: {} returned {}", url, resp.statusCode());
                } else {
                    LOGGER.warn("NirikshaAI guard: {} returned {}", url, resp.statusCode());
                }
                return null;
            }
            return resp.body();
        } catch (InterruptedException e) {
            // Restoring the flag is required: swallowing it leaves the thread
            // uninterruptible for whatever runs on it next.
            Thread.currentThread().interrupt();
            LOGGER.warn("NirikshaAI guard: interrupted");
            return null;
        } catch (Exception e) {
            LOGGER.warn("NirikshaAI guard: {} unreachable ({})", url, e.getMessage());
            return null;
        }
    }

    /**
     * Applies the configured fail mode.
     *
     * <p>Never silent. Every fail-open trip logs and is countable, because a security
     * control that quietly stops working is worse than one that was never installed —
     * the second is at least known to be absent.
     */
    GuardVerdict failVerdict(String text) {
        countFailOpen();

        if (failMode == GuardFailMode.CLOSED) {
            LOGGER.warn("NirikshaAI guard: unreachable and fail mode is closed — blocking");
            return new GuardVerdict(GuardAction.BLOCK, null, null, "", 0, "", "", false, true);
        }

        if (failMode == GuardFailMode.SECRETS_CLOSED) {
            List<GuardFinding> findings = localSecretFindings(text);
            if (!findings.isEmpty()) {
                LOGGER.warn("NirikshaAI guard: unreachable; blocking locally on {} secret pattern(s)",
                        findings.size());
                return new GuardVerdict(GuardAction.BLOCK, findings, null, "", 0, "", "", false, true);
            }
        }

        LOGGER.warn("NirikshaAI guard: unreachable — allowing text through (fail mode {}). "
                + "Text is NOT being checked.", failMode.wireValue());
        return new GuardVerdict(GuardAction.ALLOW, null, null, "", 0, "", "", false, true);
    }

    /**
     * Increments {@code guard.fail_open}.
     *
     * <p>The counter is a diagnostic: failing to record it must never turn a guard
     * outage into an application error.
     */
    private void countFailOpen() {
        try {
            LongCounter c = failOpenCounter;
            if (c == null) {
                c = GlobalOpenTelemetry.getMeter("nirikshaai.guard")
                        .counterBuilder("guard.fail_open")
                        .setDescription("Guard calls that could not reach the server")
                        .build();
                failOpenCounter = c;
            }
            c.add(1, Attributes.builder().put("fail_mode", failMode.wireValue()).build());
        } catch (RuntimeException e) {
            LOGGER.debug("NirikshaAI guard: could not record the fail_open counter");
        }
    }

    static GuardAction worst(List<GuardVerdict> verdicts) {
        GuardAction worst = GuardAction.ALLOW;
        for (GuardVerdict v : verdicts) {
            if (v.getAction().ordinal() > worst.ordinal()) {
                worst = v.getAction();
            }
        }
        return worst;
    }

    // ── response parsing ────────────────────────────────────────────────────

    /**
     * Reads the batch response's top-level {@code action}, falling back to the most
     * severe per-item verdict.
     *
     * <p>Only the prefix before the {@code results} array is searched. Scanning the
     * whole body would find the *first item's* action when the top-level one is
     * absent, silently reporting a two-item batch as whatever its first entry said —
     * which is how this was originally written, and what the test caught.
     */
    GuardAction parseAggregateAction(String json, List<GuardVerdict> verdicts) {
        int resultsAt = json.indexOf("\"results\"");
        String prefix = resultsAt >= 0 ? json.substring(0, resultsAt) : json;
        String wire = extractString(prefix, "action");
        return wire.isEmpty() ? worst(verdicts) : GuardAction.fromWire(wire);
    }

    GuardVerdict parseVerdict(String json) {
        return new GuardVerdict(
                GuardAction.fromWire(extractString(json, "action")),
                parseFindings(json),
                extractStringArray(json, "reasons"),
                extractString(json, "redacted"),
                extractInt(json, "risk_score"),
                extractString(json, "risk_severity"),
                extractString(json, "policy_source"),
                extractBool(json, "policy_enforced"),
                false);
    }

    /** Splits the {@code results} array of a batch response and parses each entry. */
    List<GuardVerdict> parseResults(String json) {
        List<GuardVerdict> out = new ArrayList<>();
        for (String obj : splitObjects(json, "results")) {
            out.add(parseVerdict(obj));
        }
        return out;
    }

    private List<GuardFinding> parseFindings(String json) {
        List<GuardFinding> out = new ArrayList<>();
        for (String obj : splitObjects(json, "findings")) {
            out.add(new GuardFinding(
                    extractString(obj, "category"),
                    extractString(obj, "severity"),
                    extractString(obj, "rule"),
                    extractDouble(obj, "confidence"),
                    extractInt(obj, "start"),
                    extractInt(obj, "end"),
                    extractBool(obj, "local")));
        }
        return out;
    }

    /**
     * Extracts each top-level object from the named array.
     *
     * <p>Brace-counting rather than a regex, because a finding contains no nested
     * object today but a regex would break silently the day one does.
     */
    private static List<String> splitObjects(String json, String key) {
        List<String> out = new ArrayList<>();
        int keyAt = json.indexOf("\"" + key + "\"");
        if (keyAt < 0) {
            return out;
        }
        int arrayStart = json.indexOf('[', keyAt);
        if (arrayStart < 0) {
            return out;
        }
        int depth = 0;
        int start = -1;
        for (int i = arrayStart + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == ']' && depth == 0) {
                break;
            } else if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    out.add(json.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return out;
    }

    private static String extractString(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(json);
        return m.find() ? m.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : "";
    }

    private static List<String> extractStringArray(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(json);
        if (!m.find()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        Matcher items = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(m.group(1));
        while (items.find()) {
            out.add(items.group(1));
        }
        return out;
    }

    private static int extractInt(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static double extractDouble(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : 0.0;
    }

    private static boolean extractBool(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(true|false)").matcher(json);
        return m.find() && "true".equals(m.group(1));
    }

    private static String jsonString(String s) {
        if (s == null) {
            return "\"\"";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    // ── local secret patterns, for SECRETS_CLOSED ───────────────────────────
    //
    // A deliberately small, prefix-anchored subset of the server's set. The point is
    // not parity — the server has eighteen patterns, entropy gating and a placeholder
    // denylist — but that the highest-confidence, zero-false-positive formats are
    // still caught with no network call. Every one is a vendor's own key prefix, so a
    // match is near-certain and a non-match is cheap.

    private static final Map<String, Pattern> LOCAL_SECRETS = Map.of(
            "aws_access_key", Pattern.compile("\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b"),
            "github_token", Pattern.compile("\\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{36,}\\b"),
            "github_pat", Pattern.compile("\\bgithub_pat_[A-Za-z0-9_]{22,}\\b"),
            "slack_token", Pattern.compile("\\bxox[baprs]-[A-Za-z0-9-]{10,}\\b"),
            "stripe_secret_key", Pattern.compile("\\b(?:sk|rk)_live_[A-Za-z0-9]{20,}\\b"),
            "google_api_key", Pattern.compile("\\bAIza[0-9A-Za-z_-]{35}\\b"),
            "anthropic_api_key", Pattern.compile("\\bsk-ant-[A-Za-z0-9_-]{20,}\\b"),
            "openai_api_key", Pattern.compile("\\bsk-(?:proj-)?[A-Za-z0-9_-]{20,}\\b"),
            "private_key_block",
            Pattern.compile("-----BEGIN (?:RSA |EC |OPENSSH |PGP |DSA )?PRIVATE KEY-----"),
            "niriksha_api_key", Pattern.compile("\\bnai_(?:plat_)?[A-Za-z0-9]{20,}\\b"));

    /**
     * Documentation values that match a real pattern.
     *
     * <p>Without this the local check would block on a README, and the first person
     * it inconveniences would switch the mode off.
     */
    private static final Set<String> LOCAL_PLACEHOLDERS =
            Set.of("akiaiosfodnn7example", "aws_access_key_id");

    /** Detects embedded secret formats without calling the server. */
    static List<GuardFinding> localSecretFindings(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<GuardFinding> out = new ArrayList<>();
        for (Map.Entry<String, Pattern> e : LOCAL_SECRETS.entrySet()) {
            Matcher m = e.getValue().matcher(text);
            while (m.find()) {
                if (LOCAL_PLACEHOLDERS.contains(m.group().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                out.add(new GuardFinding("secret", "critical", e.getKey(), 0.95,
                        m.start(), m.end(), true));
            }
        }
        return out;
    }
}
