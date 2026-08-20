package com.baseProject.myBaseProject.repository;

import java.util.List;

import com.baseProject.myBaseProject.entity.QuestionImportRow;
import com.baseProject.myBaseProject.enums.QuestionImportRowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionImportRowRepository extends JpaRepository<QuestionImportRow, Long> {
    List<QuestionImportRow> findAllByImportJobIdOrderByRowNumberAsc(Long importJobId);

    Page<QuestionImportRow> findAllByImportJobId(Long importJobId, Pageable pageable);

    Page<QuestionImportRow> findAllByImportJobIdAndStatus(
            Long importJobId,
            QuestionImportRowStatus status,
            Pageable pageable
    );

    long countByImportJobIdAndStatus(Long importJobId, QuestionImportRowStatus status);

    @Query("select row.id from QuestionImportRow row "
            + "where row.importJob.id = :jobId and row.status = :status order by row.rowNumber")
    List<Long> findIdsByJobIdAndStatus(
            @Param("jobId") Long jobId,
            @Param("status") QuestionImportRowStatus status
    );
}
