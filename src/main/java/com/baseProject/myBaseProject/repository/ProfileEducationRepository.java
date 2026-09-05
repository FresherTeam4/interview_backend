package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.ProfileEducation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

import com.baseProject.myBaseProject.repository.projection.ProfileItemCount;

public interface ProfileEducationRepository extends JpaRepository<ProfileEducation, Long> {

    List<ProfileEducation> findByProfileIdOrderByDisplayOrderAsc(Long profileId);

    Optional<ProfileEducation> findByIdAndProfileId(Long id, Long profileId);

    @Query("""
            SELECT education.profile.id AS profileId, COUNT(education.id) AS itemCount
            FROM ProfileEducation education
            WHERE education.profile.id IN :profileIds
            GROUP BY education.profile.id
            """)
    List<ProfileItemCount> countGroupedByProfileIds(
            @Param("profileIds") Collection<Long> profileIds);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ProfileEducation e WHERE e.profile.id = :profileId")
    int deleteAllByProfileId(@Param("profileId") Long profileId);
}
