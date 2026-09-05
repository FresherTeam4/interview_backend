package com.baseProject.myBaseProject.dto.session.snapshot;

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
