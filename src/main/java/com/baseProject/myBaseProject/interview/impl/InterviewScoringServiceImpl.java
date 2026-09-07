package com.baseProject.myBaseProject.interview.impl;

import com.baseProject.myBaseProject.config.AsyncConfig;
import com.baseProject.myBaseProject.constant.Message;
import com.baseProject.myBaseProject.dto.ai.interview.InterviewAssessmentResult;
import com.baseProject.myBaseProject.entity.InterviewAssessment;
import com.baseProject.myBaseProject.entity.InterviewFocusArea;
import com.baseProject.myBaseProject.entity.InterviewFocusAreaResult;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTransitionActor;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.interview.InterviewScoringEngine;
import com.baseProject.myBaseProject.interview.InterviewScoringService;
import com.baseProject.myBaseProject.interview.model.InterviewScoreCalculation;
import com.baseProject.myBaseProject.interview.model.InterviewScoringContext;
import com.baseProject.myBaseProject.interview.support.InterviewScoreCalculator;
import com.baseProject.myBaseProject.interview.support.InterviewScoringContextLoader;
import com.baseProject.myBaseProject.interview.support.InterviewSessionTransitionRecorder;
import com.baseProject.myBaseProject.repository.InterviewAssessmentRepository;
import com.baseProject.myBaseProject.repository.InterviewFocusAreaRepository;
import com.baseProject.myBaseProject.repository.InterviewFocusAreaResultRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class InterviewScoringServiceImpl implements InterviewScoringService {
    private final InterviewSessionRepository sessions;
    private final InterviewAssessmentRepository assessments;
    private final InterviewFocusAreaRepository focusAreas;
    private final InterviewFocusAreaResultRepository focusAreaResults;
    private final InterviewScoringContextLoader contextLoader;
    private final InterviewScoringEngine engine;
    private final InterviewScoreCalculator calculator;
    private final InterviewSessionTransitionRecorder transitionRecorder;
    private final ObjectMapper objectMapper;
    private final ChatModel chatModel;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final TransactionTemplate failureTransactions;

    public InterviewScoringServiceImpl(
            InterviewSessionRepository sessions,
            InterviewAssessmentRepository assessments,
            InterviewFocusAreaRepository focusAreas,
            InterviewFocusAreaResultRepository focusAreaResults,
            InterviewScoringContextLoader contextLoader,
            InterviewScoringEngine engine,
            InterviewScoreCalculator calculator,
            InterviewSessionTransitionRecorder transitionRecorder,
            ObjectMapper objectMapper,
            ChatModel chatModel,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.sessions = sessions;
        this.assessments = assessments;
        this.focusAreas = focusAreas;
        this.focusAreaResults = focusAreaResults;
        this.contextLoader = contextLoader;
        this.engine = engine;
        this.calculator = calculator;
        this.transitionRecorder = transitionRecorder;
        this.objectMapper = objectMapper;
        this.chatModel = chatModel;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
        this.failureTransactions = new TransactionTemplate(transactionManager);

        // Ghi trạng thái FAILED độc lập với transaction đã lỗi hoặc callback AFTER_COMMIT.
        this.failureTransactions.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Async(AsyncConfig.INTERVIEW_SCORING_EXECUTOR)
    public void scoreAsync(Long sessionId) {
        try {
            if (!claim(sessionId)) {
                return;
            }
            // Không giữ transaction hoặc row lock trong lúc chờ AI phản hồi.
            InterviewScoringContext context = contextLoader.load(sessionId);
            InterviewAssessmentResult result = engine.assess(context);
            InterviewScoreCalculation calculation = calculator.calculate(context, result);

            persist(sessionId, result, calculation);
        } catch (DomainException exception) {
            log.warn("Interview scoring failed, sessionId={}, code={}, reason={}",
                    sessionId, exception.getCode(), exception.getMessage());
            markFailed(sessionId, exception.getCode());
        } catch (RuntimeException exception) {
            log.error("Interview scoring failed, sessionId={}", sessionId, exception);
            markFailed(sessionId, ErrorCode.INTERVIEW_SCORING_FAILED);
        }
    }

    @Override
    public void markDispatchFailed(Long sessionId) {
        markFailed(sessionId, ErrorCode.INTERVIEW_SCORING_FAILED);
    }

    private boolean claim(Long sessionId) {
        Boolean claimed = transactions.execute(status -> {
            // Row lock cùng scoringStartedAt bảo đảm chỉ một worker nhận session.
            InterviewSession session = sessions.findByIdForUpdate(sessionId).orElse(null);
            if (session == null || session.getStatus() != InterviewSessionStatus.SCORING
                    || session.getScoringStartedAt() != null) {
                return false;
            }
            session.markScoringStarted(clock.instant());
            return true;
        });

        return Boolean.TRUE.equals(claimed);
    }

    private void persist(
            Long sessionId,
            InterviewAssessmentResult result,
            InterviewScoreCalculation calculation) {
        Instant now = clock.instant();
        transactions.executeWithoutResult(status -> {

            // bỏ kết quả nếu session đã bị chuyển trạng thái
            InterviewSession session = sessions.findByIdForUpdate(sessionId).orElse(null);
            if (session == null || session.getStatus() != InterviewSessionStatus.SCORING
                    || session.getScoringStartedAt() == null) {
                return;
            }

            InterviewAssessment assessment = assessments.save(InterviewAssessment.builder()
                    .session(session)
                    .technicalScore(calculation.technicalScore())
                    .communicationScore(calculation.communicationScore())
                    .overallScore(calculation.overallScore())
                    .coveragePercentage(calculation.coveragePercentage())
                    .confidence(calculation.confidence())
                    .overallSummary(result.overallSummary())
                    .strengthsJson(objectMapper.writeValueAsString(result.strengths()))
                    .improvementsJson(objectMapper.writeValueAsString(result.improvements()))
                    .actionPlanJson(objectMapper.writeValueAsString(result.actionPlan()))
                    .communicationFeedback(result.communicationFeedback())
                    .schemaVersion(InterviewScoringEngineImpl.ASSESSMENT_SCHEMA_VERSION)
                    .modelName(modelName())
                    .promptVersion(InterviewScoringEngineImpl.ASSESSMENT_PROMPT_VERSION)
                    .createdAt(now)
                    .build());

            Map<String, InterviewFocusArea> areasByCode = new HashMap<>();
            for (InterviewFocusArea area
                    : focusAreas.findBySessionIdOrderByDisplayOrderAsc(sessionId)) {
                areasByCode.put(area.getCode(), area);
            }
            List<InterviewFocusAreaResult> persistedResults = new ArrayList<>();
            for (InterviewAssessmentResult.FocusAreaAssessment areaResult
                    : result.focusAreaAssessments()) {
                persistedResults.add(InterviewFocusAreaResult.builder()
                        .assessment(assessment)
                        .focusArea(areasByCode.get(areaResult.focusAreaCode()))
                        .score(areaResult.score() == null
                                ? null : BigDecimal.valueOf(areaResult.score()))
                        .confidence(areaResult.confidence())
                        .evidenceStatus(areaResult.evidenceStatus())
                        .rationale(areaResult.rationale())
                        .strengthsJson(objectMapper.writeValueAsString(areaResult.strengths()))
                        .gapsJson(objectMapper.writeValueAsString(areaResult.gaps()))
                        .feedback(areaResult.feedback())
                        .evidenceTurnIdsJson(objectMapper.writeValueAsString(
                                areaResult.evidenceTurnIds()))
                        .build());
            }
            focusAreaResults.saveAll(persistedResults);
            session.markScoringCompleted(now);
            transitionRecorder.record(
                    session,
                    InterviewSessionStatus.SCORING,
                    InterviewSessionStatus.COMPLETED,
                    "Interview scoring completed",
                    InterviewTransitionActor.SYSTEM,
                    now);
        });
    }

    private void markFailed(Long sessionId, ErrorCode errorCode) {
        try {
            Instant now = clock.instant();
            failureTransactions.executeWithoutResult(status -> {
                InterviewSession session = sessions.findByIdForUpdate(sessionId).orElse(null);
                if (session == null || session.getStatus() != InterviewSessionStatus.SCORING) {
                    return;
                }
                session.markScoringFailed(errorCode.name(), safeMessage(errorCode), now);
                transitionRecorder.record(
                        session,
                        InterviewSessionStatus.SCORING,
                        InterviewSessionStatus.SCORING_FAILED,
                        "Interview scoring failed",
                        InterviewTransitionActor.SYSTEM,
                        now);
            });
        } catch (RuntimeException persistenceError) {
            log.error("Cannot persist scoring failure, sessionId={}",
                    sessionId, persistenceError);
        }
    }

    private String safeMessage(ErrorCode errorCode) {
        return switch (errorCode) {
            case AI_TIMEOUT, AI_SERVICE_UNAVAILABLE -> errorCode.getDefaultMessage();
            default -> Message.INTERVIEW_SCORING_FAILED;
        };
    }

    private String modelName() {
        String value = chatModel.getOptions() == null
                ? null : chatModel.getOptions().getModel();

        return value == null || value.isBlank() ? "unknown" : value.strip();
    }
}
