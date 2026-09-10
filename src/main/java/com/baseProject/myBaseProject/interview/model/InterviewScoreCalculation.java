package com.baseProject.myBaseProject.interview.model;

import java.math.BigDecimal;

public record InterviewScoreCalculation(
        BigDecimal technicalScore,
        BigDecimal communicationScore,
        BigDecimal overallScore,
        BigDecimal coveragePercentage) {
}
