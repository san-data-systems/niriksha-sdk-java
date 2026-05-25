package ai.niriksha.sdk;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PiiRedactorTest {

    @Test
    void redactsEmailAddress() {
        String result = PiiRedactor.redact("contact user@example.com please");
        assertEquals("contact [REDACTED_EMAIL] please", result);
    }

    @Test
    void redactsPhoneNumber() {
        String result = PiiRedactor.redact("call 555-123-4567 now");
        assertEquals("call [REDACTED_PHONE] now", result);
    }

    @Test
    void redactsSsn() {
        String result = PiiRedactor.redact("ssn 123-45-6789 found");
        assertEquals("ssn [REDACTED_SSN] found", result);
    }

    @Test
    void redactsCreditCard() {
        String result = PiiRedactor.redact("card 4111 1111 1111 1111 stored");
        assertEquals("card [REDACTED_CC] stored", result);
    }

    @Test
    void returnsUnchangedWhenNoPiiFound() {
        String input = "hello world";
        assertEquals(input, PiiRedactor.redact(input));
    }

    @Test
    void doesNotMutateInput() {
        String input = "email: test@test.com";
        PiiRedactor.redact(input);
        assertEquals("email: test@test.com", input); // strings are immutable in Java
    }
}
