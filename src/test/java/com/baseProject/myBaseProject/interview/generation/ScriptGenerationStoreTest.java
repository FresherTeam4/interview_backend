package com.baseProject.myBaseProject.interview.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.config.properites.InterviewQuestionProperties;
import com.baseProject.myBaseProject.entity.CandidateProfile;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionContextSnapshot;
import com.baseProject.myBaseProject.enums.InterviewDifficulty;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.interview.generation.model.ScriptGenerationData.GenerationPreparation;
import com.baseProject.myBaseProject.interview.lifecycle.SessionStateMachine;
import com.baseProject.myBaseProject.repository.CandidateProfileRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.SessionContextSnapshotRepository;
import com.baseProject.myBaseProject.repository.SessionQuestionRepository;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class ScriptGenerationStoreTest {

    private static final Long SESSION_ID = 11L;
    private static final Long PROFILE_ID = 21L;
    private static final UUID PROCESSING_TOKEN =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID GENERATION_SEED =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    private InterviewSessionRepository sessionRepository;
    @Mock
    private CandidateProfileRepository profileRepository;
    @Mock
    private SessionContextSnapshotRepository snapshotRepository;
    @Mock
    private SessionQuestionRepository questionRepository;
    @Mock
    private SessionStateMachine stateMachine;
    @Mock
    private QuestionDiversityPolicy diversityPolicy;
    @Mock
    private Clock clock;
    @Mock
    private SessionContextSnapshot snapshot;
    @Mock
    private InterviewSession session;
    @Mock
    private CandidateProfile profile;

    private ScriptGenerationStore store;

    @BeforeEach
    void setUp() {
        store = new ScriptGenerationStore(
                new InterviewQuestionProperties(4, 6, 8),
                sessionRepository,
                profileRepository,
                snapshotRepository,
                questionRepository,
                stateMachine,
                diversityPolicy,
                clock);
    }

    @Test
    void prepareUsesProjectAndSkillIdsFromImmutableSnapshot() {
        ObjectNode profileJson = profileSnapshot();
        when(snapshotRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(snapshot));
        when(snapshot.getSession()).thenReturn(session);
        when(snapshot.getProfileJson()).thenReturn(profileJson);
        when(snapshot.getJobDescriptionText()).thenReturn("Java backend position");
        when(snapshot.getJobDescriptionHash()).thenReturn("a".repeat(64));
        when(session.getStatus()).thenReturn(SessionStatus.SCRIPT_GENERATING);
        when(session.getProcessingStage()).thenReturn(SessionProcessingStage.SCRIPT_GENERATION);
        when(session.getProcessingToken()).thenReturn(PROCESSING_TOKEN.toString());
        when(session.getProfile()).thenReturn(profile);
        when(profile.getId()).thenReturn(PROFILE_ID);
        when(session.getLanguageCode()).thenReturn("vi");
        when(session.getDifficulty()).thenReturn(InterviewDifficulty.MEDIUM);
        when(session.getGenerationSeed()).thenReturn(GENERATION_SEED.toString());
        when(questionRepository.countBySessionId(SESSION_ID)).thenReturn(0L);
        when(questionRepository.findRecentComparableSessionIds(
                        eq(PROFILE_ID),
                        eq("a".repeat(64)),
                        eq(SESSION_ID),
                        any(Pageable.class)))
                .thenReturn(List.of());

        GenerationPreparation preparation = store.prepare(SESSION_ID, PROCESSING_TOKEN);

        assertThat(preparation.allowedProjectIds()).containsExactly(42L);
        assertThat(preparation.allowedSkillIds()).containsExactly(15L);
        assertThat(preparation.input().profile()).isEqualTo(profileJson);
        assertThat(preparation.input().questionCount()).isEqualTo(6);
    }

    private static ObjectNode profileSnapshot() {
        ObjectNode profile = JsonNodeFactory.instance.objectNode();
        profile.putArray("projects")
                .addObject()
                .put("id", 42L)
                .put("name", "Snapshot project");
        profile.putArray("skills")
                .addObject()
                .put("id", 15L)
                .put("name", "Snapshot skill");
        return profile;
    }
}
