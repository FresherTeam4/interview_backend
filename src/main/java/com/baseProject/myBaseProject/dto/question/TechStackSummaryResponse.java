package com.baseProject.myBaseProject.dto.question;

public record TechStackSummaryResponse(
        Integer id,
        String code,
        String nameVi,
        String nameEn,
        boolean active
) {
}
