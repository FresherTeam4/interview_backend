package com.baseProject.myBaseProject.mapper;

import com.baseProject.myBaseProject.dto.ai.CvExtractionResult;
import com.baseProject.myBaseProject.entity.CandidateProfile;
import com.baseProject.myBaseProject.entity.CvDocument;
import com.baseProject.myBaseProject.entity.ProfileEducation;
import com.baseProject.myBaseProject.entity.ProfileProject;
import com.baseProject.myBaseProject.entity.ProfileSkill;
import com.baseProject.myBaseProject.enums.ProfileSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ProfileMapper {

    private static final int MAX_EDUCATIONS = 20;
    private static final int MAX_SKILLS = 100;
    private static final int MAX_PROJECTS = 50;

    private static final short MIN_YEAR = 1900;
    private static final short MAX_YEAR = 2100;
    private static final BigDecimal MAX_YEARS_EXPERIENCE = new BigDecimal("99.9");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern ISO_YEAR_MONTH = Pattern.compile("\\d{4}-\\d{2}");
    private static final Pattern YEAR_ONLY = Pattern.compile("\\d{4}");
    private static final Pattern MONTH_SLASH_YEAR = Pattern.compile("(\\d{1,2})/(\\d{4})");

    public CandidateProfile newProfile(CvDocument document,
                                       CvExtractionResult result,
                                       Instant createdAt) {
        // Dữ liệu AI không đáng tin hoàn toàn nên cắt theo đúng giới hạn cột trước khi lưu.
        return CandidateProfile.builder()
                .user(document.getUser())
                .cvDocument(document)
                .headline(clamp(result.headline(), 255))
                .yearsExperience(clampYearsExperience(result.yearsExperience()))
                .targetPosition(clamp(result.targetPosition(), 150))
                .seniorityLevel(clamp(result.seniorityLevel(), 30))
                .source(ProfileSource.AUTO_PARSED)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }

    public List<ProfileEducation> newEducations(CandidateProfile profile,
                                                CvExtractionResult result) {
        List<ProfileEducation> educations = new ArrayList<>();
        for (CvExtractionResult.EducationItem item : present(result.educations())) {
            if (educations.size() >= MAX_EDUCATIONS) {
                break;
            }

            String school = clamp(item.school(), 255);
            if (school == null) {
                continue;
            }

            Short startYear = clampYear(item.startYear());
            Short endYear = clampYear(item.endYear());
            if (startYear != null && endYear != null && endYear < startYear) {
                endYear = null;
            }

            educations.add(ProfileEducation.builder()
                    .profile(profile)
                    .school(school)
                    .degree(clamp(item.degree(), 150))
                    .fieldOfStudy(clamp(item.fieldOfStudy(), 150))
                    .startYear(startYear)
                    .endYear(endYear)
                    .userEdited(false)
                    .displayOrder((short) educations.size())
                    .build());
        }
        return educations;
    }

    public List<ProfileSkill> newSkills(CandidateProfile profile, CvExtractionResult result) {
        List<ProfileSkill> skills = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        for (CvExtractionResult.SkillItem item : present(result.skills())) {
            if (skills.size() >= MAX_SKILLS) {
                break;
            }

            String name = clamp(normalizeSkillName(item.name()), 80);
            // Collation DB không phân biệt hoa thường nên loại trùng trước khi INSERT.
            if (name == null || !seenNames.add(name.toLowerCase(Locale.ROOT))) {
                continue;
            }

            skills.add(ProfileSkill.builder()
                    .profile(profile)
                    .name(name)
                    .category(clamp(item.category(), 50))
                    .userEdited(false)
                    .displayOrder((short) skills.size())
                    .build());
        }
        return skills;
    }

    public List<ProfileProject> newProjects(CandidateProfile profile, CvExtractionResult result) {
        List<ProfileProject> projects = new ArrayList<>();
        for (CvExtractionResult.ProjectItem item : present(result.projects())) {
            if (projects.size() >= MAX_PROJECTS) {
                break;
            }

            String name = clamp(item.name(), 255);
            if (name == null) {
                continue;
            }

            LocalDate startDate = parseDate(item.startDate());
            LocalDate endDate = parseDate(item.endDate());
            if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
                endDate = null;
            }

            projects.add(ProfileProject.builder()
                    .profile(profile)
                    .name(name)
                    .description(clamp(item.description(), 5000))
                    .roleInProject(clamp(item.roleInProject(), 150))
                    .techStack(clamp(item.techStack(), 500))
                    .startDate(startDate)
                    .endDate(endDate)
                    .userEdited(false)
                    .displayOrder((short) projects.size())
                    .build());
        }
        return projects;
    }

    private String normalizeSkillName(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : WHITESPACE.matcher(normalized).replaceAll(" ");
    }

    private BigDecimal clampYearsExperience(BigDecimal yearsExperience) {
        if (yearsExperience == null) {
            return null;
        }
        return yearsExperience.max(BigDecimal.ZERO)
                .min(MAX_YEARS_EXPERIENCE)
                .setScale(1, RoundingMode.HALF_UP);
    }

    private Short clampYear(Short year) {
        return year == null || year < MIN_YEAR || year > MAX_YEAR ? null : year;
    }

    private LocalDate parseDate(String rawValue) {
        String value = blankToNull(rawValue);
        if (value == null) {
            return null;
        }

        try {
            LocalDate date = null;
            if (ISO_DATE.matcher(value).matches()) {
                date = LocalDate.parse(value);
            } else if (ISO_YEAR_MONTH.matcher(value).matches()) {
                date = LocalDate.parse(value + "-01");
            } else if (YEAR_ONLY.matcher(value).matches()) {
                date = LocalDate.of(Integer.parseInt(value), 1, 1);
            } else {
                Matcher matcher = MONTH_SLASH_YEAR.matcher(value);
                if (matcher.matches()) {
                    date = LocalDate.of(
                            Integer.parseInt(matcher.group(2)),
                            Integer.parseInt(matcher.group(1)),
                            1);
                }
            }

            return date == null || date.getYear() < MIN_YEAR || date.getYear() > MAX_YEAR
                    ? null
                    : date;
        } catch (DateTimeException e) {
            log.debug("Ignoring invalid date returned by CV parser: {}", value);
            return null;
        }
    }

    private String clamp(String rawValue, int maxLength) {
        String value = blankToNull(rawValue);
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return blankToNull(value.substring(0, maxLength));
    }

    private String blankToNull(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = rawValue.trim();
        return value.isEmpty() ? null : value;
    }

    private <T> List<T> present(List<T> values) {
        return values == null ? List.of() : values.stream().filter(java.util.Objects::nonNull).toList();
    }
}
