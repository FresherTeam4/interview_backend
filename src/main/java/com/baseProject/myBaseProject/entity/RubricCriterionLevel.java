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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

@Entity
@Table(
        name = "rubric_criterion_levels",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_rubric_criterion_levels_criterion_level",
                        columnNames = {"criterion_id", "level_no"})
        }
)
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RubricCriterionLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criterion_id", nullable = false, updatable = false)
    private RubricCriterion criterion;

    @Column(name = "level_no", nullable = false, updatable = false)
    private short levelNo;

    @Column(nullable = false, length = 80, updatable = false)
    private String label;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String descriptor;

    @Column(name = "score_value", nullable = false, precision = 4, scale = 2, updatable = false)
    private BigDecimal scoreValue;
}
