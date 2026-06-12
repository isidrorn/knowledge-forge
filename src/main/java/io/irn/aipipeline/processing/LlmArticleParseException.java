package io.irn.aipipeline.processing;

public class LlmArticleParseException extends IllegalArgumentException {

    private final String rawResponse;

    public LlmArticleParseException(String message, String rawResponse, Throwable cause) {
        super(message, cause);
        this.rawResponse = rawResponse;
    }

    public String rawResponse() {
        return rawResponse;
    }

    public String abbreviatedRawResponse(int maxLength) {
        if (rawResponse == null) {
            return "";
        }
        String normalized = rawResponse.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }
}
