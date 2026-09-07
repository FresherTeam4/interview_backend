package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.InterviewFocusAreaResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewFocusAreaResultRepository
        extends JpaRepository<InterviewFocusAreaResult, Long> {
    List<InterviewFocusAreaResult> findByAssessmentIdOrderByFocusAreaDisplayOrderAsc(
            Long assessmentId);
}
