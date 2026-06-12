package io.irn.aipipeline.repos;

import io.irn.aipipeline.domain.ArticleProcessed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;


public interface ArticleProcessedRepository extends JpaRepository<ArticleProcessed, UUID> {
}
