package com.baseProject.myBaseProject.interview.ai.port;

import com.baseProject.myBaseProject.interview.ai.model.ScriptGenerationContract.ScriptGenerationInput;
import com.baseProject.myBaseProject.interview.ai.model.ScriptGenerationContract.ScriptGenerationOutcome;

public interface InterviewQuestionGenerator {

    ScriptGenerationOutcome generate(ScriptGenerationInput input);
}
