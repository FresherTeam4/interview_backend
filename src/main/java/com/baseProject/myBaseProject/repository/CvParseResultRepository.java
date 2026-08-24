package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.CvParseResult;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CvParseResultRepository extends JpaRepository<CvParseResult, Long> {

    Optional<CvParseResult> findByCvDocumentId(Long cvDocumentId);

    /** A document is parsed once; check this before spending another API call. */
    boolean existsByCvDocumentId(Long cvDocumentId);
}
