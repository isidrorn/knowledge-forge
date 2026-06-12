package io.irn.aipipeline.processing.dto;

import jakarta.validation.constraints.*;

import java.util.List;

/**
 * Contrato de respuesta del LLM. Separado de la entidad JPA deliberadamente:
 * el schema que ve el modelo y el modelo de datos pueden evolucionar independientemente.
 */
public record LlmArticleResponse(

        @NotBlank
        String tldr,

        @NotEmpty @Size(min = 1, max = 10)
        List<String> keyPoints,

        @NotEmpty @Size(min = 1, max = 8)
        List<String> tags,

        @Min(1) @Max(5)
        int difficulty,

        @NotBlank
        String markdownContent
) {}