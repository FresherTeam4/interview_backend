package com.baseProject.myBaseProject.entity;

import java.time.Instant;

import com.baseProject.myBaseProject.enums.QuestionImportRowStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "question_import_rows")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionImportRow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_job_id", nullable = false)
    private QuestionImportJob importJob;

    @Column(name = "csv_row_number", nullable = false)
    private int rowNumber;

    @Column(name = "content_vi", columnDefinition = "MEDIUMTEXT")
    private String contentVi;

    @Column(name = "content_en", columnDefinition = "MEDIUMTEXT")
    private String contentEn;

    @Column(name = "level_value", columnDefinition = "TEXT")
    private String levelValue;

    @Column(name = "question_type_value", columnDefinition = "TEXT")
    private String questionTypeValue;

    @Column(name = "difficulty_value", columnDefinition = "TEXT")
    private String difficultyValue;

    @Column(name = "company_ref", columnDefinition = "TEXT")
    private String companyRef;

    @Column(name = "tech_stack_codes", columnDefinition = "TEXT")
    private String techStackCodes;

    @Column(name = "technology_codes", columnDefinition = "TEXT")
    private String technologyCodes;

    @Column(name = "active_value", columnDefinition = "TEXT")
    private String activeValue;

    @Column(name = "content_fingerprint", length = 64)
    private String contentFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private QuestionImportRowStatus status;

    @Column(name = "error_code", length = 60)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "existing_question_id")
    private Question existingQuestion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_question_id")
    private Question createdQuestion;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
