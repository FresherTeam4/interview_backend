package com.baseProject.myBaseProject.interview.lifecycle;

import com.baseProject.myBaseProject.interview.lifecycle.model.NewInterviewSession;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionContextSnapshot;
import com.baseProject.myBaseProject.enums.JobDescriptionStatus;
import com.baseProject.myBaseProject.interview.snapshot.SessionContextSnapshotFactory;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class InterviewSessionFactory {

    private static final String MVP_LANGUAGE_CODE = "vi";
    private static final int CREATION_KEY_MAX_LENGTH = 128;
    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");

    private final SessionContextSnapshotFactory snapshotFactory;

    public void validate(NewInterviewSession command) {
        Objects.requireNonNull(command);
        Objects.requireNonNull(command.user());
        Objects.requireNonNull(command.profile());
        Objects.requireNonNull(command.jobDescription());
        Objects.requireNonNull(command.rubricVersion());
        Objects.requireNonNull(command.difficulty());
        Objects.requireNonNull(command.mode());
        Objects.requireNonNull(command.generationSeed());
        if (command.user().getId() == null
                || !Objects.equals(command.profile().getUser().getId(), command.user().getId())
                || !Objects.equals(command.jobDescription().getUser().getId(), command.user().getId())) {
            throw new IllegalArgumentException("Session inputs must belong to the same persisted user");
        }
        if (!command.profile().isConfirmed()
                || !command.jobDescription().isActive()
                || command.jobDescription().getStatus() != JobDescriptionStatus.READY
                || command.rubricVersion().getPublishedAt() == null) {
            throw new IllegalArgumentException("Session inputs are not ready");
        }
        if (command.creationKey() == null
                || command.creationKey().isBlank()
                || command.creationKey().strip().length() > CREATION_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("Creation key must contain 1-128 characters");
        }
        if (command.creationRequestHash() == null
                || !SHA_256.matcher(command.creationRequestHash()).matches()) {
            throw new IllegalArgumentException("Creation request hash must be lowercase SHA-256");
        }
        if (!MVP_LANGUAGE_CODE.equals(command.languageCode())) {
            throw new IllegalArgumentException("Only language code vi is supported in the MVP");
        }
    }

    public InterviewSession createSession(NewInterviewSession command, Instant now) {
        String creationKey = command.creationKey().strip();
        return InterviewSession.create(
                command.user(),
                command.profile(),
                command.jobDescription(),
                command.rubricVersion(),
                creationKey,
                command.creationRequestHash(),
                command.difficulty(),
                command.mode(),
                command.languageCode(),
                command.generationSeed(),
                now);
    }

    public SessionContextSnapshot createSnapshot(
            InterviewSession session,
            NewInterviewSession command,
            Instant now) {
        return snapshotFactory.create(
                session,
                command.snapshotSchemaVersion(),
                command.profileSnapshot(),
                command.jobDescription().getConfirmedText(),
                now);
    }
}
