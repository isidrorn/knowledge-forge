package io.irn.aipipeline.ingestion;

/**
 * Thrown when the content of an article URL cannot be fetched.
 */
public class ArticleFetchException extends RuntimeException {

    public ArticleFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
