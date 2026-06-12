package io.irn.aipipeline.processing;

import io.irn.aipipeline.processing.dto.LlmArticleResponse;
import io.irn.aipipeline.processing.llm.LlmGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Single responsibility: preparedContent (plain text) → LlmArticleResponse.
 * All prompt building and LLM response parsing lives here.
 * Delegates the actual LLM call to LlmGateway — provider-agnostic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmArticleProcessor {

    private static final int RAW_RESPONSE_LOG_LIMIT = 2_000;

    private final LlmGateway llmGateway;
    private final LlmArticlePromptBuilder promptBuilder;
    private final LlmArticleResponseParser responseParser;

    public LlmArticleResponse process(String preparedContent, String articleUrl) {
        return process(preparedContent, articleUrl, ProcessingStrategy.STANDARD);
    }

    public LlmArticleResponse process(String preparedContent, String articleUrl, ProcessingStrategy strategy) {
        String prompt = promptBuilder.buildExtractionPrompt(articleUrl, preparedContent, strategy);

        log.debug("Calling LLM via gateway [url={}, strategy={}]", articleUrl, strategy);

        String raw = llmGateway.complete(prompt).content();

        try {
            return responseParser.parse(raw);
        } catch (LlmArticleParseException firstFailure) {
            log.warn("LLM returned invalid article JSON; retrying once with strict JSON prompt [url={}, strategy={}, reason={}, raw={}]",
                    articleUrl, strategy, firstFailure.getMessage(),
                    firstFailure.abbreviatedRawResponse(RAW_RESPONSE_LOG_LIMIT));
            return retryWithStrictJsonPrompt(articleUrl, preparedContent, raw, firstFailure);
        }
    }

    private LlmArticleResponse retryWithStrictJsonPrompt(
            String articleUrl,
            String preparedContent,
            String invalidResponse,
            LlmArticleParseException firstFailure
    ) {
        String retryPrompt = promptBuilder.buildJsonRepairPrompt(
                articleUrl,
                preparedContent,
                invalidResponse,
                firstFailure.getMessage()
        );
        String retryRaw = llmGateway.complete(retryPrompt).content();

        try {
            return responseParser.parse(retryRaw);
        } catch (LlmArticleParseException secondFailure) {
            var failure = new LlmArticleParseException(
                    "LLM returned invalid article JSON after repair attempt [url=%s]: %s"
                            .formatted(articleUrl, secondFailure.getMessage()),
                    retryRaw,
                    secondFailure
            );
            failure.addSuppressed(firstFailure);
            throw failure;
        }
    }
}
