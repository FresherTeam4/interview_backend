package com.baseProject.myBaseProject.realtime;

import com.baseProject.myBaseProject.entity.InterviewFocusArea;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.InterviewFocusPriority;
import com.baseProject.myBaseProject.enums.InterviewerStyle;
import com.baseProject.myBaseProject.interview.support.InterviewerStyleInstructionProvider;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RealtimeInterviewInstructionFactoryTest {

    @Test
    void rendersContextWithoutReplacingPlaceholderTextInsideValues() throws IOException {
        InterviewerStyleInstructionProvider styleInstructions =
                mock(InterviewerStyleInstructionProvider.class);
        when(styleInstructions.instructionFor(InterviewerStyle.PROFESSIONAL))
                .thenReturn("Keep a neutral tone.");
        RealtimeInterviewInstructionFactory factory =
                new RealtimeInterviewInstructionFactory(styleInstructions);
        InterviewSession session = InterviewSession.builder()
                .languageCode("vi")
                .durationMinutes(30)
                .interviewerStyle(InterviewerStyle.PROFESSIONAL)
                .jobContextSummary("Literal marker: {candidateContext}")
                .candidateContextSummary("Literal marker: {focusAreas}")
                .openingMessage("Xin chào, hãy giới thiệu về bạn.")
                .build();
        InterviewFocusArea focusArea = InterviewFocusArea.builder()
                .priority(InterviewFocusPriority.HIGH)
                .name("Java")
                .description("Concurrency")
                .reason("Verify practical experience")
                .plannedSeconds(300)
                .build();

        String instruction = factory.create(session, List.of(focusArea));

        assertThat(instruction)
                .contains("Interview language: Vietnamese (vi)")
                .contains("first receive START_INTERVIEW")
                .contains("about 5 to 12 seconds to speak")
                .contains("Ask exactly one primary question at a time")
                .contains("Literal marker: {candidateContext}")
                .contains("Literal marker: {focusAreas}")
                .contains("- [HIGH] Java: Concurrency")
                .contains("Keep a neutral tone.")
                .contains("Xin chào, hãy giới thiệu về bạn.");
    }
}
