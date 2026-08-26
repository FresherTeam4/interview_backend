package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.ProfileProject;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProfileProjectRepository extends JpaRepository<ProfileProject, Long> {

    /** The question generator reads this list to build deep-dive questions. */
    List<ProfileProject> findByProfileIdOrderByDisplayOrderAsc(Long profileId);

    Optional<ProfileProject> findByIdAndProfileId(Long id, Long profileId);

    /** Đếm cho một dòng của {@code GET /api/profiles}. */
    long countByProfileId(Long profileId);

    /** Clears the list in one statement, for a replace-all profile edit. */
    @Modifying(flushAutomatically = true)
    @Query("delete from ProfileProject p where p.profile.id = :profileId")
    int deleteAllByProfileId(@Param("profileId") Long profileId);
}
