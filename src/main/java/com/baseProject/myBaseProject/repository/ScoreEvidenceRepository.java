package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.ScoreEvidence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ScoreEvidenceRepository extends JpaRepository<ScoreEvidence, Long> {

    @Query("""
            select evidence
            from ScoreEvidence evidence
            join fetch evidence.turn
            where evidence.sessionScore.id in :scoreIds
            order by evidence.sessionScore.id, evidence.id
            """)
    List<ScoreEvidence> findByScoreIds(@Param("scoreIds") Collection<Long> scoreIds);
}
