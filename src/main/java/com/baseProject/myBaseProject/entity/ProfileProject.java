package com.baseProject.myBaseProject.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * One project of a {@link CandidateProfile}. Generated questions reference this row,
 * and {@link #description} is the main raw material the question generator digs into —
 * the most valuable column in the whole CV group.
 */
@Entity
@Table(
        name = "profile_projects",
        indexes = {
                @Index(name = "idx_profile_projects_profile_id", columnList = "profile_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileProject {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private CandidateProfile profile;

    @Column(nullable = false, length = 255)
    private String name;

    /** Main raw material for deep-dive question generation. */
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "role_in_project", length = 150)
    private String roleInProject;

    /** Comma-separated list. A join table is not needed for the MVP. */
    @Column(name = "tech_stack", columnDefinition = "TEXT")
    private String techStack;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** Maps {@code is_user_edited}. */
    @Column(name = "is_user_edited", nullable = false)
    @Builder.Default
    private boolean userEdited = false;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private short displayOrder = 0;
}
