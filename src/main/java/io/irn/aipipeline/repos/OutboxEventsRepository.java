package io.irn.aipipeline.repos;

import io.irn.aipipeline.domain.OutboxEvents;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxEventsRepository extends JpaRepository<OutboxEvents, UUID> {

    @EntityGraph(attributePaths = "articleProcessed")
    List<OutboxEvents> findTop20ByStatusOrderByCreatedAtAsc(String status);

    @Query("SELECT o FROM OutboxEvents o WHERE o.status = 'FAILED'")
    List<OutboxEvents> findAllFailed();
}
