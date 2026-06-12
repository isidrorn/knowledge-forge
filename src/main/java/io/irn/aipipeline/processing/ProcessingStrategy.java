package io.irn.aipipeline.processing;

/**
 * Estrategia de procesado LLM.
 * STANDARD:          prompt completo — tldr + keyPoints + tags + difficulty + markdownContent.
 * SIMPLIFIED_PROMPT: prompt reducido — solo tldr + keyPoints + tags + difficulty, sin markdownContent.
 *                    Usado en batch retry cuando el fallo previo fue por parse error (respuesta demasiado larga).
 */
public enum ProcessingStrategy {
    STANDARD,
    SIMPLIFIED_PROMPT
}
