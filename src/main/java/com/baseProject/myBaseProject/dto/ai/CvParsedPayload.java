package com.baseProject.myBaseProject.dto.ai;

import java.math.BigDecimal;
import java.util.List;

public record CvParsedPayload(
        String headline,
        BigDecimal yearsExperience,
        String targetPosition,
        String seniorityLevel,
        List<ParsedEducation> educations,
        List<ParsedSkill> skills,
        List<ParsedProject> projects
) {
    public record ParsedEducation(
            String school,
            String degree,
            String fieldOfStudy,
            Integer startYear,
            Integer endYear
    ) {
    }

    public record ParsedSkill(
            String name,
            String category
    ) {
    }

    public record ParsedProject(
            String name,
            String description,
            String roleInProject,
            List<String> techStack,
            String startDate,
            String endDate
    ) {
    }
}
