package com.baseProject.myBaseProject.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.baseProject.myBaseProject.enums.JobDescriptionStatus;

import org.junit.jupiter.api.Test;

import java.time.Instant;

class JobDescriptionTest {

    @Test
    void draftLifecyclePreservesRawSourceAndFirstConfirmation() {
        Instant createdAt = Instant.parse("2026-08-26T01:00:00Z");
        Instant updatedAt = Instant.parse("2026-08-26T02:00:00Z");
        Instant confirmedAt = Instant.parse("2026-08-26T03:00:00Z");
        JobDescription jobDescription = JobDescription.createText(
                UserAccount.builder().build(),
                "Original title",
                "a".repeat(64),
                "immutable raw",
                createdAt);

        jobDescription.updateDraft("Updated title", "edited transcript", updatedAt);
        jobDescription.confirm(confirmedAt);
        jobDescription.confirm(confirmedAt.plusSeconds(60));

        assertThat(jobDescription.getRawText()).isEqualTo("immutable raw");
        assertThat(jobDescription.getChecksumSha256()).isEqualTo("a".repeat(64));
        assertThat(jobDescription.getConfirmedText()).isEqualTo("edited transcript");
        assertThat(jobDescription.getStatus()).isEqualTo(JobDescriptionStatus.READY);
        assertThat(jobDescription.getConfirmedAt()).isEqualTo(confirmedAt);
        assertThat(jobDescription.getUpdatedAt()).isEqualTo(confirmedAt);
    }

    @Test
    void deactivateKeepsRowAndUpdatesActivityTimestamp() {
        Instant deletedAt = Instant.parse("2026-08-26T04:00:00Z");
        JobDescription jobDescription = JobDescription.createText(
                UserAccount.builder().build(),
                "JD",
                "a".repeat(64),
                "immutable raw",
                deletedAt.minusSeconds(60));

        jobDescription.deactivate(deletedAt);

        assertThat(jobDescription.isActive()).isFalse();
        assertThat(jobDescription.getUpdatedAt()).isEqualTo(deletedAt);
    }
}
