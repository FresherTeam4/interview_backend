package com.baseProject.myBaseProject.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.baseProject.myBaseProject.enums.ProfileSource;
import com.baseProject.myBaseProject.enums.SeniorityLevel;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * The confirmed picture of the candidate. Built from a CV parse, then editable by the user;
 * this - not the raw CV - is what question generation reads.
 */
@Entity
@Table(
        name = "candidate_profiles",
        uniqueConstraints = @UniqueConstraint(name = "uq_candidate_profiles_user", columnNames = "user_id")
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

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cv_document_id", nullable = false)
    private CvDocument cvDocument;

    @Column(length = 255)
    private String headline;

    @Column(name = "years_experience", precision = 3, scale = 1)
    private BigDecimal yearsExperience;

    @Column(name = "target_position", length = 150)
    private String targetPosition;

    @Enumerated(EnumType.STRING)
    @Column(name = "seniority_level", length = 30)
    private SeniorityLevel seniorityLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProfileSource source;

    /** Set when the user accepts the profile; an unconfirmed profile must not start a session. */
    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * DB tự quản lý qua DEFAULT CURRENT_TIMESTAMP(6) và ON UPDATE CURRENT_TIMESTAMP(6).
     * {@code @Generated} báo Hibernate đọc lại giá trị sau INSERT/UPDATE để response
     * trả về đúng thời điểm cập nhật, không bị trễ một nhịp.
     */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
