package com.baseProject.myBaseProject.dto.ai;

import java.util.List;

public record JobAnalysis(
        boolean sufficientJobContext,
        String sourceLanguage,
        String jobTitle,
        String targetSeniority,
        String domain,
        String summary,
        List<KeySkill> keySkills) {

    public record KeySkill(
            String name,
            SkillLevel level,
            String description) {
    }

    public enum SkillLevel {
        MUST_HAVE, NICE_TO_HAVE
    }
}
