package com.baseProject.myBaseProject.service;

public interface QuestionImportAsyncWorker {
    void validateAsync(Long importId);

    void importAsync(Long importId);
}
