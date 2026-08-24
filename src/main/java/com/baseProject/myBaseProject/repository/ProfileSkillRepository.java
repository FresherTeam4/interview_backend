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

    /**
     * Duplicate check before insert. The lookup is case-insensitive because of the
     * column collation, matching {@code uq_profile_skills_profile_name}.
     */
    Optional<ProfileSkill> findByProfileIdAndName(Long profileId, String name);

    /** Clears the list in one statement, for a replace-all profile edit. */
    @Modifying(flushAutomatically = true)
    @Query("delete from ProfileSkill s where s.profile.id = :profileId")
    int deleteAllByProfileId(@Param("profileId") Long profileId);
}
