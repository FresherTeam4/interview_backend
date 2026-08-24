package com.baseProject.myBaseProject.entity;

import com.baseProject.myBaseProject.enums.ProfileSource;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The editable working copy of a parsed CV — one per {@link CvDocument}, and the only
 * place the user touches. {@link CvParseResult} keeps the untouched AI output alongside
 * it, which is why no "prefer the edited version" flag is needed: the edited version is
 * the only version anything downstream reads.
 *
 * <p>A user owns as many profiles as they have CVs and picks which one an interview runs
 * against, so uploading a new CV never disturbs an older profile's manual edits. The
 * one-per-CV rule is the database's job: {@code uq_candidate_profiles_cv_document}.
 *
 * <p>Child rows live in {@link ProfileEducation}, {@link ProfileSkill} and
 * {@link ProfileProject} rather than a JSON blob, because generated questions
 * carry a foreign key back to the exact project or skill they dig into.
 */
@Entity
@Table(
        name = "candidate_profiles",
        indexes = {
                @Index(name = "idx_candidate_profiles_user_id", columnList = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    /**
     * Which CV this profile was built from. Unique, so a CV never grows a second
     * profile; and the foreign key is RESTRICT, so that CV cannot be deleted while
     * this row points at it — which is why removing a CV is a soft delete.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cv_document_id", nullable = false, unique = true)
    private CvDocument cvDocument;

    @Column(length = 255)
    private String headline;

    @Column(name = "years_experience", precision = 3, scale = 1)
    private BigDecimal yearsExperience;

    @Column(name = "target_position", length = 150)
    private String targetPosition;

    @Column(name = "seniority_level", length = 30)
    private String seniorityLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ProfileSource source = ProfileSource.AUTO_PARSED;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public boolean isConfirmed() {
        return confirmedAt != null;
    }
}
