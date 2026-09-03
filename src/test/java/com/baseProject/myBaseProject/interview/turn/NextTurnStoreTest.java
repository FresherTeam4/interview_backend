package com.baseProject.myBaseProject.interview.turn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionContextSnapshot;
import com.baseProject.myBaseProject.entity.SessionQuestion;
import com.baseProject.myBaseProject.entity.SessionTurn;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.enums.TurnRole;
import com.baseProject.myBaseProject.interview.lifecycle.SessionStateMachine;
import com.baseProject.myBaseProject.interview.turn.model.NextTurnData.NextTurnPreparation;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.SessionContextSnapshotRepository;
import com.baseProject.myBaseProject.repository.SessionQuestionRepository;
import com.baseProject.myBaseProject.repository.SessionTurnRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class NextTurnStoreTest {

    private static final Long SESSION_ID = 31L;
    private static final Long USER_ID = 41L;
    private static final Long QUESTION_ID = 51L;
    private static final Long CANDIDATE_TURN_ID = 61L;
    private static final UUID PROCESSING_TOKEN =
            UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock
    private InterviewSessionRepository sessionRepository;
    @Mock
    private SessionContextSnapshotRepository snapshotRepository;
    @Mock
    private SessionQuestionRepository questionRepository;
    @Mock
    private SessionTurnRepository turnRepository;
    @Mock
    private SessionStateMachine stateMachine;
    @Mock
    private Clock clock;
    @Mock
    private InterviewSession session;
    @Mock
    private UserAccount user;
    @Mock
    private SessionQuestion question;
    @Mock
    private SessionTurn candidateTurn;
    @Mock
    private SessionContextSnapshot snapshot;

    private NextTurnStore store;

    @BeforeEach
    void setUp() {
        store = new NextTurnStore(
                sessionRepository,
                snapshotRepository,
                questionRepository,
                turnRepository,
                stateMachine,
                clock);
    }

    @Test
    void prepareBuildsRelevantContextFromSnapshotSourceIds() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(session.getStatus()).thenReturn(SessionStatus.IN_PROGRESS);
        when(session.getAwaitingAction()).thenReturn(AwaitingAction.ENGINE_RESPONSE);
        when(session.getProcessingStage()).thenReturn(SessionProcessingStage.NEXT_TURN);
        when(session.getProcessingToken()).thenReturn(PROCESSING_TOKEN.toString());
        when(session.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(USER_ID);
        when(session.getLanguageCode()).thenReturn("vi");
        when(session.getCurrentQuestionOrdinal()).thenReturn((short) 1);
        when(session.getNextTurnIndex()).thenReturn(2);
        when(session.getAnsweredQuestionCount()).thenReturn((short) 1);
        when(session.getCurrentFollowupDepth()).thenReturn((short) 0);
        when(session.getTotalFollowupCount()).thenReturn((short) 0);

        when(turnRepository.findFirstBySessionIdAndSessionUserIdOrderByTurnIndexDesc(
                        SESSION_ID, USER_ID))
                .thenReturn(Optional.of(candidateTurn));
        when(candidateTurn.getId()).thenReturn(CANDIDATE_TURN_ID);
        when(candidateTurn.getRole()).thenReturn(TurnRole.CANDIDATE);
        when(candidateTurn.getQuestion()).thenReturn(question);
        when(candidateTurn.getTurnIndex()).thenReturn(1);
        when(candidateTurn.getContentText()).thenReturn("I used it in the snapshot project");
        when(question.getId()).thenReturn(QUESTION_ID);
        when(question.getOrdinal()).thenReturn((short) 1);
        when(question.getQuestionText()).thenReturn("How did you use Spring?");
        when(question.getTopic()).thenReturn("Spring");
        when(question.getCompetency()).thenReturn("Backend development");
        when(question.getSourceProjectSnapshotId()).thenReturn(42L);
        when(question.getSourceSkillSnapshotId()).thenReturn(15L);
        when(question.getSourceJdExcerpt()).thenReturn("Build Java services");
        when(turnRepository.findBySessionIdAndQuestionIdOrderByTurnIndexAsc(
                        SESSION_ID, QUESTION_ID))
                .thenReturn(List.of(candidateTurn));

        when(snapshotRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(snapshot));
        when(snapshot.getProfileJson()).thenReturn(profileSnapshot());

        NextTurnPreparation preparation = store.prepare(SESSION_ID, PROCESSING_TOKEN);

        JsonNode context = preparation.input().relevantProfileContext();
        assertThat(context.path("project").path("id").longValue()).isEqualTo(42L);
        assertThat(context.path("project").path("name").textValue())
                .isEqualTo("Snapshot project");
        assertThat(context.path("skill").path("id").longValue()).isEqualTo(15L);
        assertThat(context.path("skill").path("name").textValue())
                .isEqualTo("Snapshot skill");
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
