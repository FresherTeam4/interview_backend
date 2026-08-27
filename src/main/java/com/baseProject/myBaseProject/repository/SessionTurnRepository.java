package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.SessionTurn;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
