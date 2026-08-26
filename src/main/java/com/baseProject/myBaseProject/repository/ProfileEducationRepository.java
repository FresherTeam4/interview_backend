package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.ProfileEducation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProfileEducationRepository extends JpaRepository<ProfileEducation, Long> {

    List<ProfileEducation> findByProfileIdOrderByDisplayOrderAsc(Long profileId);

    Optional<ProfileEducation> findByIdAndProfileId(Long id, Long profileId);

    /**
     * Đếm cho một dòng của {@code GET /api/profiles}.
     *
     * <p>Đếm bằng {@code COUNT(*)} thay vì tải danh sách rồi {@code size()}: danh sách hồ sơ chỉ
     * cần con số, tải cả 100 hàng con về rồi bỏ đi là tốn băng thông DB không đổi lấy gì.
     */
    long countByProfileId(Long profileId);

    /** Clears the list in one statement, for a replace-all profile edit. */
    @Modifying(flushAutomatically = true)
    @Query("delete from ProfileEducation e where e.profile.id = :profileId")
    int deleteAllByProfileId(@Param("profileId") Long profileId);
}
