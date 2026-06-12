package io.irn.aipipeline.processing.rest;

import io.irn.aipipeline.processing.ArticleProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/pipeline")
@RequiredArgsConstructor
public class ProcessingEndpoint {

    private final ArticleProcessor articleProcessor;

    /**
     * @Transactional(REQUIRES_NEW) está en ArticleProcessor.process() —
     * se activa a través del proxy al llamar desde este controlador.
     */
    @PostMapping("/process/{articleRawId}")
    public ResponseEntity<Void> process(@PathVariable UUID articleRawId) {
        articleProcessor.process(articleRawId);
        return ResponseEntity.accepted().build();
    }
}
