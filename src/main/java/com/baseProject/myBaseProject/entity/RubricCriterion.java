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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(
        name = "rubric_criteria",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_rubric_criteria_version_code",
                        columnNames = {"rubric_version_id", "code"})
        },
        indexes = {
                @Index(
                        name = "idx_rubric_criteria_version_order",
                        columnList = "rubric_version_id, display_order")
        }
)
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RubricCriterion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rubric_version_id", nullable = false, updatable = false)
    private RubricVersion rubricVersion;

    @Column(nullable = false, length = 50, updatable = false)
    private String code;

    @Column(nullable = false, length = 150, updatable = false)
    private String name;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 4, scale = 3, updatable = false)
    private BigDecimal weight;

    @Column(name = "max_score", nullable = false, updatable = false)
    private short maxScore;

    @Column(name = "display_order", nullable = false, updatable = false)
    private short displayOrder;

    @OneToMany(mappedBy = "criterion", fetch = FetchType.LAZY)
    @OrderBy("levelNo ASC, id ASC")
    private Set<RubricCriterionLevel> levels = new LinkedHashSet<>();

    public Set<RubricCriterionLevel> getLevels() {
        return Collections.unmodifiableSet(levels);
    }
}
