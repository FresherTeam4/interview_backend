package com.baseProject.myBaseProject.interview.scoring;

import com.baseProject.myBaseProject.dto.report.InterviewReportResponse;
import com.baseProject.myBaseProject.dto.report.InterviewReportResponse.CriterionScore;
import com.baseProject.myBaseProject.dto.report.InterviewReportResponse.Evidence;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.ReportHighlight;
import com.baseProject.myBaseProject.entity.ScoreEvidence;
import com.baseProject.myBaseProject.entity.SessionReport;
import com.baseProject.myBaseProject.entity.SessionScore;
import com.baseProject.myBaseProject.enums.ReportHighlightType;
import com.baseProject.myBaseProject.enums.SessionFailureStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.exception.ReportNotReadyException;
import com.baseProject.myBaseProject.exception.SessionNotFoundException;
import com.baseProject.myBaseProject.exception.SessionRetryRequiredException;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.ReportHighlightRepository;
import com.baseProject.myBaseProject.repository.ScoreEvidenceRepository;
import com.baseProject.myBaseProject.repository.SessionReportRepository;
import com.baseProject.myBaseProject.repository.SessionScoreRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InterviewReportQueryService {

    private final InterviewSessionRepository sessionRepository;
    private final SessionReportRepository reportRepository;
    private final SessionScoreRepository scoreRepository;
    private final ScoreEvidenceRepository evidenceRepository;
    private final ReportHighlightRepository highlightRepository;

    @Transactional(readOnly = true)
    public InterviewReportResponse get(Long userId, Long sessionId) {
        InterviewSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(SessionNotFoundException::new);
        SessionReport report = reportRepository.findOwned(sessionId, userId).orElse(null);
        if (report == null) {
            if (session.getStatus() == SessionStatus.FAILED
                    && session.getFailureStage() == SessionFailureStage.SCORING) {
                throw new SessionRetryRequiredException();
            }
            if (session.getStatus() == SessionStatus.COMPLETED) {
                throw new IllegalStateException(
                        "COMPLETED session has no report, sessionId=" + sessionId);
            }
            throw new ReportNotReadyException();
        }

        List<SessionScore> scores = scoreRepository.findBySessionIdInDisplayOrder(sessionId);
        List<Long> scoreIds = scores.stream().map(SessionScore::getId).toList();
        Map<Long, List<ScoreEvidence>> evidencesByScore = scoreIds.isEmpty()
                ? Collections.emptyMap()
                : evidenceRepository.findByScoreIds(scoreIds).stream()
                        .collect(Collectors.groupingBy(
                                evidence -> evidence.getSessionScore().getId()));
        List<ReportHighlight> highlights = highlightRepository
                .findByReportIdOrderByTypeAscDisplayOrderAsc(report.getId());

        return new InterviewReportResponse(
                sessionId,
                report.getResultStatus(),
                report.getOverallScore(),
                report.isPartial(),
                report.getCompletionRatio(),
                report.getAssessedWeight(),
                report.getSummaryText(),
                report.getDisclaimer(),
                scores.stream()
                        .map(score -> toCriterion(
                                score,
                                evidencesByScore.getOrDefault(score.getId(), List.of())))
                        .toList(),
                highlightContents(highlights, ReportHighlightType.STRENGTH),
                highlightContents(highlights, ReportHighlightType.IMPROVEMENT),
                highlightContents(highlights, ReportHighlightType.NEXT_ACTION),
                report.getGeneratedAt());
    }

    private CriterionScore toCriterion(
            SessionScore score,
            List<ScoreEvidence> evidences) {
        return new CriterionScore(
                score.getCriterionCode(),
                score.getCriterionName(),
                score.getScore(),
                score.getMaxScore(),
                score.getLevelNo(),
                score.getComment(),
                evidences.stream()
                        .map(evidence -> new Evidence(
                                evidence.getTurn().getId(),
                                evidence.getQuoteText(),
                                evidence.getStartOffset(),
                                evidence.getEndOffset()))
                        .toList());
    }

    private List<String> highlightContents(
            List<ReportHighlight> highlights,
            ReportHighlightType type) {
        return highlights.stream()
                .filter(highlight -> highlight.getType() == type)
                .map(ReportHighlight::getContent)
                .toList();
    }
}
