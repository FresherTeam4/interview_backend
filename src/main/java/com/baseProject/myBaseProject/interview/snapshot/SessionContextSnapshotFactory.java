package com.baseProject.myBaseProject.interview.snapshot;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionContextSnapshot;
import com.baseProject.myBaseProject.jd.JobDescriptionFingerprint;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class SessionContextSnapshotFactory {

    private final ProfileSnapshotPolicy profileSnapshotPolicy;
    private final JobDescriptionFingerprint jobDescriptionFingerprint;

    /** Kiểm tra context và tạo snapshot bất biến dùng xuyên suốt một session. */
    public SessionContextSnapshot create(
            InterviewSession session,
            String schemaVersion,
            JsonNode profileJson,
            String jobDescriptionText,
            Instant now) {
        profileSnapshotPolicy.validate(profileJson);
        String jobDescriptionHash = jobDescriptionFingerprint.create(jobDescriptionText);
        return SessionContextSnapshot.create(
                session,
                schemaVersion,
                profileJson,
                jobDescriptionText,
                jobDescriptionHash,
                now);
    }
}
