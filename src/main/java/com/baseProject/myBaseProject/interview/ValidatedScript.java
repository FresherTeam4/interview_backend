package com.baseProject.myBaseProject.interview;

import java.util.List;

record ValidatedScript(
        List<ValidatedQuestion> questions,
        String modelName,
        String promptVersion,
        Integer tokenCost,
        int durationMs) {

    ValidatedScript {
        questions = List.copyOf(questions);
    }
}
