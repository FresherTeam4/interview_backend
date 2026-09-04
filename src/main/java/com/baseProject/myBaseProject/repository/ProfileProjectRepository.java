package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.ProfileProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProfileProjectRepository extends JpaRepository<ProfileProject, Long> {

    List<ProfileProject> findByProfileIdOrderByDisplayOrderAsc(Long profileId);

    Optional<ProfileProject> findByIdAndProfileId(Long id, Long profileId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ProfileProject p WHERE p.profile.id = :profileId")
    int deleteAllByProfileId(@Param("profileId") Long profileId);
}
