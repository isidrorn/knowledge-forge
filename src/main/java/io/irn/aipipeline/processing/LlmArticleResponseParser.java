package io.irn.aipipeline.processing;

import io.irn.aipipeline.processing.dto.LlmArticleResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LlmArticleResponseParser {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public LlmArticleResponse parse(String rawResponse) {
        String json = extractTopLevelJsonObject(rawResponse);
        LlmArticleResponse response = readResponse(json, rawResponse);
        validate(response, rawResponse);
        return response;
    }

    private LlmArticleResponse readResponse(String json, String rawResponse) {
        try {
            return objectMapper.readValue(json, LlmArticleResponse.class);
        } catch (Exception e) {
            throw new LlmArticleParseException("Invalid LLM article JSON: " + e.getMessage(), rawResponse, e);
        }
    }

    private void validate(LlmArticleResponse response, String rawResponse) {
        Set<ConstraintViolation<LlmArticleResponse>> violations = validator.validate(response);
        if (!violations.isEmpty()) {
            throw new LlmArticleParseException(
                    "LLM article JSON failed validation: " + summarizeViolations(violations),
                    rawResponse,
                    null
            );
        }
    }

    private String summarizeViolations(Set<ConstraintViolation<LlmArticleResponse>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
    }

    private String extractTopLevelJsonObject(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new LlmArticleParseException("LLM returned a blank response", rawResponse, null);
        }

        String text = rawResponse.trim();
        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (current == '"') {
                inString = true;
            } else if (current == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (current == '}' && depth > 0) {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }

        if (start >= 0) {
            return text.substring(start);
        }
        throw new LlmArticleParseException("No JSON object found in LLM response", rawResponse, null);
    }
}
