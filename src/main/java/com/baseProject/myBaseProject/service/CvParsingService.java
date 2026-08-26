package com.baseProject.myBaseProject.service;


public interface CvParsingService {

    void parseAsync(Long cvDocumentId);
    void failParsesInterruptedByRestart();
}
