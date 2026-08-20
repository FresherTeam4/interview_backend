package com.baseProject.myBaseProject.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.baseProject.myBaseProject.entity.QuestionImportJob;
import com.baseProject.myBaseProject.enums.QuestionImportStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionImportJobRepository extends JpaRepository<QuestionImportJob, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from QuestionImportJob job where job.id = :id")
    Optional<QuestionImportJob> findByIdForUpdate(@Param("id") Long id);

    List<QuestionImportJob> findAllByStatusIn(Collection<QuestionImportStatus> statuses);
}
