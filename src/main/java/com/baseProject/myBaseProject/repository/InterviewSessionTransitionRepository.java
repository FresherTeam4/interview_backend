package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.InterviewSessionTransition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewSessionTransitionRepository
        extends JpaRepository<InterviewSessionTransition, Long> {
}
