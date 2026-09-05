package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.ProfileProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

import com.baseProject.myBaseProject.repository.projection.ProfileItemCount;

public interface ProfileProjectRepository extends JpaRepository<ProfileProject, Long> {

    List<ProfileProject> findByProfileIdOrderByDisplayOrderAsc(Long profileId);

    Optional<ProfileProject> findByIdAndProfileId(Long id, Long profileId);

    @Query("""
            SELECT project.profile.id AS profileId, COUNT(project.id) AS itemCount
            FROM ProfileProject project
            WHERE project.profile.id IN :profileIds
            GROUP BY project.profile.id
            """)
    List<ProfileItemCount> countGroupedByProfileIds(
            @Param("profileIds") Collection<Long> profileIds);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ProfileProject p WHERE p.profile.id = :profileId")
    int deleteAllByProfileId(@Param("profileId") Long profileId);
}
