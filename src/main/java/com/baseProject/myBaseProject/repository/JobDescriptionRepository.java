package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.JobDescription;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface JobDescriptionRepository extends JpaRepository<JobDescription, Long> {

    Page<JobDescription> findByUserIdAndActiveTrue(Long userId, Pageable pageable);

    Optional<JobDescription> findByIdAndUserIdAndActiveTrue(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select jd
            from JobDescription jd
            where jd.id = :id
              and jd.user.id = :userId
              and jd.active = true
            """)
    Optional<JobDescription> findActiveOwnedByIdForUpdate(
            @Param("id") Long id,
            @Param("userId") Long userId);

    long countByUserIdAndActiveTrue(Long userId);
}
