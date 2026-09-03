package com.baseProject.myBaseProject.config.properites;

import static com.baseProject.myBaseProject.constant.InterviewConstraints.MAX_BASE_QUESTION_COUNT;
import static com.baseProject.myBaseProject.constant.InterviewConstraints.MIN_BASE_QUESTION_COUNT;

import com.baseProject.myBaseProject.enums.InterviewDifficulty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.interview.questions")
public record InterviewQuestionProperties(
        @Min(MIN_BASE_QUESTION_COUNT) @Max(MAX_BASE_QUESTION_COUNT) int easy,
        @Min(MIN_BASE_QUESTION_COUNT) @Max(MAX_BASE_QUESTION_COUNT) int medium,
        @Min(MIN_BASE_QUESTION_COUNT) @Max(MAX_BASE_QUESTION_COUNT) int hard) {

    public int countFor(InterviewDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> easy;
            case MEDIUM -> medium;
            case HARD -> hard;
        };
    }
}
