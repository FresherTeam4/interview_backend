package com.baseProject.myBaseProject.interview.support;

import com.baseProject.myBaseProject.enums.InterviewerStyle;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewerStyleInstructionProviderTest {

    @Test
    void providesVersionedInstructionForEveryInterviewerStyle() throws Exception {
        InterviewerStyleInstructionProvider provider =
                new InterviewerStyleInstructionProvider();

        assertThat(InterviewerStyleInstructionProvider.STYLE_POLICY_VERSION).isEqualTo("v1");
        assertThat(Arrays.stream(InterviewerStyle.values())
                .map(provider::instructionFor))
                .allSatisfy(instruction -> assertThat(instruction).isNotBlank());
    }

    @Test
    void keepsStyleBehaviorsDistinctAndEvaluationInvariant() throws Exception {
        InterviewerStyleInstructionProvider provider =
                new InterviewerStyleInstructionProvider();

        assertThat(provider.instructionFor(InterviewerStyle.FRIENDLY))
                .containsIgnoringCase("warm")
                .containsIgnoringCase("Never hint")
                .containsIgnoringCase("evidence standard");
        assertThat(provider.instructionFor(InterviewerStyle.PROFESSIONAL))
                .containsIgnoringCase("neutral")
                .containsIgnoringCase("structured");
        assertThat(provider.instructionFor(InterviewerStyle.CHALLENGING))
                .containsIgnoringCase("rigorous")
                .containsIgnoringCase("respectful")
                .containsIgnoringCase("evidence standard");
    }
}
