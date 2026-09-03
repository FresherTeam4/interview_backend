package com.baseProject.myBaseProject.interview.ai.model;

import java.util.List;

public record GeneratedScript(List<GeneratedQuestion> questions) {

    public GeneratedScript {
        questions = questions == null ? null : List.copyOf(questions);
    }
}
