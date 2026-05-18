package ai.niriksha.sdk;

/**
 * Thrown by NirikshaAI SDK operations when a network call or configuration step fails.
 */
public class NirikshaAIException extends RuntimeException {

    public NirikshaAIException(String message) {
        super(message);
    }

    public NirikshaAIException(String message, Throwable cause) {
        super(message, cause);
    }
}
