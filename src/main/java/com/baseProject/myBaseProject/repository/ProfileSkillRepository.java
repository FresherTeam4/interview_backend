package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.ProfileSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProfileSkillRepository extends JpaRepository<ProfileSkill, Long> {

    List<ProfileSkill> findByProfileIdOrderByDisplayOrderAsc(Long profileId);

    Optional<ProfileSkill> findByProfileIdAndName(Long profileId, String name);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ProfileSkill s WHERE s.profile.id = :profileId")
    int deleteAllByProfileId(@Param("profileId") Long profileId);
}
