package com.baseProject.myBaseProject.dto.cv;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import com.baseProject.myBaseProject.enums.SeniorityLevel;
import com.baseProject.myBaseProject.enums.SkillCategory;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Cấu trúc JSON mà Claude phải trả về khi đọc CV. Anthropic SDK tự sinh JSON schema từ record
 * này nên đây cũng là "hợp đồng" duy nhất giữa AI và database.
 *
 * <p>Các method {@code ...Value()} làm sạch dữ liệu AI trả về (cắt độ dài, đổi enum, parse ngày)
 * trước khi ghi xuống DB - AI là dữ liệu ngoài, không tin tuyệt đối được.
 */
public record CvParsePayload(
        @JsonPropertyDescription("One-line professional summary, e.g. 'Java backend developer, 2 years'")
        String headline,

        @JsonPropertyDescription("Total years of professional experience, 0 if the CV shows none")
        BigDecimal yearsExperience,

        @JsonPropertyDescription("Job title the candidate is applying for, as written in the CV")
        String targetPosition,

        @JsonPropertyDescription("One of STUDENT, FRESHER, JUNIOR, MID, SENIOR")
        String seniorityLevel,

        List<ParsedEducation> educations,

        @JsonPropertyDescription("Technical and soft skills, no duplicates")
        List<ParsedSkill> skills,

        @JsonPropertyDescription("Projects or work experience, newest first")
        List<ParsedProject> projects
) {
    /** Ghi vào cv_parse_results.schema_version, tăng khi đổi cấu trúc record này. */
    public static final String SCHEMA_VERSION = "v1";

    /** Độ dài tối đa của các cột VARCHAR liên quan, theo changeset 022. */
    public static final int MAX_HEADLINE = 255;
    public static final int MAX_TARGET_POSITION = 150;
    public static final int MAX_SCHOOL = 255;
    public static final int MAX_DEGREE = 150;
    public static final int MAX_FIELD_OF_STUDY = 150;
    public static final int MAX_SKILL_NAME = 80;
    public static final int MAX_PROJECT_NAME = 255;
    public static final int MAX_ROLE_IN_PROJECT = 150;

    private static final BigDecimal MAX_YEARS_EXPERIENCE = new BigDecimal("99.9");

    /** Trim, đổi chuỗi rỗng thành null và cắt cho vừa cột VARCHAR. */
    public static String text(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    /** years_experience là DECIMAL(3,1) nên giá trị vô lý sẽ bị bỏ thay vì làm insert lỗi. */
    public BigDecimal yearsExperienceValue() {
        if (yearsExperience == null
                || yearsExperience.signum() < 0
                || yearsExperience.compareTo(MAX_YEARS_EXPERIENCE) > 0) {
            return null;
        }
        return yearsExperience.setScale(1, RoundingMode.HALF_UP);
    }

    public SeniorityLevel seniorityLevelValue() {
        return toEnum(SeniorityLevel.class, seniorityLevel);
    }

    public List<ParsedEducation> educationList() {
        return educations == null ? List.of() : educations;
    }

    public List<ParsedSkill> skillList() {
        return skills == null ? List.of() : skills;
    }

    public List<ParsedProject> projectList() {
        return projects == null ? List.of() : projects;
    }

    private static <E extends Enum<E>> E toEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public record ParsedEducation(
            @JsonPropertyDescription("School or university name")
            String school,

            @JsonPropertyDescription("Degree, e.g. 'Bachelor'")
            String degree,

            @JsonPropertyDescription("Major or field of study")
            String fieldOfStudy,

            @JsonPropertyDescription("Start year, 4 digits")
            Integer startYear,

            @JsonPropertyDescription("End year (or expected graduation year), 4 digits")
            Integer endYear
    ) {
        /** DB có CHECK end_year >= start_year, AI trả ngược thì bỏ năm kết thúc thay vì lỗi cả bản parse. */
        public Integer endYearValue() {
            if (endYear == null || (startYear != null && endYear < startYear)) {
                return null;
            }
            return endYear;
        }
    }

    public record ParsedSkill(
            @JsonPropertyDescription("Skill name exactly as written in the CV, e.g. 'Spring Boot'")
            String name,

            @JsonPropertyDescription("One of LANGUAGE, FRAMEWORK, DATABASE, TOOL, SOFT")
            String category
    ) {
        public SkillCategory categoryValue() {
            return toEnum(SkillCategory.class, category);
        }
    }

    public record ParsedProject(
            @JsonPropertyDescription("Project or job title")
            String name,

            @JsonPropertyDescription("What the project does and what the candidate actually built, "
                    + "2-4 sentences, keep the original language of the CV")
            String description,

            @JsonPropertyDescription("Candidate's role, e.g. 'Backend developer'")
            String roleInProject,

            @JsonPropertyDescription("Technologies used, comma separated, e.g. 'Java, Spring Boot, MySQL'")
            String techStack,

            @JsonPropertyDescription("Start date as yyyy-MM-dd or yyyy-MM, null if the CV does not say")
            String startDate,

            @JsonPropertyDescription("End date as yyyy-MM-dd or yyyy-MM, null if ongoing")
            String endDate
    ) {
        public LocalDate startDateValue() {
            return toDate(startDate);
        }

        public LocalDate endDateValue() {
            LocalDate end = toDate(endDate);
            LocalDate start = startDateValue();
            // DB có CHECK end_date >= start_date, AI trả ngược thì coi như dự án chưa kết thúc.
            if (end == null || (start != null && end.isBefore(start))) {
                return null;
            }
            return end;
        }

        /** CV hay ghi ngày kiểu "2024-05" nên chấp nhận cả yyyy-MM, sai định dạng thì bỏ qua. */
        private static LocalDate toDate(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            String trimmed = value.trim();
            try {
                return LocalDate.parse(trimmed.length() == 7 ? trimmed + "-01" : trimmed);
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
    }
}
