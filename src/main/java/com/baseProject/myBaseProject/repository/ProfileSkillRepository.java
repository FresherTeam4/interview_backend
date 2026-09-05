package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.ProfileSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

import com.baseProject.myBaseProject.repository.projection.ProfileItemCount;

public interface ProfileSkillRepository extends JpaRepository<ProfileSkill, Long> {

    List<ProfileSkill> findByProfileIdOrderByDisplayOrderAsc(Long profileId);

    Optional<ProfileSkill> findByProfileIdAndName(Long profileId, String name);

    @Query("""
            SELECT skill.profile.id AS profileId, COUNT(skill.id) AS itemCount
            FROM ProfileSkill skill
            WHERE skill.profile.id IN :profileIds
            GROUP BY skill.profile.id
            """)
    List<ProfileItemCount> countGroupedByProfileIds(
            @Param("profileIds") Collection<Long> profileIds);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ProfileSkill s WHERE s.profile.id = :profileId")
    int deleteAllByProfileId(@Param("profileId") Long profileId);
}
