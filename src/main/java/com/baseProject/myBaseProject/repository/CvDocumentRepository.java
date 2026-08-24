package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.CvDocument;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CvDocumentRepository extends JpaRepository<CvDocument, Long> {

    /** The one CV a user is currently interviewing against. */
    Optional<CvDocument> findByUserIdAndActiveTrue(Long userId);

    /** Ownership-scoped fetch, so a stray id cannot read someone else's CV. */
    Optional<CvDocument> findByIdAndUserId(Long id, Long userId);

    List<CvDocument> findByUserIdOrderByUploadedAtDesc(Long userId);

    /**
     * Dedup check before parsing: the same user re-uploading identical bytes can reuse
     * the existing parse result instead of paying for another API call.
     */
    Optional<CvDocument> findFirstByUserIdAndChecksumSha256OrderByUploadedAtDesc(
            Long userId, String checksumSha256);

    /**
     * Step one of "upload a new CV": retire the current one. Deliberately an update and
     * not a delete — interview sessions already run against that CV still point at it.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update CvDocument d
               set d.active = false
             where d.user.id = :userId
               and d.active = true
            """)
    int deactivateAllByUserId(@Param("userId") Long userId);
}
