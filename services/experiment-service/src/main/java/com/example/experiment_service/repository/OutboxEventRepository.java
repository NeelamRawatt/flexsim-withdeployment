package com.example.experiment_service.repository;

import com.example.experiment_service.entity.OutboxEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads and writes the outbox table. */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * The oldest NOT-sent rows, oldest first (keeps the order).
     *
     * FOR UPDATE SKIP LOCKED: if 2 copies of experiment-service run (Kubernetes),
     * each copy LOCKS the rows it takes and SKIPS rows the other copy has locked.
     * So the same row is never sent by both copies at the same time.
     * Must be called inside a transaction (OutboxPublisher does that).
     */
    @Query(value = """
            SELECT * FROM outbox_event
            WHERE sent_at IS NULL
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextUnsent(@Param("limit") int limit);

    /** Deletes SENT rows older than the given time (daily cleanup). Returns how many were deleted. */
    @Modifying
    @Query("delete from OutboxEvent e where e.sentAt is not null and e.sentAt < :before")
    int deleteSentBefore(@Param("before") LocalDateTime before);
}