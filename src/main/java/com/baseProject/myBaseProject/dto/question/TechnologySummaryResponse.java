package com.baseProject.myBaseProject.dto.question;

import com.baseProject.myBaseProject.enums.TechnologyType;

public record TechnologySummaryResponse(
        Integer id,
        String code,
        String nameVi,
        String nameEn,
        TechnologyType type,
        boolean active
) {
}
