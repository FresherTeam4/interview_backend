package com.baseProject.myBaseProject.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.baseProject.myBaseProject.entity.ProfileSkill;

public interface ProfileSkillRepository extends JpaRepository<ProfileSkill, Long> {

    List<ProfileSkill> findByProfileIdOrderByDisplayOrderAsc(Long profileId);

    /** Bulk delete để câu xoá chạy ngay, không bị Hibernate dồn xuống sau các câu insert. */
    @Modifying
    @Query("delete from ProfileSkill s where s.profile.id = :profileId")
    int deleteByProfileId(@Param("profileId") Long profileId);
}
