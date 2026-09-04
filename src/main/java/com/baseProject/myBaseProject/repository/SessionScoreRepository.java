package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.SessionScore;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SessionScoreRepository extends JpaRepository<SessionScore, Long> {

    @Query("""
            select score
            from SessionScore score
            join fetch score.criterion criterion
            where score.session.id = :sessionId
            order by criterion.displayOrder, score.id
            """)
    List<SessionScore> findBySessionIdInDisplayOrder(@Param("sessionId") Long sessionId);

    boolean existsBySessionId(Long sessionId);
}
