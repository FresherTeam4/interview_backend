package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.CvParseResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CvParseResultRepository extends JpaRepository<CvParseResult, Long> {

    Optional<CvParseResult> findByCvDocumentId(Long cvDocumentId);

    boolean existsByCvDocumentId(Long cvDocumentId);
}
