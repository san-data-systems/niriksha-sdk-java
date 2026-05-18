package ai.niriksha.sdk;

import java.util.regex.Pattern;

/**
 * Redacts common PII patterns from strings before they are attached to spans or logs.
 *
 * <p>The following patterns are replaced, applied in order:
 * <ol>
 *   <li>Email addresses → {@code [REDACTED_EMAIL]}</li>
 *   <li>US phone numbers → {@code [REDACTED_PHONE]}</li>
 *   <li>US Social Security Numbers → {@code [REDACTED_SSN]}</li>
 *   <li>Credit/debit card numbers (13–16 digits, optionally space/dash separated) → {@code [REDACTED_CC]}</li>
 * </ol>
 *
 * <p>Example:
 * <pre>{@code
 * String safe = PiiRedactor.redact(llmOutput);
 * SpanHelpers.recordRagChunk(span,
 *     RagChunk.builder()
 *         .chunkId("c1").source("db").score(0.9)
 *         .content(safe)
 *         .build());
 * }</pre>
 *
 * <p>This class provides best-effort, heuristic redaction and is not a substitute for a
 * dedicated PII detection service in high-compliance environments.
 */
public final class PiiRedactor {

    /** Matches common email address formats. */
    static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");

    /**
     * Matches US phone numbers in common formats:
     * {@code (555) 867-5309}, {@code 555-867-5309}, {@code +1 555 867 5309}, etc.
     */
    static final Pattern PHONE_PATTERN = Pattern.compile(
            "(\\+?1[\\s.\\-]?)?\\(?\\d{3}\\)?[\\s.\\-]?\\d{3}[\\s.\\-]?\\d{4}");

    /** Matches US Social Security Numbers in {@code NNN-NN-NNNN} format. */
    static final Pattern SSN_PATTERN = Pattern.compile(
            "\\b\\d{3}-\\d{2}-\\d{4}\\b");

    /**
     * Matches 13–16 digit card numbers, optionally separated by single spaces or dashes.
     * Applied last so it does not interfere with phone and SSN patterns.
     */
    static final Pattern CC_PATTERN = Pattern.compile(
            "\\b(?:\\d[ \\-]?){13,16}\\b");

    private PiiRedactor() {}

    /**
     * Replaces PII patterns in {@code input} with placeholder strings.
     *
     * @param input the string to redact; may be {@code null}
     * @return the redacted string, or {@code null} if {@code input} was {@code null}
     */
    public static String redact(String input) {
        if (input == null) {
            return null;
        }
        String result = EMAIL_PATTERN.matcher(input).replaceAll("[REDACTED_EMAIL]");
        result = PHONE_PATTERN.matcher(result).replaceAll("[REDACTED_PHONE]");
        result = SSN_PATTERN.matcher(result).replaceAll("[REDACTED_SSN]");
        result = CC_PATTERN.matcher(result).replaceAll("[REDACTED_CC]");
        return result;
    }
}
