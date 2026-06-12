package io.irn.aipipeline.processing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Preprocesa rawContent antes del LLM:
 *  - Normaliza whitespace excesivo
 *  - Detecta si supera el umbral (isTooLarge)
 * El contenido ya llega como texto plano desde ArticleContentFetcher (jsoup).
 * No truncamos aquí: si es demasiado grande el servicio lo marca TOO_LARGE y conserva el original.
 */
@Slf4j
@Component
public class ArticleContentPreprocessor {

    public String prepare(String rawContent) {
        return rawContent
                .replaceAll("[ \t]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }

    public boolean isTooLarge(String rawContent, int maxChars) {
        return rawContent != null && rawContent.length() > maxChars;
    }
}
