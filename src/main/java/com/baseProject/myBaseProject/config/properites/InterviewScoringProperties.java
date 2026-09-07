package com.baseProject.myBaseProject.config.properites;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@ConfigurationProperties(prefix = "app.interview-scoring")
public record InterviewScoringProperties(
        @NotNull @DecimalMin("0") @DecimalMax("100")
        BigDecimal minimumCoveragePercentage,
        @NotNull @DecimalMin("0") @DecimalMax("1")
        BigDecimal technicalWeight,
        @NotNull @DecimalMin("0") @DecimalMax("1")
        BigDecimal communicationWeight) {

    public InterviewScoringProperties {
        if (technicalWeight != null && communicationWeight != null
                && technicalWeight.add(communicationWeight).compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException(
                    "Interview scoring weights must add up to 1");
        }
    }
}
