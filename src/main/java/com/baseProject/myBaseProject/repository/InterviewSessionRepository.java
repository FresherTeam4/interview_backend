package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.repository.projection.SessionSummaryProjection;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    Optional<InterviewSession> findByIdAndUserId(Long id, Long userId);

    Optional<InterviewSession> findByUserIdAndCreationKey(Long userId, String creationKey);

    long countByUserIdAndStatusIn(Long userId, Collection<SessionStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from InterviewSession session
            where session.id = :sessionId
              and session.user.id = :userId
            """)
    Optional<InterviewSession> findOwnedByIdForUpdate(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from InterviewSession session where session.id = :sessionId")
    Optional<InterviewSession> findByIdForUpdate(@Param("sessionId") Long sessionId);

    @Query("""
            select new com.baseProject.myBaseProject.repository.projection.SessionSummaryProjection(
                session.id,
                session.difficulty,
                session.mode,
                session.status,
                session.awaitingAction,
                session.answeredQuestionCount,
                session.totalQuestionCount,
                session.overallScore,
                session.lastActivityAt,
                session.completedAt,
                session.createdAt,
                session.updatedAt)
            from InterviewSession session
            where session.user.id = :userId
              and session.status in :statuses
            """)
    Page<SessionSummaryProjection> findSummariesByUserIdAndStatuses(
            @Param("userId") Long userId,
            @Param("statuses") Collection<SessionStatus> statuses,
            Pageable pageable);

    @Query("""
            select session.id
            from InterviewSession session
            where session.status in :statuses
              and session.lastActivityAt <= :cutoff
            order by session.lastActivityAt, session.id
            """)
    List<Long> findExpiredIds(
            @Param("statuses") Collection<SessionStatus> statuses,
            @Param("cutoff") Instant cutoff,
            Pageable pageable);

    @Query("""
            select session.id
            from InterviewSession session
            where session.status in :statuses
              and session.processingStage in :stages
              and (session.nextRetryAt is null or session.nextRetryAt <= :now)
              and (
                  session.processingToken is null
                  or session.processingStartedAt < :staleBefore
              )
            order by session.updatedAt, session.id
            """)
    List<Long> findRecoverableWorkIds(
            @Param("statuses") Collection<SessionStatus> statuses,
            @Param("stages") Collection<SessionProcessingStage> stages,
            @Param("now") Instant now,
            @Param("staleBefore") Instant staleBefore,
            Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update InterviewSession session
            set session.processingToken = :token,
                session.processingStartedAt = :claimedAt,
                session.processingAttempts = session.processingAttempts + 1,
                session.nextRetryAt = null,
                session.statusMessage = null,
                session.updatedAt = :claimedAt,
                session.version = session.version + 1
            where session.id = :sessionId
              and session.processingStage = :stage
              and (session.nextRetryAt is null or session.nextRetryAt <= :claimedAt)
              and (
                  session.processingToken is null
                  or session.processingStartedAt < :staleBefore
              )
            """)
    int claimProcessing(
            @Param("sessionId") Long sessionId,
            @Param("stage") SessionProcessingStage stage,
            @Param("token") String token,
            @Param("claimedAt") Instant claimedAt,
            @Param("staleBefore") Instant staleBefore);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update InterviewSession session
            set session.processingToken = null,
                session.processingStartedAt = null,
                session.nextRetryAt = :nextRetryAt,
                session.statusMessage = :statusMessage,
                session.updatedAt = :updatedAt,
                session.version = session.version + 1
            where session.id = :sessionId
              and session.processingStage = :stage
              and session.processingToken = :token
            """)
    int releaseProcessingClaimForRetry(
            @Param("sessionId") Long sessionId,
            @Param("stage") SessionProcessingStage stage,
            @Param("token") String token,
            @Param("nextRetryAt") Instant nextRetryAt,
            @Param("statusMessage") String statusMessage,
            @Param("updatedAt") Instant updatedAt);

    boolean existsByIdAndProcessingStageAndProcessingToken(
            Long id,
            SessionProcessingStage processingStage,
            String processingToken);
}
