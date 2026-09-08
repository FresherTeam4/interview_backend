package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.ProfileProject;
import com.baseProject.myBaseProject.repository.projection.ProfileItemCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProfileProjectRepository extends JpaRepository<ProfileProject, Long> {

    List<ProfileProject> findByProfileIdOrderByDisplayOrderAsc(Long profileId);

    @Query("""
            SELECT project.profile.id AS profileId, COUNT(project.id) AS itemCount
            FROM ProfileProject project
            WHERE project.profile.id IN :profileIds
            GROUP BY project.profile.id
            """)
    List<ProfileItemCount> countGroupedByProfileIds(
            @Param("profileIds") Collection<Long> profileIds);

}
