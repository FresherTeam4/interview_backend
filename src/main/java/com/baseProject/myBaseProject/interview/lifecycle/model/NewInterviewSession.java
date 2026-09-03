package com.baseProject.myBaseProject.interview.lifecycle.model;

import com.baseProject.myBaseProject.entity.CandidateProfile;
import com.baseProject.myBaseProject.entity.JobDescription;
import com.baseProject.myBaseProject.entity.RubricVersion;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.InterviewDifficulty;
import com.baseProject.myBaseProject.enums.SessionMode;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record NewInterviewSession(
        UserAccount user,
        CandidateProfile profile,
        JobDescription jobDescription,
        RubricVersion rubricVersion,
        String creationKey,
        String creationRequestHash,
        InterviewDifficulty difficulty,
        SessionMode mode,
        String languageCode,
        UUID generationSeed,
        String snapshotSchemaVersion,
        JsonNode profileSnapshot) {
}
