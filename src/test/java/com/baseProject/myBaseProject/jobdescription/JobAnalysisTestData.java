package com.baseProject.myBaseProject.jobdescription;

import java.util.List;

import com.baseProject.myBaseProject.dto.ai.JobAnalysis;

public final class JobAnalysisTestData {
    public static final String JD =
            "Java Backend Fresher. Xây dựng REST API bằng Spring Boot và sử dụng MySQL.";

    public static JobAnalysis analysis() {
        return new JobAnalysis(
                true,
                "vi",
                "Java Backend Developer",
                "Fresher",
                "IT Services",
                "Phát triển API với Java và MySQL.",
                List.of(
                        new JobAnalysis.KeySkill("Spring Boot", JobAnalysis.SkillLevel.MUST_HAVE, "Xây dựng REST API"),
                        new JobAnalysis.KeySkill("MySQL", JobAnalysis.SkillLevel.MUST_HAVE, "Thiết kế và truy vấn CSDL")
                )
        );
    }

    private JobAnalysisTestData() {
    }
}
