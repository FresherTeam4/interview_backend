package com.baseProject.myBaseProject.entity;

import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "interview_focus_area_results",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_interview_focus_result_area",
                columnNames = {"assessment_id", "focus_area_id"}),
        indexes = @Index(
                name = "idx_interview_focus_result_assessment",
                columnList = "assessment_id"))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewFocusAreaResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false, updatable = false)
    private InterviewAssessment assessment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "focus_area_id", nullable = false, updatable = false)
    private InterviewFocusArea focusArea;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "evidence_status", nullable = false, length = 20)
    private InterviewEvidenceStatus evidenceStatus;

    @Column(name = "evidence_turn_ids_json", nullable = false, columnDefinition = "JSON")
    private String evidenceTurnIdsJson;
}
