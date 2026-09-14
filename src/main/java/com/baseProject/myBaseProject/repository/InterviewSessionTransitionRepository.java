package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.InterviewSessionTransition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewSessionTransitionRepository
        extends JpaRepository<InterviewSessionTransition, Long> {
    List<InterviewSessionTransition> findBySessionIdOrderByOccurredAtAsc(Long sessionId);
}
