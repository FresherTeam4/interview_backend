package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.InterviewAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InterviewAssessmentRepository extends JpaRepository<InterviewAssessment, Long> {
    Optional<InterviewAssessment> findBySessionId(Long sessionId);
}
