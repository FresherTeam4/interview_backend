package com.baseProject.myBaseProject.config.properites;

import com.baseProject.myBaseProject.enums.InterviewDifficulty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.interview.questions")
public record InterviewQuestionProperties(
        @Min(5) @Max(7) int easy,
        @Min(5) @Max(7) int medium,
        @Min(5) @Max(7) int hard) {

    public int countFor(InterviewDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> easy;
            case MEDIUM -> medium;
            case HARD -> hard;
        };
    }
}
