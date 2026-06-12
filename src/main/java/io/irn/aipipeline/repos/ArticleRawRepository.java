package io.irn.aipipeline.repos;

import io.irn.aipipeline.domain.ArticleRaw;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArticleRawRepository extends JpaRepository<ArticleRaw, UUID> {

    Optional<ArticleRaw> findByUrl(String url);

    boolean existsByContentChecksum(String contentChecksum);

    List<ArticleRaw> findByStatus(String status);
}
