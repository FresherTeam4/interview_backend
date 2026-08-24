package com.baseProject.myBaseProject.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.baseProject.myBaseProject.entity.CandidateProfile;

public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, Long> {

    /** uq_candidate_profiles_user: mỗi user chỉ có một hồ sơ. */
    Optional<CandidateProfile> findByUserId(Long userId);
}
