package com.baseProject.myBaseProject.interview.ai.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.config.properites.AiProperties;
import com.baseProject.myBaseProject.config.properites.InterviewAiProperties;
import com.baseProject.myBaseProject.enums.FollowUpDecision;
import com.baseProject.myBaseProject.enums.InterviewDifficulty;
import com.baseProject.myBaseProject.enums.SessionEndReason;
import com.baseProject.myBaseProject.enums.TurnRole;
import com.baseProject.myBaseProject.exception.FollowUpDecisionException;
import com.baseProject.myBaseProject.exception.ScriptGenerationException;
import com.baseProject.myBaseProject.interview.ai.model.FollowUpDecisionContract.FollowUpDecisionInput;
import com.baseProject.myBaseProject.interview.ai.model.FollowUpDecisionContract.FollowUpDecisionOutcome;
import com.baseProject.myBaseProject.interview.ai.model.FollowUpDecisionContract.FollowUpTurnContext;
import com.baseProject.myBaseProject.interview.ai.model.ScriptGenerationContract.ScriptGenerationInput;
import com.baseProject.myBaseProject.interview.ai.model.ScriptGenerationContract.ScriptGenerationOutcome;
import com.baseProject.myBaseProject.interview.ai.model.ScoringContract.ScoringInput;
import com.baseProject.myBaseProject.interview.ai.model.ScoringContract.ScoringOutcome;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.genai.errors.ClientException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.DefaultResourceLoader;

