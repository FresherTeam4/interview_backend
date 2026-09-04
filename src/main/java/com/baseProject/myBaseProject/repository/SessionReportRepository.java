package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.SessionReport;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SessionReportRepository extends JpaRepository<SessionReport, Long> {

    boolean existsBySessionId(Long sessionId);

    @Query("""
            select report
            from SessionReport report
            where report.session.id = :sessionId
              and report.session.user.id = :userId
            """)
    Optional<SessionReport> findOwned(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId);
}
