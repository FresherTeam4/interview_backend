package com.baseProject.myBaseProject.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One skill of a {@link CandidateProfile}. Generated questions reference this row
 * directly, which is why skills are rows rather than a JSON array.
 *
 * <p>{@code uq_profile_skills_profile_name} runs on a case-insensitive collation, so
 * "React" and "react" collide in the database. It cannot catch "ReactJS" — normalise
 * the name in the service layer before writing.
 */
@Entity
@Table(
        name = "profile_skills",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_profile_skills_profile_name",
                        columnNames = {"profile_id", "name"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileSkill {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private CandidateProfile profile;

    /** Java, Spring Boot, React, PostgreSQL... */
    @Column(nullable = false, length = 80)
    private String name;

    /** LANGUAGE | FRAMEWORK | DATABASE | TOOL | SOFT — free-form, not constrained by the database. */
    @Column(length = 50)
    private String category;

    /** Maps {@code is_user_edited}. */
    @Column(name = "is_user_edited", nullable = false)
    @Builder.Default
    private boolean userEdited = false;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private short displayOrder = 0;
}
