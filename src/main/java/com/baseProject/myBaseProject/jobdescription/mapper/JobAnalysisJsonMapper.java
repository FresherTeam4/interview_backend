package com.baseProject.myBaseProject.jobdescription.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.baseProject.myBaseProject.dto.ai.JobAnalysis;

import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class JobAnalysisJsonMapper {
    public static final String SCHEMA_VERSION = "v2";

    private final ObjectMapper objectMapper;

    public String toJson(JobAnalysis analysis) {
        return objectMapper.writeValueAsString(analysis);
    }

    public JobAnalysis fromJson(String json) {
        return json == null ? null : objectMapper.readValue(json, JobAnalysis.class);
    }
}
