package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.VoiceAnswerAttempt;
import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VoiceAnswerAttemptRepository extends JpaRepository<VoiceAnswerAttempt, Long> {

    Optional<VoiceAnswerAttempt> findBySessionIdAndClientAttemptId(
            Long sessionId,
            String clientAttemptId);

    Optional<VoiceAnswerAttempt> findByIdAndSessionIdAndSessionUserId(
            Long id,
            Long sessionId,
            Long userId);

    Optional<VoiceAnswerAttempt> findFirstBySessionIdAndPromptTurnIdOrderByAttemptNoDesc(
            Long sessionId,
            Long promptTurnId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select attempt
            from VoiceAnswerAttempt attempt
            join fetch attempt.session session
            join fetch attempt.promptTurn prompt
            join fetch attempt.question
            where attempt.id = :attemptId
              and session.id = :sessionId
              and session.user.id = :userId
            """)
    Optional<VoiceAnswerAttempt> findOwnedByIdForUpdate(
            @Param("attemptId") Long attemptId,
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select attempt
            from VoiceAnswerAttempt attempt
            join fetch attempt.session session
            where attempt.id = :attemptId
            """)
    Optional<VoiceAnswerAttempt> findWithSessionByIdForUpdate(
            @Param("attemptId") Long attemptId);

    @Query("""
            select attempt
            from VoiceAnswerAttempt attempt
            join fetch attempt.session session
            where attempt.id = :attemptId
              and attempt.status = com.baseProject.myBaseProject.enums.VoiceAttemptStatus.TRANSCRIBING
              and attempt.processingToken = :token
            """)
    Optional<VoiceAnswerAttempt> findClaimedForTranscription(
            @Param("attemptId") Long attemptId,
            @Param("token") String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select attempt
            from VoiceAnswerAttempt attempt
            where attempt.session.id = :sessionId
              and attempt.promptTurn.id = :promptTurnId
            order by attempt.attemptNo desc
            """)
    List<VoiceAnswerAttempt> findPromptAttemptsForUpdate(
            @Param("sessionId") Long sessionId,
            @Param("promptTurnId") Long promptTurnId);

    @Query("""
            select attempt.id
            from VoiceAnswerAttempt attempt
            where (
                    attempt.status = com.baseProject.myBaseProject.enums.VoiceAttemptStatus.RECORDED
                    and (attempt.nextRetryAt is null or attempt.nextRetryAt <= :now)
                  )
               or (
                    attempt.status = com.baseProject.myBaseProject.enums.VoiceAttemptStatus.TRANSCRIBING
                    and (
                        attempt.processingToken is null
                        or attempt.processingStartedAt < :staleBefore
                    )
                  )
            order by attempt.createdAt, attempt.id
            """)
    List<Long> findRecoverableTranscriptionIds(
            @Param("now") Instant now,
            @Param("staleBefore") Instant staleBefore,
            Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update VoiceAnswerAttempt attempt
            set attempt.status = com.baseProject.myBaseProject.enums.VoiceAttemptStatus.TRANSCRIBING,
                attempt.processingToken = :token,
                attempt.processingStartedAt = :claimedAt,
                attempt.processingAttempts = attempt.processingAttempts + 1,
                attempt.nextRetryAt = null,
                attempt.statusMessage = null,
                attempt.version = attempt.version + 1
            where attempt.id = :attemptId
              and attempt.session.status = com.baseProject.myBaseProject.enums.SessionStatus.IN_PROGRESS
              and (
                    (
                        attempt.status = com.baseProject.myBaseProject.enums.VoiceAttemptStatus.RECORDED
                        and (
                            attempt.nextRetryAt is null
                            or attempt.nextRetryAt <= :claimedAt
                        )
                    )
                    or (
                        attempt.status = com.baseProject.myBaseProject.enums.VoiceAttemptStatus.TRANSCRIBING
                        and (
                            attempt.processingToken is null
                            or attempt.processingStartedAt < :staleBefore
                        )
                    )
                  )
            """)
    int claimTranscription(
            @Param("attemptId") Long attemptId,
            @Param("token") String token,
            @Param("claimedAt") Instant claimedAt,
            @Param("staleBefore") Instant staleBefore);

    @Query("""
            select max(attempt.attemptNo)
            from VoiceAnswerAttempt attempt
            where attempt.session.id = :sessionId
              and attempt.question.id = :questionId
            """)
    Short findMaxAttemptNo(
            @Param("sessionId") Long sessionId,
            @Param("questionId") Long questionId);
}
