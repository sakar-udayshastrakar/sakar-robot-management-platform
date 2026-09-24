package com.sakarrobotics.cloud.task;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface RobotTaskRepository extends JpaRepository<RobotTask, UUID> {

    Page<RobotTask> findByRobotIdOrderByIdDesc(UUID robotId, Pageable pageable);

    Page<RobotTask> findByOrganizationIdInOrderByIdDesc(List<UUID> organizationIds, Pageable pageable);

    /** Unpaged, full-history — Operational Dashboard's "Total cumulative" figures (see DashboardService). */
    List<RobotTask> findByOrganizationIdIn(List<UUID> organizationIds);

    /** Bounded-window variants — retention/hotel-record aggregation only needs a recent slice, not the full table. */
    List<RobotTask> findByOrganizationIdInAndCreatedAtGreaterThanEqual(List<UUID> organizationIds, Instant since);

    List<RobotTask> findByCreatedAtGreaterThanEqual(Instant since);

    /**
     * {@code SELECT ... FOR UPDATE} — used only for the START transition of a
     * task, to close the race where two concurrent START requests could both
     * observe {@code CREATED} and both dispatch a physical command (see
     * {@link RobotTaskService#transition}). A second transaction requesting
     * this same row blocks until the first commits (or rolls back), then
     * re-reads the now-updated status — it never observes the stale
     * {@code CREATED} value the first transaction already acted on. An
     * explicit {@code @Query} (rather than a derived {@code findBy...} name)
     * so this is unambiguous and never accidentally changes the locking
     * behavior of the plain, unlocked {@code findById} every other caller
     * (GET endpoints, other transitions) already relies on.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RobotTask t where t.id = :id")
    Optional<RobotTask> lockByIdForUpdate(@Param("id") UUID id);
}
