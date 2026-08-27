package com.baseProject.myBaseProject.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(
        name = "rubric_versions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_rubric_versions_rubric_version",
                        columnNames = {"rubric_id", "version_no"})
        }
)
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RubricVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rubric_id", nullable = false, updatable = false)
    private Rubric rubric;

    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;

    @Column(name = "change_note", length = 500, updatable = false)
    private String changeNote;

    @Column(name = "published_at", updatable = false)
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "rubricVersion", fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC, id ASC")
    private Set<RubricCriterion> criteria = new LinkedHashSet<>();

    public Set<RubricCriterion> getCriteria() {
        return Collections.unmodifiableSet(criteria);
    }
}
