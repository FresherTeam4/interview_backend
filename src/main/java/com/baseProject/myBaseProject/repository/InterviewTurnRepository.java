package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.InterviewTurn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewTurnRepository extends JpaRepository<InterviewTurn, Long> {
    List<InterviewTurn> findBySessionIdOrderByTurnIndexAsc(Long sessionId);

    List<InterviewTurn> findTop12BySessionIdOrderByTurnIndexDesc(Long sessionId);

    Optional<InterviewTurn> findBySessionIdAndTurnIndex(Long sessionId, int turnIndex);

    Optional<InterviewTurn> findByIdAndSessionId(Long id, Long sessionId);

    Optional<InterviewTurn> findBySessionIdAndIdempotencyKey(Long sessionId, String idempotencyKey);

    Optional<InterviewTurn> findByReplyToTurnId(Long replyToTurnId);
}
