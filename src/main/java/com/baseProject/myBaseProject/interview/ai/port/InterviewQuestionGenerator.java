package com.baseProject.myBaseProject.interview.ai.port;

import com.baseProject.myBaseProject.interview.ai.model.ScriptGenerationInput;
import com.baseProject.myBaseProject.interview.ai.model.ScriptGenerationOutcome;

public interface InterviewQuestionGenerator {

    ScriptGenerationOutcome generate(ScriptGenerationInput input);
}
