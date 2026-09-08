package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.ProfileEducation;
import com.baseProject.myBaseProject.repository.projection.ProfileItemCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProfileEducationRepository extends JpaRepository<ProfileEducation, Long> {

    List<ProfileEducation> findByProfileIdOrderByDisplayOrderAsc(Long profileId);

    @Query("""
            SELECT education.profile.id AS profileId, COUNT(education.id) AS itemCount
            FROM ProfileEducation education
            WHERE education.profile.id IN :profileIds
            GROUP BY education.profile.id
            """)
    List<ProfileItemCount> countGroupedByProfileIds(
            @Param("profileIds") Collection<Long> profileIds);

}
