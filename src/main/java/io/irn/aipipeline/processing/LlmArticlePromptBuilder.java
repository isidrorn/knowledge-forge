package io.irn.aipipeline.processing;

import org.springframework.stereotype.Component;

@Component
public class LlmArticlePromptBuilder {

    private static final int INVALID_RESPONSE_PROMPT_LIMIT = 4_000;

    public String buildExtractionPrompt(String url, String content, ProcessingStrategy strategy) {
        return strategy == ProcessingStrategy.SIMPLIFIED_PROMPT
                ? buildSimplifiedPrompt(url, content)
                : buildStandardPrompt(url, content);
    }

    public String buildJsonRepairPrompt(String url, String content, String invalidResponse, String parseError) {
        return """
                Your previous response was not valid JSON and could not be parsed.
                Parse error: %s

                Re-run the extraction and return a replacement response.

                STRICT RULES:
                - Return exactly one JSON object and nothing else.
                - Do not wrap the JSON in markdown fences.
                - Do not include prose, comments, trailing commas, or schema keywords.
                - Include every required field: tldr, keyPoints, tags, difficulty, markdownContent.
                - keyPoints and tags must be valid JSON arrays closed with ']'.
                - markdownContent must be a JSON string; escape line breaks as \\n if needed.

                ARTICLE URL: %s

                ARTICLE CONTENT:
                %s

                %s

                INVALID PREVIOUS RESPONSE:
                %s
                """.formatted(
                abbreviate(parseError, 500),
                url,
                content,
                outputContract(),
                abbreviate(invalidResponse, INVALID_RESPONSE_PROMPT_LIMIT)
        );
    }

    private String buildStandardPrompt(String url, String content) {
        return """
                You are a technical knowledge extraction assistant.
                Your task is to analyze a technical article and extract its core content.

                RULES:
                - Remove all noise: personal introductions, hypothetical scenarios, calls to action,
                  social media prompts ("follow me", "like this post"), generic disclaimers, and
                  unrelated opinions.
                - Keep ONLY: technical concepts, code examples, architecture explanations,
                  comparisons, benchmarks, and actionable technical guidance.
                - markdownContent must be a clean, structured technical summary in Markdown.
                  Use ## for sections, bullet points for lists, and fenced code blocks for code.
                - tldr: 2-3 sentences maximum, purely technical.
                - keyPoints: 3-7 specific technical takeaways.
                - tags: lowercase, no spaces (e.g. "spring-boot", "jvm", "kafka").
                - difficulty: 1=beginner, 2=easy, 3=intermediate, 4=advanced, 5=expert.
                - Return only valid JSON. No markdown fences, prose, comments, trailing commas, or schema keywords.

                ARTICLE URL: %s

                ARTICLE CONTENT:
                %s

                %s
                """.formatted(url, content, outputContract());
    }

    private String buildSimplifiedPrompt(String url, String content) {
        return """
                You are a technical knowledge extraction assistant.
                Analyze the article and extract only the essential metadata. Be concise.

                RULES:
                - tldr: 2-3 sentences maximum, purely technical.
                - keyPoints: 3-5 specific technical takeaways (keep it short).
                - tags: lowercase, no spaces (e.g. "spring-boot", "jvm", "kafka").
                - difficulty: 1=beginner, 2=easy, 3=intermediate, 4=advanced, 5=expert.
                - markdownContent: write a single short paragraph summarizing the technical topic only.
                - Return only valid JSON. No markdown fences, prose, comments, trailing commas, or schema keywords.

                ARTICLE URL: %s

                ARTICLE CONTENT:
                %s

                %s
                """.formatted(url, content, outputContract());
    }

    private String outputContract() {
        return """
                OUTPUT CONTRACT:
                Return exactly one JSON object with this shape:
                {
                  "tldr": "2-3 sentence technical summary",
                  "keyPoints": ["specific technical takeaway 1", "specific technical takeaway 2", "specific technical takeaway 3"],
                  "tags": ["lowercase-tag", "another-tag"],
                  "difficulty": 3,
                  "markdownContent": "Clean Markdown technical summary as a JSON string"
                }

                Do not return or copy a JSON schema. Do not include fields such as type, properties, required, or additionalProperties.
                """;
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }
}
