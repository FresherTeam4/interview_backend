package com.baseProject.myBaseProject.interview.generation.model;

import java.util.List;

public record ValidatedScript(
        List<ValidatedQuestion> questions,
        String modelName,
        String promptVersion,
        Integer tokenCost,
        int durationMs) {

    public ValidatedScript {
        questions = List.copyOf(questions);
    }
}
