package io.irn.aipipeline.processing.llm;

/**
 * Internal contract for provider implementations.
 * Not exposed outside the llm package.
 */
interface LlmProvider {

    String name();

    LlmResponse complete(String prompt);
}
