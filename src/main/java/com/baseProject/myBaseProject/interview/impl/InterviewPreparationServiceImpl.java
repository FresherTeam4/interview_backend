package com.baseProject.myBaseProject.interview.impl;

import com.baseProject.myBaseProject.config.AsyncConfig;
import com.baseProject.myBaseProject.constant.Message;
import com.baseProject.myBaseProject.dto.ai.interview.InterviewPlanResult;
import com.baseProject.myBaseProject.entity.InterviewFocusArea;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTransitionActor;
import com.baseProject.myBaseProject.enums.InterviewerStyle;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.interview.InterviewPlanningService;
import com.baseProject.myBaseProject.interview.InterviewPreparationService;
import com.baseProject.myBaseProject.interview.support.InterviewSessionTransitionRecorder;
import com.baseProject.myBaseProject.interview.support.InterviewerStyleInstructionProvider;
import com.baseProject.myBaseProject.repository.InterviewFocusAreaRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class InterviewPreparationServiceImpl implements InterviewPreparationService {
    public static final String PLAN_SCHEMA_VERSION = "v1";
    public static final String PLAN_PROMPT_VERSION =
            "v2-style-" + InterviewerStyleInstructionProvider.STYLE_POLICY_VERSION;

    private final InterviewSessionRepository sessions;
    private final InterviewFocusAreaRepository focusAreas;
    private final InterviewPlanningService planningService;
    private final InterviewSessionTransitionRecorder transitionRecorder;
    private final ChatModel chatModel;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public InterviewPreparationServiceImpl(
            InterviewSessionRepository sessions,
            InterviewFocusAreaRepository focusAreas,
            InterviewPlanningService planningService,
            InterviewSessionTransitionRecorder transitionRecorder,
            ChatModel chatModel,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.sessions = sessions;
        this.focusAreas = focusAreas;
        this.planningService = planningService;
        this.transitionRecorder = transitionRecorder;
        this.chatModel = chatModel;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    @Async(AsyncConfig.INTERVIEW_PREPARATION_EXECUTOR)
    public void prepareAsync(Long sessionId) {
        try {
            // Claim trong transaction ngắn để không giữ khóa database trong lúc gọi AI.
            WorkItem item = claim(sessionId);
            if (item == null) {
                return;
            }
            InterviewPlanResult plan = planningService.generate(
                    item.templateSnapshotJson(), item.profileSnapshotJson(),
                    item.languageCode(), item.durationMinutes(), item.interviewerStyle());

            persist(sessionId, plan);
        } catch (DomainException exception) {
            log.warn("Interview preparation failed, sessionId={}, code={}, reason={}",
                    sessionId, exception.getCode(), exception.getMessage());
            markFailed(sessionId, exception.getCode());
        } catch (RuntimeException exception) {
            log.error("Interview preparation failed, sessionId={}", sessionId, exception);
            markFailed(sessionId, ErrorCode.INTERVIEW_SESSION_PREPARATION_FAILED);
        }
    }

    private WorkItem claim(Long sessionId) {
        return transactions.execute(status -> {
            // Khóa bản ghi và đánh dấu startedAt để mỗi session chỉ có một worker được xử lý.
            InterviewSession session = sessions.findByIdForUpdate(sessionId).orElse(null);
            if (session == null || session.getStatus() != InterviewSessionStatus.PREPARING
                    || session.getPreparationStartedAt() != null) {
                return null;
            }
            session.beginPreparation(clock.instant());
            return new WorkItem(
                    session.getTemplateSnapshotJson(),
                    session.getProfileSnapshotJson(),
                    session.getLanguageCode(),
                    session.getDurationMinutes(),
                    session.getInterviewerStyle());
        });
    }

    private void persist(Long sessionId, InterviewPlanResult plan) {
        Instant now = clock.instant();
        transactions.executeWithoutResult(status -> {
            // Bỏ qua kết quả đến muộn nếu session đã được một luồng khác chuyển trạng thái.
            InterviewSession session = sessions.findByIdForUpdate(sessionId).orElse(null);
            if (session == null || session.getStatus() != InterviewSessionStatus.PREPARING) {
                return;
            }

            // Thay kế hoạch và chuyển READY trong cùng transaction để dữ liệu luôn nhất quán.
            focusAreas.deleteBySessionId(sessionId);
            List<InterviewFocusArea> plannedAreas = new ArrayList<>();
            for (int index = 0; index < plan.focusAreas().size(); index++) {
                InterviewPlanResult.FocusArea area = plan.focusAreas().get(index);
                plannedAreas.add(InterviewFocusArea.builder()
                        .session(session)
                        .code(area.code())
                        .name(area.name())
                        .description(area.description())
                        .priority(area.priority())
                        .reason(area.reason())
                        .plannedSeconds(area.plannedSeconds())
                        .evidenceStatus(InterviewEvidenceStatus.NOT_EXPLORED)
                        .displayOrder((short) index)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
            }
            focusAreas.saveAll(plannedAreas);
            session.markReady(
                    plan.jobContextSummary(), plan.candidateContextSummary(),
                    plan.openingMessage(), PLAN_SCHEMA_VERSION, modelName(),
                    PLAN_PROMPT_VERSION, now);
            transitionRecorder.record(
                    session, InterviewSessionStatus.PREPARING, InterviewSessionStatus.READY,
                    "Interview plan prepared", InterviewTransitionActor.SYSTEM, now);
        });
    }

    private void markFailed(Long sessionId, ErrorCode errorCode) {
        try {
            Instant now = clock.instant();
            transactions.executeWithoutResult(status -> {
                InterviewSession session = sessions.findByIdForUpdate(sessionId).orElse(null);
                if (session == null || session.getStatus() != InterviewSessionStatus.PREPARING) {
                    return;
                }
                session.markPreparationFailed(
                        errorCode.name(), safeMessage(errorCode), now);
                transitionRecorder.record(
                        session, InterviewSessionStatus.PREPARING,
                        InterviewSessionStatus.PREPARATION_FAILED,
                        "Interview plan preparation failed",
                        InterviewTransitionActor.SYSTEM, now);
            });
        } catch (RuntimeException persistenceError) {
            // Worker async không có request caller nên lỗi lưu trạng thái phải được ghi log tại đây.
            log.error("Cannot persist preparation failure, sessionId={}",
                    sessionId, persistenceError);
        }
    }

    private String safeMessage(ErrorCode errorCode) {
        // Chỉ công khai lỗi hạ tầng đã chuẩn hóa, còn lỗi nội bộ dùng thông báo chung.
        return switch (errorCode) {
            case AI_TIMEOUT, AI_SERVICE_UNAVAILABLE -> errorCode.getDefaultMessage();
            default -> Message.INTERVIEW_SESSION_PREPARATION_FAILED;
        };
    }

    private String modelName() {
        String value = chatModel.getOptions() == null
                ? null : chatModel.getOptions().getModel();
        return value == null || value.isBlank() ? "unknown" : value.strip();
    }

    private record WorkItem(
            String templateSnapshotJson,
            String profileSnapshotJson,
            String languageCode,
            int durationMinutes,
            InterviewerStyle interviewerStyle) {
    }
}
