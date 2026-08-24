package com.baseProject.myBaseProject.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.baseProject.myBaseProject.entity.ProfileProject;

public interface ProfileProjectRepository extends JpaRepository<ProfileProject, Long> {

    List<ProfileProject> findByProfileIdOrderByDisplayOrderAsc(Long profileId);

    /** Bulk delete để câu xoá chạy ngay, không bị Hibernate dồn xuống sau các câu insert. */
    @Modifying
    @Query("delete from ProfileProject p where p.profile.id = :profileId")
    int deleteByProfileId(@Param("profileId") Long profileId);
}
