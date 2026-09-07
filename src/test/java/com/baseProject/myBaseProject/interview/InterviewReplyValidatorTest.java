package com.baseProject.myBaseProject.interview;

import com.baseProject.myBaseProject.dto.ai.interview.InterviewReplyResult;
import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;
import com.baseProject.myBaseProject.enums.InterviewFocusPriority;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTurnAction;
import com.baseProject.myBaseProject.enums.InterviewerStyle;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.interview.model.InterviewContext;
import com.baseProject.myBaseProject.interview.validation.InterviewReplyValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewReplyValidatorTest {
    private final InterviewReplyValidator validator = new InterviewReplyValidator();

    @Test
    void normalizesAValidFollowUpAndEvidenceUpdate() {
        InterviewReplyResult result = validator.validate(
                new InterviewReplyResult(
                        InterviewTurnAction.FOLLOW_UP,
                        "  Bạn đã đo kết quả thay đổi đó như thế nào?  ",
                        " backend ",
                        "  Ứng viên đã mô tả quyết định kỹ thuật.  ",
                        List.of(new InterviewReplyResult.EvidenceUpdate(
                                "backend",
                                InterviewEvidenceStatus.PARTIAL,
                                "  Có quyết định nhưng chưa có số liệu kết quả.  "))),
                context(InterviewEvidenceStatus.NOT_EXPLORED),
                false);

        assertThat(result.focusAreaCode()).isEqualTo("BACKEND");
        assertThat(result.interviewerMessage())
                .isEqualTo("Bạn đã đo kết quả thay đổi đó như thế nào?");
        assertThat(result.evidenceUpdates()).singleElement().satisfies(update -> {
            assertThat(update.focusAreaCode()).isEqualTo("BACKEND");
            assertThat(update.status()).isEqualTo(InterviewEvidenceStatus.PARTIAL);
        });
    }

    @Test
    void rejectsUnknownFocusArea() {
        assertThatThrownBy(() -> validator.validate(
                new InterviewReplyResult(
                        InterviewTurnAction.EXPLORE,
                        "Bạn có thể chia sẻ thêm không?",
                        "UNKNOWN",
                        "Chưa có thêm dữ liệu.",
                        List.of()),
                context(InterviewEvidenceStatus.NOT_EXPLORED),
                false))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(
                                ErrorCode.INTERVIEW_REPLY_INVALID));
    }

    @Test
    void rejectsEvidenceRegression() {
        assertThatThrownBy(() -> validator.validate(
                new InterviewReplyResult(
                        InterviewTurnAction.FOLLOW_UP,
                        "Bạn có thể nói rõ hơn không?",
                        "BACKEND",
                        "Ứng viên đã đưa ra bằng chứng.",
                        List.of(new InterviewReplyResult.EvidenceUpdate(
                                "BACKEND",
                                InterviewEvidenceStatus.PARTIAL,
                                "Bằng chứng một phần."))),
                context(InterviewEvidenceStatus.SUFFICIENT),
                false))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void forcesCloseInsideClosingWindow() {
        assertThatThrownBy(() -> validator.validate(
                new InterviewReplyResult(
                        InterviewTurnAction.EXPLORE,
                        "Câu hỏi tiếp theo?",
                        "BACKEND",
                        "Tóm tắt.",
                        List.of()),
                context(InterviewEvidenceStatus.PARTIAL),
                true))
                .isInstanceOf(DomainException.class);
    }

    private InterviewContext context(InterviewEvidenceStatus status) {
        return new InterviewContext(
                501L,
                InterviewSessionStatus.IN_PROGRESS,
                "vi",
                30,
                InterviewerStyle.PROFESSIONAL,
                null,
                null,
                "Backend Java",
                "Candidate",
                "Xin chào",
                null,
                null,
                null,
                0,
                List.of(new InterviewContext.FocusArea(
                        "BACKEND",
                        "Backend",
                        "Spring",
                        InterviewFocusPriority.HIGH,
                        "Required",
                        300,
                        status,
                        null,
                        (short) 0)));
    }
}
