package com.baseProject.myBaseProject.service;

public interface QuestionImportRowProcessor {
    void process(Long rowId);

    void recordFailure(Long rowId, Throwable failure);
}
