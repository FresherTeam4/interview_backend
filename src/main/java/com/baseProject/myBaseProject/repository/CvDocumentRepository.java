package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.CvDocument;
import com.baseProject.myBaseProject.enums.CvDocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CvDocumentRepository extends JpaRepository<CvDocument, Long> {

    List<CvDocument> findByUserIdAndActiveTrueOrderByUploadedAtDesc(Long userId);

    Optional<CvDocument> findByIdAndUserIdAndActiveTrue(Long id, Long userId);

    Optional<CvDocument> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndActiveTrue(Long userId);

    Optional<CvDocument> findFirstByUserIdAndChecksumSha256AndStatusOrderByUploadedAtDesc(
            Long userId, String checksumSha256, CvDocumentStatus status);

    List<CvDocument> findByStatus(CvDocumentStatus status);
}
