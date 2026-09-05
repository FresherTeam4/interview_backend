package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.JobDescriptionAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobDescriptionAnalysisResultRepository
        extends JpaRepository<JobDescriptionAnalysisResult, Long> {
    Optional<JobDescriptionAnalysisResult> findByJobDescriptionId(Long jobDescriptionId);

    boolean existsByJobDescriptionId(Long jobDescriptionId);
}
