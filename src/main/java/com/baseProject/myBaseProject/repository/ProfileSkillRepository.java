package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.ProfileSkill;
import com.baseProject.myBaseProject.repository.projection.ProfileItemCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProfileSkillRepository extends JpaRepository<ProfileSkill, Long> {

    List<ProfileSkill> findByProfileIdOrderByDisplayOrderAsc(Long profileId);

    @Query("""
            SELECT skill.profile.id AS profileId, COUNT(skill.id) AS itemCount
            FROM ProfileSkill skill
            WHERE skill.profile.id IN :profileIds
            GROUP BY skill.profile.id
            """)
    List<ProfileItemCount> countGroupedByProfileIds(
            @Param("profileIds") Collection<Long> profileIds);

}
