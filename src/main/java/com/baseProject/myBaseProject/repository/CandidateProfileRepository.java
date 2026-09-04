package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.CandidateProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, Long> {

    List<CandidateProfile> findByUserIdAndCvDocumentActiveTrueOrderByCreatedAtDesc(Long userId);

    Optional<CandidateProfile> findByIdAndUserIdAndCvDocumentActiveTrue(Long id, Long userId);

    Optional<CandidateProfile> findByCvDocumentId(Long cvDocumentId);

    List<CandidateProfile> findByCvDocumentIdIn(Collection<Long> cvDocumentIds);

    boolean existsByCvDocumentId(Long cvDocumentId);
}
