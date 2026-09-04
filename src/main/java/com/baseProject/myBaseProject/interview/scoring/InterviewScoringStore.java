package com.baseProject.myBaseProject.interview.scoring;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.ReportHighlight;
import com.baseProject.myBaseProject.entity.RubricCriterion;
import com.baseProject.myBaseProject.entity.RubricCriterionLevel;
import com.baseProject.myBaseProject.entity.ScoreEvidence;
import com.baseProject.myBaseProject.entity.SessionReport;
import com.baseProject.myBaseProject.entity.SessionScore;
import com.baseProject.myBaseProject.entity.SessionTurn;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.enums.TurnRole;
import com.baseProject.myBaseProject.exception.InterviewScoringException;
import com.baseProject.myBaseProject.interview.ai.model.ScoringContract.ScoringCriterionInput;
import com.baseProject.myBaseProject.interview.ai.model.ScoringContract.ScoringInput;
import com.baseProject.myBaseProject.interview.ai.model.ScoringContract.ScoringLevelInput;
import com.baseProject.myBaseProject.interview.ai.model.ScoringContract.ScoringTurnInput;
import com.baseProject.myBaseProject.interview.lifecycle.SessionStateMachine;
import com.baseProject.myBaseProject.interview.scoring.model.ScoringData.CriterionReference;
import com.baseProject.myBaseProject.interview.scoring.model.ScoringData.ScoringPreparation;
import com.baseProject.myBaseProject.interview.scoring.model.ScoringData.TurnReference;
import com.baseProject.myBaseProject.interview.scoring.model.ScoringData.ValidatedCriterionScore;
import com.baseProject.myBaseProject.interview.scoring.model.ScoringData.ValidatedScoringResult;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.ReportHighlightRepository;
import com.baseProject.myBaseProject.repository.RubricCriterionRepository;
import com.baseProject.myBaseProject.repository.ScoreEvidenceRepository;
import com.baseProject.myBaseProject.repository.SessionReportRepository;
import com.baseProject.myBaseProject.repository.SessionScoreRepository;
import com.baseProject.myBaseProject.repository.SessionTurnRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class InterviewScoringStore {

    public static final String DISCLAIMER =
            "Đây là kết quả từ công cụ luyện tập, không phải chứng nhận năng lực.";
    private static final String INSUFFICIENT_SUMMARY =
            "Chưa có câu trả lời đã xác nhận để tạo báo cáo chấm điểm.";
    private static final String SCORED_TRANSITION_REASON = "Interview report committed";
    private static final String INSUFFICIENT_TRANSITION_REASON =
            "Insufficient-evidence report committed";

    private final InterviewSessionRepository sessionRepository;
    private final SessionTurnRepository turnRepository;
    private final RubricCriterionRepository criterionRepository;
    private final SessionScoreRepository scoreRepository;
    private final ScoreEvidenceRepository evidenceRepository;
    private final SessionReportRepository reportRepository;
    private final ReportHighlightRepository highlightRepository;
    private final SessionStateMachine stateMachine;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ScoringPreparation prepare(Long sessionId, UUID processingToken) {
        InterviewSession session = sessionRepository.findByIdWithLockedRubric(sessionId)
                .orElse(null);
        if (!ownsScoringClaim(session, processingToken)) {
            return null;
        }
        if (reportRepository.existsBySessionId(sessionId)) {
            throw inconsistent(sessionId, "report already exists");
        }
        if (session.getEndReason() == null || session.getTotalQuestionCount() <= 0) {
            throw inconsistent(sessionId, "completion context is missing");
        }

        List<SessionTurn> history = turnRepository.findOwnedHistory(
                sessionId,
                session.getUser().getId());
        List<ScoringTurnInput> candidateTurns = new ArrayList<>();
        Map<Long, TurnReference> turnsById = new LinkedHashMap<>();
        String currentPrompt = null;
        for (SessionTurn turn : history) {
            if (turn.getRole() == TurnRole.INTERVIEWER) {
                currentPrompt = turn.getContentText();
                continue;
            }
            if (turn.getQuestion() == null || currentPrompt == null) {
                throw inconsistent(sessionId, "candidate turn has no matching prompt");
            }
            candidateTurns.add(new ScoringTurnInput(
                    turn.getId(),
                    turn.getTurnIndex(),
                    currentPrompt,
                    turn.getContentText()));
            turnsById.put(turn.getId(), new TurnReference(
                    turn.getId(),
                    turn.getContentText()));
        }
        if (candidateTurns.isEmpty()) {
            throw inconsistent(sessionId, "scoring was claimed without candidate answers");
        }

        Map<String, CriterionReference> criteriaByCode = new LinkedHashMap<>();
        List<ScoringCriterionInput> criterionInputs = session.getRubricVersion()
                .getCriteria().stream()
                .sorted(Comparator.comparingInt(RubricCriterion::getDisplayOrder))
                .map(criterion -> toCriterionInput(criterion, criteriaByCode))
                .toList();
        if (criterionInputs.isEmpty()) {
            throw inconsistent(sessionId, "locked rubric has no criteria");
        }

        BigDecimal completionRatio = BigDecimal.valueOf(session.getAnsweredQuestionCount())
                .divide(
                        BigDecimal.valueOf(session.getTotalQuestionCount()),
                        4,
                        RoundingMode.HALF_UP);
        boolean partial = session.getAnsweredQuestionCount() < session.getTotalQuestionCount();
        return new ScoringPreparation(
                new ScoringInput(
                        session.getLanguageCode(),
                        completionRatio,
                        session.getEndReason(),
                        criterionInputs,
                        candidateTurns),
                criteriaByCode,
                turnsById,
                partial);
    }

    @Transactional
    public boolean commit(
            Long sessionId,
            UUID processingToken,
            ValidatedScoringResult result) {
        InterviewSession session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
        if (!ownsScoringClaim(session, processingToken)) {
            return false;
        }
        if (reportRepository.existsBySessionId(sessionId)
                || scoreRepository.existsBySessionId(sessionId)) {
            throw inconsistent(sessionId, "scoring output already exists");
        }

        Map<Long, RubricCriterion> criteria = criterionRepository
                .findAllById(result.criteria().stream()
                        .map(ValidatedCriterionScore::criterionId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(RubricCriterion::getId, Function.identity()));
        Map<Long, SessionTurn> turns = turnRepository
                .findAllById(result.criteria().stream()
                        .flatMap(score -> score.evidences().stream())
                        .map(evidence -> evidence.turnId())
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(SessionTurn::getId, Function.identity()));
        validatePersistedReferences(session, result, criteria, turns);

        Instant now = clock.instant();
        SessionReport report = reportRepository.save(SessionReport.scored(
                session,
                result.overallScore(),
                result.partial(),
                result.completionRatio(),
                result.assessedWeight(),
                result.summary(),
                DISCLAIMER,
                result.modelName(),
                result.promptVersion(),
                result.durationMs(),
                now));

        for (ValidatedCriterionScore validatedScore : result.criteria()) {
            SessionScore score = scoreRepository.save(SessionScore.create(
                    session,
                    criteria.get(validatedScore.criterionId()),
                    validatedScore.score(),
                    validatedScore.maxScore(),
                    validatedScore.levelNo(),
                    validatedScore.comment(),
                    result.modelName(),
                    now));
            evidenceRepository.saveAll(validatedScore.evidences().stream()
                    .map(evidence -> ScoreEvidence.create(
                            score,
                            turns.get(evidence.turnId()),
                            evidence.quote(),
                            evidence.startOffset(),
                            evidence.endOffset()))
                    .toList());
        }
        highlightRepository.saveAll(result.highlights().stream()
                .map(highlight -> ReportHighlight.create(
                        report,
                        highlight.type(),
                        highlight.content(),
                        highlight.displayOrder()))
                .toList());

        session.recordOverallScore(result.overallScore());
        stateMachine.completeScoring(session, processingToken, SCORED_TRANSITION_REASON);
        return true;
    }

    @Transactional
    public InterviewSession commitInsufficientEvidence(InterviewSession session) {
        Objects.requireNonNull(session);
        if (session.getStatus() != SessionStatus.SCORING
                || session.getAwaitingAction() != AwaitingAction.REPORT
                || session.getProcessingStage() != SessionProcessingStage.SCORING
                || session.getAnsweredQuestionCount() != 0
                || reportRepository.existsBySessionId(session.getId())
                || scoreRepository.existsBySessionId(session.getId())) {
            throw inconsistent(session.getId(), "cannot create insufficient-evidence report");
        }
        reportRepository.save(SessionReport.insufficientEvidence(
                session,
                INSUFFICIENT_SUMMARY,
                DISCLAIMER,
                clock.instant()));
        session.recordOverallScore(null);
        return stateMachine.completeScoringWithoutEvidence(
                session,
                INSUFFICIENT_TRANSITION_REASON);
    }

    private ScoringCriterionInput toCriterionInput(
            RubricCriterion criterion,
            Map<String, CriterionReference> criteriaByCode) {
        List<RubricCriterionLevel> levels = criterion.getLevels().stream()
                .sorted(Comparator.comparingInt(RubricCriterionLevel::getLevelNo))
                .toList();
        Map<Short, BigDecimal> scoresByLevel = levels.stream()
                .collect(Collectors.toMap(
                        RubricCriterionLevel::getLevelNo,
                        RubricCriterionLevel::getScoreValue,
                        (first, second) -> first,
                        LinkedHashMap::new));
        criteriaByCode.put(criterion.getCode(), new CriterionReference(
                criterion.getId(),
                criterion.getCode(),
                criterion.getName(),
                criterion.getWeight(),
                BigDecimal.valueOf(criterion.getMaxScore()),
                scoresByLevel));
        return new ScoringCriterionInput(
                criterion.getCode(),
                criterion.getName(),
                criterion.getDescription(),
                criterion.getWeight(),
                criterion.getMaxScore(),
                levels.stream()
                        .map(level -> new ScoringLevelInput(
                                level.getLevelNo(),
                                level.getLabel(),
                                level.getDescriptor(),
                                level.getScoreValue()))
                        .toList());
    }

    private void validatePersistedReferences(
            InterviewSession session,
            ValidatedScoringResult result,
            Map<Long, RubricCriterion> criteria,
            Map<Long, SessionTurn> turns) {
        for (ValidatedCriterionScore score : result.criteria()) {
            RubricCriterion criterion = criteria.get(score.criterionId());
            if (criterion == null
                    || !criterion.getRubricVersion().getId()
                            .equals(session.getRubricVersion().getId())) {
                throw inconsistent(session.getId(), "criterion no longer matches locked rubric");
            }
            score.evidences().forEach(evidence -> {
                SessionTurn turn = turns.get(evidence.turnId());
                if (turn == null
                        || turn.getRole() != TurnRole.CANDIDATE
                        || !turn.getSession().getId().equals(session.getId())
                        || !turn.getContentText().regionMatches(
                                evidence.startOffset(),
                                evidence.quote(),
                                0,
                                evidence.quote().length())) {
                    throw inconsistent(session.getId(), "evidence reference is invalid");
                }
            });
        }
    }

    private boolean ownsScoringClaim(
            InterviewSession session,
            UUID processingToken) {
        return session != null
                && session.getStatus() == SessionStatus.SCORING
                && session.getAwaitingAction() == AwaitingAction.REPORT
                && session.getProcessingStage() == SessionProcessingStage.SCORING
                && processingToken != null
                && processingToken.toString().equals(session.getProcessingToken());
    }

    private InterviewScoringException inconsistent(Long sessionId, String detail) {
        return InterviewScoringException.invalidOutput(
                "sessionId=" + sessionId + ", " + detail);
    }
}
