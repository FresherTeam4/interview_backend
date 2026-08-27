package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.Rubric;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RubricRepository extends JpaRepository<Rubric, Long> {

    /**
     * Resolves the current version through the rubric pointer and initializes its complete scoring
     * graph. Both child associations are sets so Hibernate can fetch them together safely.
     */
    @Query("""
            select distinct rubric
            from Rubric rubric
            join fetch rubric.currentVersion currentVersion
            left join fetch currentVersion.criteria criterion
            left join fetch criterion.levels
            where rubric.code = :code
              and currentVersion.rubric = rubric
            """)
    Optional<Rubric> findByCodeWithCurrentVersion(@Param("code") String code);
}
