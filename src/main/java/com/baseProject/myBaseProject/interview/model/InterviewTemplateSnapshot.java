package com.baseProject.myBaseProject.interview.model;

import com.baseProject.myBaseProject.dto.ai.JobAnalysis;

public record InterviewTemplateSnapshot(
        String snapshotSchemaVersion,
        Long templateId,
        long templateVersion,
        String title,
        String jobTitle,
        String targetSeniority,
        String contentSchemaVersion,
        JobAnalysis analysis,
        String jobDescriptionText) {
}
