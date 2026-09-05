package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {
    @EntityGraph(attributePaths = {"template", "profile"})
    Optional<InterviewSession> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = {"template", "profile"})
    Optional<InterviewSession> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT session FROM InterviewSession session WHERE session.id = :id")
    Optional<InterviewSession> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT session
            FROM InterviewSession session
            WHERE session.id = :id
              AND session.user.id = :userId
            """)
    Optional<InterviewSession> findOwnedByIdForUpdate(
            @Param("id") Long id,
            @Param("userId") Long userId);

    List<InterviewSession> findByStatusAndCreatedAtBefore(
            InterviewSessionStatus status, Instant createdBefore);
}
