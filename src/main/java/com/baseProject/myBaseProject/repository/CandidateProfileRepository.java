package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.CandidateProfile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, Long> {

    Optional<CandidateProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    /**
     * The gate in front of every interview session: the user must have pressed
     * "Information is correct" first.
     */
    boolean existsByUserIdAndConfirmedAtIsNotNull(Long userId);

    /**
     * A CV backing a profile cannot be deleted — the foreign key is RESTRICT.
     * Check here to fail with a readable message instead of a constraint violation.
     */
    boolean existsByCvDocumentId(Long cvDocumentId);
}
