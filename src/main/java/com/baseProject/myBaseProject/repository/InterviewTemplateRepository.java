package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.InterviewTemplate;
import com.baseProject.myBaseProject.enums.JobDescriptionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InterviewTemplateRepository extends JpaRepository<InterviewTemplate, Long> {
    Optional<InterviewTemplate> findByIdAndOwnerId(Long id, Long ownerId);

    Optional<InterviewTemplate> findBySourceJobDescriptionId(Long jobDescriptionId);

    @Query("""
            SELECT t FROM InterviewTemplate t JOIN FETCH t.sourceJobDescription d
            WHERE t.owner.id = :ownerId
                AND d.checksumSha256 = :checksum
                AND d.status = :status
                AND t.confirmedAt IS NULL
                AND t.archivedAt IS NULL
            ORDER BY d.uploadedAt DESC
            """)
    List<InterviewTemplate> findReusable(
            @Param("ownerId") Long ownerId,
            @Param("checksum") String checksum,
            @Param("status") JobDescriptionStatus status,
            Pageable pageable);

    List<InterviewTemplate> findBySourceJobDescriptionIdIn(Collection<Long> jobDescriptionIds);

    Page<InterviewTemplate> findByOwnerId(Long ownerId, Pageable pageable);

    Page<InterviewTemplate> findByPublishedAtIsNotNullAndArchivedAtIsNull(Pageable pageable);

    Optional<InterviewTemplate> findByIdAndPublishedAtIsNotNullAndArchivedAtIsNull(Long id);

    @Query("""
            SELECT template
            FROM InterviewTemplate template
            JOIN FETCH template.sourceJobDescription
            WHERE template.id = :id
              AND (template.owner.id = :userId
                   OR (template.publishedAt IS NOT NULL AND template.archivedAt IS NULL))
            """)
    Optional<InterviewTemplate> findAccessibleForSession(
            @Param("id") Long id,
            @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM InterviewTemplate t WHERE t.id = :id AND t.owner.id = :ownerId")
    Optional<InterviewTemplate> findOwnedForUpdate(@Param("id") Long id,
                                                   @Param("ownerId") Long ownerId);
}
