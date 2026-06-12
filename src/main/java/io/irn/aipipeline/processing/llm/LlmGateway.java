package io.irn.aipipeline.processing.llm;

/**
 * Public contract for LLM interaction.
 * The processing pipeline only depends on this interface —
 * it never knows which provider actually responded.
 */
public interface LlmGateway {

    LlmResponse complete(String prompt);
}
