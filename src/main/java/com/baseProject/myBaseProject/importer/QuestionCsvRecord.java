package com.baseProject.myBaseProject.importer;

public record QuestionCsvRecord(
        int rowNumber,
        String contentVi,
        String contentEn,
        String level,
        String questionType,
        String difficulty,
        String companyRef,
        String techStackCodes,
        String technologyCodes,
        String active
) {
}
