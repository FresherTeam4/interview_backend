package com.baseProject.myBaseProject.jobdescription;

import com.baseProject.myBaseProject.dto.ai.JobAnalysis;

public interface JobDescriptionAnalysisService {
    JobAnalysis analyze(String title, String jobDescriptionText);
}
