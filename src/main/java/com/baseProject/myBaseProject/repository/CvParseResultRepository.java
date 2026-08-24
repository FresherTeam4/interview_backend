package com.baseProject.myBaseProject.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.baseProject.myBaseProject.entity.CvParseResult;

public interface CvParseResultRepository extends JpaRepository<CvParseResult, Long> {

    Optional<CvParseResult> findByCvDocumentId(Long cvDocumentId);
}
