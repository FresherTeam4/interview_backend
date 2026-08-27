package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.entity.RubricVersion;

public interface RubricService {

    String MVP_RUBRIC_CODE = "TECH_INTERVIEW_FRESHER";

    /** Returns the fully initialized, validated rubric version used by new MVP sessions. */
    RubricVersion getCurrentPublishedVersion();
}
