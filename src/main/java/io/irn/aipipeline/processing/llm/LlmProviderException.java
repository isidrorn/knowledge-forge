package io.irn.aipipeline.processing.llm;

import lombok.Getter;

/**
 * Thrown by a provider when it cannot complete the request.
 * The gateway catches this to try the next provider in the chain.
 */
@Getter
public class LlmProviderException extends RuntimeException {

    private final String providerName;

    public LlmProviderException(String providerName, Throwable cause) {
        this(providerName, cause.getMessage(), cause);
    }

    public LlmProviderException(String providerName, String detail, Throwable cause) {
        super("Provider '%s' failed: %s".formatted(providerName, detail), cause);
        this.providerName = providerName;
    }

}
