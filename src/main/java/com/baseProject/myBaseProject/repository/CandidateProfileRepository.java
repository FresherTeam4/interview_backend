package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.CandidateProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, Long> {

    @EntityGraph(attributePaths = "cvDocument")
    List<CandidateProfile> findByUserIdAndCvDocumentActiveTrueOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "cvDocument")
    Optional<CandidateProfile> findByIdAndUserIdAndCvDocumentActiveTrue(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT profile
            FROM CandidateProfile profile
            JOIN FETCH profile.cvDocument document
            WHERE profile.id = :profileId
              AND profile.user.id = :userId
              AND document.active = true
            """)
    Optional<CandidateProfile> findActiveOwnedByIdForUpdate(
            @Param("profileId") Long profileId,
            @Param("userId") Long userId);

    Optional<CandidateProfile> findByCvDocumentId(Long cvDocumentId);

    List<CandidateProfile> findByCvDocumentIdIn(Collection<Long> cvDocumentIds);

    boolean existsByCvDocumentId(Long cvDocumentId);
}
