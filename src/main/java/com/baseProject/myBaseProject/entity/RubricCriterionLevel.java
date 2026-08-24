package com.baseProject.myBaseProject.entity;

import java.math.BigDecimal;

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
 * One rung of a criterion scale. Having the wording in the database is what lets a score
 * be explained the same way to every user.
 */
@Entity
@Table(
        name = "rubric_criterion_levels",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_rubric_criterion_levels_level",
                columnNames = {"criterion_id", "level_no"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RubricCriterionLevel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criterion_id", nullable = false)
    private RubricCriterion criterion;

    @Column(name = "level_no", nullable = false)
    private Integer levelNo;

    @Column(nullable = false, length = 80)
    private String label;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descriptor;

    @Column(name = "score_value", nullable = false, precision = 4, scale = 2)
    private BigDecimal scoreValue;
}
