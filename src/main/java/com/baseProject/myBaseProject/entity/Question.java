package com.baseProject.myBaseProject.entity;

import java.time.Instant;

import com.baseProject.myBaseProject.enums.QuestionDifficulty;
import com.baseProject.myBaseProject.enums.QuestionLevel;
import com.baseProject.myBaseProject.enums.QuestionType;

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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_vi", nullable = false, columnDefinition = "TEXT")
    private String contentVi;

    @Column(name = "content_en", columnDefinition = "TEXT")
    private String contentEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tech_stack_id")
    private TechStack techStack;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionLevel level;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false)
    private QuestionType questionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private QuestionDifficulty difficulty = QuestionDifficulty.MEDIUM;

    @Column(name = "company_ref", length = 150)
    private String companyRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserAccount createdBy;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private int version = 0;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
