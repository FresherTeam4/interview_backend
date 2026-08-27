package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.SessionTurn;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SessionTurnRepository extends JpaRepository<SessionTurn, Long> {

    @Query("""
            select turn
            from SessionTurn turn
            left join fetch turn.question
            where turn.session.id = :sessionId
              and turn.session.user.id = :userId
            order by turn.turnIndex
            """)
    List<SessionTurn> findOwnedHistory(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId);

    Optional<SessionTurn> findFirstBySessionIdAndSessionUserIdOrderByTurnIndexDesc(
            Long sessionId,
            Long userId);

    Optional<SessionTurn> findBySessionIdAndClientTurnId(
            Long sessionId,
            String clientTurnId);

    List<SessionTurn> findBySessionIdAndQuestionIdOrderByTurnIndexAsc(
            Long sessionId,
            Long questionId);

    @Query("""
            select turn
            from SessionTurn turn
            join fetch turn.question
            where turn.id = :turnId
              and turn.session.id = :sessionId
            """)
    Optional<SessionTurn> findWithQuestionByIdAndSessionId(
            @Param("turnId") Long turnId,
            @Param("sessionId") Long sessionId);

    @Query("""
            select session.id
            from InterviewSession session
            where session.status = :status
              and session.awaitingAction in :awaitingActions
              and session.processingStage = :stage
              and (session.nextRetryAt is null or session.nextRetryAt <= :now)
              and (
                  session.processingToken is null
                  or session.processingStartedAt < :staleBefore
              )
              and exists (
                  select candidate.id
                  from SessionTurn candidate
                  where candidate.session = session
                    and candidate.role = com.baseProject.myBaseProject.enums.TurnRole.CANDIDATE
                    and not exists (
                        select interviewer.id
                        from SessionTurn interviewer
                        where interviewer.session = session
                          and interviewer.role =
                              com.baseProject.myBaseProject.enums.TurnRole.INTERVIEWER
                          and interviewer.turnIndex > candidate.turnIndex
                    )
              )
            order by session.updatedAt, session.id
            """)
    List<Long> findRecoverableNextTurnSessionIds(
            @Param("status") SessionStatus status,
            @Param("awaitingActions") List<AwaitingAction> awaitingActions,
            @Param("stage") SessionProcessingStage stage,
            @Param("now") Instant now,
            @Param("staleBefore") Instant staleBefore,
            Pageable pageable);
}
