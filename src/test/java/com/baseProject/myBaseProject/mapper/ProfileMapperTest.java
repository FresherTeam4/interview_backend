package com.baseProject.myBaseProject.mapper;

import com.baseProject.myBaseProject.dto.ai.CvExtractionResult;
import com.baseProject.myBaseProject.entity.CandidateProfile;
import com.baseProject.myBaseProject.entity.CvDocument;
import com.baseProject.myBaseProject.entity.UserAccount;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileMapperTest {

    private final ProfileMapper mapper = new ProfileMapper();

    @Test
    void createsNamedProfileAndKeepsSummaryFromExtraction() {
        CvDocument document = CvDocument.builder()
                .id(7L)
                .user(UserAccount.builder().id(3L).build())
                .originalFilename("backend-cv.pdf")
                .build();
        CvExtractionResult extraction = new CvExtractionResult(
                "Nguyen Van A",
                "candidate@example.com",
                "0900000000",
                "Java Developer",
                new BigDecimal("1.5"),
                "Backend Engineer",
                "JUNIOR",
                "Experienced with Spring Boot and relational databases.",
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null);

        CandidateProfile profile = mapper.newProfile(
                document, extraction, Instant.parse("2026-09-05T00:00:00Z"));

        assertThat(profile.getName()).isEqualTo("Backend Engineer");
        assertThat(profile.getSummary())
                .isEqualTo("Experienced with Spring Boot and relational databases.");
        assertThat(profile.getCvDocument()).isSameAs(document);
        assertThat(profile.getUser()).isSameAs(document.getUser());
    }

    @Test
    void fallsBackToFilenameWhenExtractionHasNoProfileTitle() {
        CvDocument document = CvDocument.builder()
                .user(UserAccount.builder().id(3L).build())
                .originalFilename("candidate-profile.pdf")
                .build();
        CvExtractionResult extraction = new CvExtractionResult(
                null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), null, null, null, null);

        CandidateProfile profile = mapper.newProfile(document, extraction, Instant.EPOCH);

        assertThat(profile.getName()).isEqualTo("candidate-profile");
    }
}
