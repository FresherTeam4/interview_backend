package com.baseProject.myBaseProject.interview.model;

import com.baseProject.myBaseProject.enums.InterviewAssessmentConfidence;

import java.math.BigDecimal;

public record InterviewScoreCalculation(
        BigDecimal technicalScore,
        BigDecimal communicationScore,
        BigDecimal overallScore,
        BigDecimal coveragePercentage,
        InterviewAssessmentConfidence confidence) {
}
