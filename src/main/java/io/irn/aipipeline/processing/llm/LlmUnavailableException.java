package io.irn.aipipeline.processing.llm;

/**
 * Thrown by the gateway when all providers in the chain have failed.
 * The processing service should catch this and mark the article as FAILED.
 */
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(String message) {
        super(message);
    }
}
