package com.baseProject.myBaseProject.entity;

import com.baseProject.myBaseProject.enums.ReportHighlightType;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "report_highlights",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_report_highlights_type_order",
                        columnNames = {"report_id", "type", "display_order"})
        },
        indexes = {
                @Index(
                        name = "idx_report_highlights_report_type_order",
                        columnList = "report_id, type, display_order")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportHighlight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false, updatable = false)
    private SessionReport report;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ReportHighlightType type;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "display_order", nullable = false, updatable = false)
    private short displayOrder;

    public static ReportHighlight create(
            SessionReport report,
            ReportHighlightType type,
            String content,
            short displayOrder) {
        ReportHighlight highlight = new ReportHighlight();
        highlight.report = report;
        highlight.type = type;
        highlight.content = content;
        highlight.displayOrder = displayOrder;
        return highlight;
    }
}
