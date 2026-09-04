package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.ReportHighlight;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportHighlightRepository extends JpaRepository<ReportHighlight, Long> {

    List<ReportHighlight> findByReportIdOrderByTypeAscDisplayOrderAsc(Long reportId);
}
