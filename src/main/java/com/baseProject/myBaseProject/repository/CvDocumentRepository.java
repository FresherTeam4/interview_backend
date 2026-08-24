package com.baseProject.myBaseProject.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.baseProject.myBaseProject.entity.CvDocument;

public interface CvDocumentRepository extends JpaRepository<CvDocument, Long> {

    /** Luôn lọc theo userId để một người không đọc được CV của người khác. */
    Optional<CvDocument> findByIdAndUserId(Long id, Long userId);

    List<CvDocument> findByUserIdOrderByUploadedAtDesc(Long userId);

    Optional<CvDocument> findByUserIdAndActiveTrue(Long userId);

    /** Mỗi user chỉ có một CV đang active, nên upload mới sẽ tắt cờ của các CV cũ. */
    @Modifying
    @Query("update CvDocument d set d.active = false where d.user.id = :userId and d.active = true")
    int deactivateAllByUserId(@Param("userId") Long userId);
}
