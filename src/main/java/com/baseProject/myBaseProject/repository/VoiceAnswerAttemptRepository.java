package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.VoiceAnswerAttempt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