import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class GeminiInterviewAdaptersTest {

    private static final String SCRIPT_JSON = """
            {
              "questions": [{
                "ordinal": 1,
                "questionText": "Describe a difficult technical decision.",
                "topic": "Architecture",
                "competency": "TECHNICAL_DECISION",
                "difficulty": 3,
                "sourceType": "GENERAL_BEHAVIORAL",
                "sourceProjectId": null,
                "sourceSkillId": null,
                "sourceJdExcerpt": null,
                "signatureConcept": "technical-decision"
              }]
            }
            """;
    private static final String FOLLOW_UP_JSON = """
            {
              "decision": "FOLLOW_UP",
              "questionText": "What trade-off did you consider?",
              "evidenceQuote": "I chose a queue",
              "reason": "The trade-off needs clarification"
            }
            """;
    private static final String SCORING_JSON = """
            {
              "criteria": [{
                "criterionCode": "TECHNICAL_DEPTH",
                "levelNo": 3,
                "score": 3.0,
                "comment": "Good technical explanation",
                "evidences": [{"turnId": 101, "quoteText": "I chose Redis"}]
              }],
              "summary": "Solid foundation",
              "strengths": ["Clear choice"],
              "improvements": ["Add measurements"],
              "nextActions": ["Practice trade-offs"]
            }
            """;

    @Mock
    private GoogleGenAiChatModel chatModel;
    @Mock
    private ObjectProvider<GoogleGenAiChatModel> chatModelProvider;

    private GeminiInterviewQuestionGenerator questionGenerator;
    private GeminiInterviewFollowUpDecider followUpDecider;
    private GeminiInterviewScorer scorer;

    @BeforeEach
    void setUp() {
        AiProperties credentials = new AiProperties(
                "test-api-key", "gemini-cv", "v2", 25_000);
        InterviewAiProperties properties = new InterviewAiProperties(
                "gemini-interview", "v1", "v1", "v1", 12_000, 8_000, 50_000);
        JsonMapper jsonMapper = JsonMapper.builder().build();
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        questionGenerator = new GeminiInterviewQuestionGenerator(
                credentials, properties, jsonMapper, resourceLoader, chatModelProvider);
        followUpDecider = new GeminiInterviewFollowUpDecider(
                credentials, properties, jsonMapper, resourceLoader, chatModelProvider);
        scorer = new GeminiInterviewScorer(
                credentials, properties, jsonMapper, resourceLoader, chatModelProvider);
    }

    @Test
    void questionGeneratorBuildsTrustedPromptAndReadsStructuredMetadata() {
        when(chatModelProvider.getObject()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(response(SCRIPT_JSON));

        ScriptGenerationOutcome outcome = questionGenerator.generate(new ScriptGenerationInput(
                "en",
                InterviewDifficulty.MEDIUM,
                1,
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                JsonNodeFactory.instance.objectNode(),
                "Build Java services",
                Set.of()));

        assertThat(outcome.script().questions()).hasSize(1);
        assertThat(outcome.modelName()).isEqualTo("gemini-response-model");
        assertThat(outcome.tokenCost()).isEqualTo(30);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getSystemMessage().getText())
                .contains("RANH GIỚI TIN CẬY");
        assertThat(promptCaptor.getValue().getUserMessage().getText())
                .contains("<UNTRUSTED_INTERVIEW_CONTEXT>");
    }

    @Test
    void followUpDeciderReadsStructuredDecision() {
        when(chatModelProvider.getObject()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(response(FOLLOW_UP_JSON));

        FollowUpDecisionOutcome outcome = followUpDecider.decide(new FollowUpDecisionInput(
                "en",
                "How did you handle traffic?",
                "Scalability",
                "SYSTEM_DESIGN",
                JsonNodeFactory.instance.objectNode(),
                "Build scalable services",
                List.of(new FollowUpTurnContext(
                        TurnRole.CANDIDATE, "I chose a queue", false, (short) 0)),
                "I chose a queue",
                2,
                5));

        assertThat(outcome.result().decision()).isEqualTo(FollowUpDecision.FOLLOW_UP);
        assertThat(outcome.result().evidenceQuote()).isEqualTo("I chose a queue");
        assertThat(outcome.promptVersion()).isEqualTo("v1");
    }

    @Test
    void rateLimitIsTranslatedToEachDomainFailure() {
        when(chatModelProvider.getObject()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new ClientException(429, "RESOURCE_EXHAUSTED", "quota"));

        assertThatThrownBy(() -> questionGenerator.generate(new ScriptGenerationInput(
                        "en",
                        InterviewDifficulty.MEDIUM,
                        1,
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                        JsonNodeFactory.instance.objectNode(),
                        "Build Java services",
                        Set.of())))
                .isInstanceOfSatisfying(ScriptGenerationException.class,
                        failure -> assertThat(failure.getReason())
                                .isEqualTo(ScriptGenerationException.Reason.PROVIDER_UNAVAILABLE));

        assertThatThrownBy(() -> followUpDecider.decide(new FollowUpDecisionInput(
                        "en",
                        "How did you handle traffic?",
                        "Scalability",
                        "SYSTEM_DESIGN",
                        JsonNodeFactory.instance.objectNode(),
                        null,
                        List.of(),
                        "I chose a queue",
                        2,
                        5)))
                .isInstanceOfSatisfying(FollowUpDecisionException.class,
                        failure -> assertThat(failure.getReason())
                                .isEqualTo(FollowUpDecisionException.Reason.PROVIDER_UNAVAILABLE));

        assertThatThrownBy(() -> scorer.score(new ScoringInput(
                        "en",
                        java.math.BigDecimal.ONE,
                        SessionEndReason.USER_COMPLETED,
                        List.of(),
                        List.of())))
                .isInstanceOf(com.baseProject.myBaseProject.exception.InterviewScoringException.class);
    }

    @Test
    void scorerReadsStructuredScoresAndProtectsUntrustedTranscript() {
        when(chatModelProvider.getObject()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(response(SCORING_JSON));

        ScoringOutcome outcome = scorer.score(new ScoringInput(
                "en",
                java.math.BigDecimal.ONE,
                SessionEndReason.USER_COMPLETED,
                List.of(),
                List.of()));

        assertThat(outcome.result().criteria()).hasSize(1);
        assertThat(outcome.result().criteria().get(0).criterionCode())
                .isEqualTo("TECHNICAL_DEPTH");
        assertThat(outcome.promptVersion()).isEqualTo("v1");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getSystemMessage().getText())
                .contains("RANH GIỚI TIN CẬY");
        assertThat(promptCaptor.getValue().getUserMessage().getText())
                .contains("<UNTRUSTED_SCORING_CONTEXT>");
    }

    private static ChatResponse response(String content) {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(content))),
                ChatResponseMetadata.builder()
                        .model("gemini-response-model")
                        .usage(new DefaultUsage(20, 10, 30, null))
                        .build());
    }
}
