package io.irn.aipipeline.repos;

import io.irn.aipipeline.domain.ArticleRaw;
import io.irn.aipipeline.domain.PipelineStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PipelineStatusLogRepository extends JpaRepository<PipelineStatusLog, Long> {

    Optional<PipelineStatusLog> findTopByArticleRawOrderByChangedAtDesc(ArticleRaw articleRaw);
}
