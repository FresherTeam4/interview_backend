package com.baseProject.myBaseProject.mapper;

import com.baseProject.myBaseProject.dto.ai.CvParsedPayload;
import com.baseProject.myBaseProject.dto.profile.CandidateProfileResponse;
import com.baseProject.myBaseProject.dto.profile.ProfileEducationDto;
import com.baseProject.myBaseProject.dto.profile.ProfileProjectDto;
import com.baseProject.myBaseProject.dto.profile.ProfileSkillDto;
import com.baseProject.myBaseProject.dto.profile.ProfileSummaryResponse;
import com.baseProject.myBaseProject.dto.profile.ProfileUpdateRequest;
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
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ProfileMapper {

    private static final int MAX_EDUCATIONS = 20;
    private static final int MAX_SKILLS = 100;
    private static final int MAX_PROJECTS = 50;
    private static final int MAX_TECH_ITEMS = 20;

    private static final int HEADLINE_LENGTH = 255;
    private static final int TARGET_POSITION_LENGTH = 150;
    private static final int SENIORITY_LEVEL_LENGTH = 30;
    private static final int SCHOOL_LENGTH = 255;
    private static final int DEGREE_LENGTH = 150;
    private static final int FIELD_OF_STUDY_LENGTH = 150;
    private static final int SKILL_NAME_LENGTH = 80;
    private static final int SKILL_CATEGORY_LENGTH = 50;
    private static final int PROJECT_NAME_LENGTH = 255;
    private static final int ROLE_IN_PROJECT_LENGTH = 150;

    private static final int DESCRIPTION_LENGTH = 5000;
    private static final int TECH_STACK_LENGTH = 500;

    private static final short MIN_YEAR = 1900;
    private static final short MAX_YEAR = 2100;
    private static final BigDecimal MAX_YEARS_EXPERIENCE = new BigDecimal("99.9");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final String TECH_STACK_SEPARATOR = ", ";

    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern ISO_YEAR_MONTH = Pattern.compile("\\d{4}-\\d{2}");
    private static final Pattern YEAR_ONLY = Pattern.compile("\\d{4}");
    private static final Pattern MONTH_SLASH_YEAR = Pattern.compile("(\\d{1,2})/(\\d{4})");

    public CandidateProfile newProfile(CvDocument document, CvParsedPayload payload, Instant createdAt) {
        return CandidateProfile.builder()
                .user(document.getUser())
                .cvDocument(document)
                .headline(clamp(payload.headline(), HEADLINE_LENGTH))
                .yearsExperience(clampYearsExperience(payload.yearsExperience()))
                .targetPosition(clamp(payload.targetPosition(), TARGET_POSITION_LENGTH))
                .seniorityLevel(clamp(payload.seniorityLevel(), SENIORITY_LEVEL_LENGTH))
                .source(ProfileSource.AUTO_PARSED)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }

    public List<ProfileEducation> newEducations(CandidateProfile profile, CvParsedPayload payload) {
        List<ProfileEducation> result = new ArrayList<>();

        for (CvParsedPayload.ParsedEducation parsed : limit(payload.educations(), MAX_EDUCATIONS)) {
            String school = clamp(parsed.school(), SCHOOL_LENGTH);
            if (school == null) {
                log.debug("Bỏ một mục học vấn không có tên trường");
                continue;
            }

            Short startYear = clampYear(parsed.startYear());
            result.add(ProfileEducation.builder()
                    .profile(profile)
                    .school(school)
                    .degree(clamp(parsed.degree(), DEGREE_LENGTH))
                    .fieldOfStudy(clamp(parsed.fieldOfStudy(), FIELD_OF_STUDY_LENGTH))
                    .startYear(startYear)
                    .endYear(clampEndYear(startYear, clampYear(parsed.endYear())))
                    .userEdited(false)
                    .displayOrder((short) result.size())
                    .build());
        }

        return result;
    }

    public List<ProfileSkill> newSkills(CandidateProfile profile, CvParsedPayload payload) {
        List<ProfileSkill> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (CvParsedPayload.ParsedSkill parsed : nonNullItems(payload.skills())) {
            if (result.size() >= MAX_SKILLS) {
                break;
            }

            String name = clamp(normalizeSkillName(parsed.name()), SKILL_NAME_LENGTH);
            if (name == null || !seen.add(name.toLowerCase(Locale.ROOT))) {
                continue;
            }

            result.add(ProfileSkill.builder()
                    .profile(profile)
                    .name(name)
                    .category(clamp(parsed.category(), SKILL_CATEGORY_LENGTH))
                    .userEdited(false)
                    .displayOrder((short) result.size())
                    .build());
        }

        return result;
    }

    public List<ProfileProject> newProjects(CandidateProfile profile, CvParsedPayload payload) {
        List<ProfileProject> result = new ArrayList<>();

        for (CvParsedPayload.ParsedProject parsed : limit(payload.projects(), MAX_PROJECTS)) {
            String name = clamp(parsed.name(), PROJECT_NAME_LENGTH);
            if (name == null) {
                log.debug("Bỏ một mục dự án không có tên");
                continue;
            }

            LocalDate startDate = parseFlexibleDate(parsed.startDate());
            result.add(ProfileProject.builder()
                    .profile(profile)
                    .name(name)
                    .description(clamp(parsed.description(), DESCRIPTION_LENGTH))
                    .roleInProject(clamp(parsed.roleInProject(), ROLE_IN_PROJECT_LENGTH))
                    .techStack(joinTechStack(parsed.techStack()))
                    .startDate(startDate)
                    .endDate(clampEndDate(startDate, parseFlexibleDate(parsed.endDate())))
                    .userEdited(false)
                    .displayOrder((short) result.size())
                    .build());
        }

        return result;
    }

    public CandidateProfileResponse toResponse(CandidateProfile profile,
                                               List<ProfileEducation> educations,
                                               List<ProfileSkill> skills,
                                               List<ProfileProject> projects) {
        return new CandidateProfileResponse(
                profile.getId(),
                profile.getCvDocument().getId(),
                profile.getCvDocument().getOriginalFilename(),
                profile.getHeadline(),
                profile.getYearsExperience(),
                profile.getTargetPosition(),
                profile.getSeniorityLevel(),
                profile.getSource(),
                profile.getConfirmedAt(),
                profile.getCreatedAt(),
                profile.getUpdatedAt(),
                educations.stream().map(this::toDto).toList(),
                skills.stream().map(this::toDto).toList(),
                projects.stream().map(this::toDto).toList());
    }

    public ProfileSummaryResponse toSummary(CandidateProfile profile,
                                            int educationCount, int skillCount, int projectCount) {
        return new ProfileSummaryResponse(
                profile.getId(),
                profile.getCvDocument().getId(),
                profile.getCvDocument().getOriginalFilename(),
                profile.getHeadline(),
                profile.getTargetPosition(),
                profile.getSeniorityLevel(),
                profile.getSource(),
                profile.getConfirmedAt(),
                educationCount,
                skillCount,
                projectCount,
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }

    public ProfileEducationDto toDto(ProfileEducation entity) {
        return new ProfileEducationDto(
                entity.getId(),
                entity.getSchool(),
                entity.getDegree(),
                entity.getFieldOfStudy(),
                entity.getStartYear(),
                entity.getEndYear(),
                entity.isUserEdited(),
                entity.getDisplayOrder());
    }

    public ProfileSkillDto toDto(ProfileSkill entity) {
        return new ProfileSkillDto(
                entity.getId(),
                entity.getName(),
                entity.getCategory(),
                entity.isUserEdited(),
                entity.getDisplayOrder());
    }

    public ProfileProjectDto toDto(ProfileProject entity) {
        return new ProfileProjectDto(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getRoleInProject(),
                entity.getTechStack(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.isUserEdited(),
                entity.getDisplayOrder());
    }

    public void applyScalars(CandidateProfile profile, ProfileUpdateRequest request, Instant now) {
        profile.setHeadline(blankToNull(request.headline()));
        profile.setYearsExperience(request.yearsExperience());
        profile.setTargetPosition(blankToNull(request.targetPosition()));
        profile.setSeniorityLevel(blankToNull(request.seniorityLevel()));
        profile.setSource(ProfileSource.USER_EDITED);
        profile.setUpdatedAt(now);
    }

    public ProfileEducation newEducation(CandidateProfile profile, ProfileEducationDto dto, short order) {
        return ProfileEducation.builder()
                .profile(profile)
                .school(dto.school().trim())
                .degree(blankToNull(dto.degree()))
                .fieldOfStudy(blankToNull(dto.fieldOfStudy()))
                .startYear(dto.startYear())
                .endYear(dto.endYear())
                .userEdited(true)
                .displayOrder(order)
                .build();
    }

    public ProfileSkill newSkill(CandidateProfile profile, ProfileSkillDto dto, short order) {
        return ProfileSkill.builder()
                .profile(profile)
                .name(normalizeSkillName(dto.name()))
                .category(blankToNull(dto.category()))
                .userEdited(true)
                .displayOrder(order)
                .build();
    }

    public ProfileProject newProject(CandidateProfile profile, ProfileProjectDto dto, short order) {
        return ProfileProject.builder()
                .profile(profile)
                .name(dto.name().trim())
                .description(blankToNull(dto.description()))
                .roleInProject(blankToNull(dto.roleInProject()))
                .techStack(blankToNull(dto.techStack()))
                .startDate(dto.startDate())
                .endDate(dto.endDate())
                .userEdited(true)
                .displayOrder(order)
                .build();
    }

    public void apply(ProfileEducation entity, ProfileEducationDto dto, short order) {
        String school = dto.school().trim();
        String degree = blankToNull(dto.degree());
        String fieldOfStudy = blankToNull(dto.fieldOfStudy());

        if (!Objects.equals(entity.getSchool(), school)
                || !Objects.equals(entity.getDegree(), degree)
                || !Objects.equals(entity.getFieldOfStudy(), fieldOfStudy)
                || !Objects.equals(entity.getStartYear(), dto.startYear())
                || !Objects.equals(entity.getEndYear(), dto.endYear())) {
            entity.setUserEdited(true);
        }

        entity.setSchool(school);
        entity.setDegree(degree);
        entity.setFieldOfStudy(fieldOfStudy);
        entity.setStartYear(dto.startYear());
        entity.setEndYear(dto.endYear());
        entity.setDisplayOrder(order);
    }

    public void apply(ProfileSkill entity, ProfileSkillDto dto, short order) {
        String name = normalizeSkillName(dto.name());
        String category = blankToNull(dto.category());

        if (!Objects.equals(entity.getName(), name)
                || !Objects.equals(entity.getCategory(), category)) {
            entity.setUserEdited(true);
        }

        entity.setName(name);
        entity.setCategory(category);
        entity.setDisplayOrder(order);
    }

    public void apply(ProfileProject entity, ProfileProjectDto dto, short order) {
        String name = dto.name().trim();
        String description = blankToNull(dto.description());
        String roleInProject = blankToNull(dto.roleInProject());
        String techStack = blankToNull(dto.techStack());

        if (!Objects.equals(entity.getName(), name)
                || !Objects.equals(entity.getDescription(), description)
                || !Objects.equals(entity.getRoleInProject(), roleInProject)
                || !Objects.equals(entity.getTechStack(), techStack)
                || !Objects.equals(entity.getStartDate(), dto.startDate())
                || !Objects.equals(entity.getEndDate(), dto.endDate())) {
            entity.setUserEdited(true);
        }

        entity.setName(name);
        entity.setDescription(description);
        entity.setRoleInProject(roleInProject);
        entity.setTechStack(techStack);
        entity.setStartDate(dto.startDate());
        entity.setEndDate(dto.endDate());
        entity.setDisplayOrder(order);
    }

    public static String normalizeSkillName(String raw) {
        String value = blankToNull(raw);
        return value == null ? null : WHITESPACE.matcher(value).replaceAll(" ");
    }

    private static String blankToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String clamp(String raw, int maxLength) {
        String value = blankToNull(raw);
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        log.debug("Cắt một giá trị AI trả về từ {} xuống {} ký tự", value.length(), maxLength);
        return blankToNull(value.substring(0, maxLength));
    }

    private static Short clampYear(Integer year) {
        if (year == null || year < MIN_YEAR || year > MAX_YEAR) {
            return null;
        }
        return year.shortValue();
    }

    private static Short clampEndYear(Short startYear, Short endYear) {
        if (startYear != null && endYear != null && endYear < startYear) {
            log.debug("Bỏ endYear vì nhỏ hơn startYear");
            return null;
        }
        return endYear;
    }

    private static LocalDate clampEndDate(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            log.debug("Bỏ endDate vì sớm hơn startDate");
            return null;
        }
        return endDate;
    }

    private static BigDecimal clampYearsExperience(BigDecimal years) {
        if (years == null) {
            return null;
        }
        BigDecimal bounded = years.max(BigDecimal.ZERO).min(MAX_YEARS_EXPERIENCE);
        return bounded.setScale(1, RoundingMode.HALF_UP);
    }

    private static LocalDate parseFlexibleDate(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }

        try {
            if (ISO_DATE.matcher(value).matches()) {
                return boundedDate(LocalDate.parse(value));
            }
            if (ISO_YEAR_MONTH.matcher(value).matches()) {
                return boundedDate(LocalDate.parse(value + "-01"));
            }
            if (YEAR_ONLY.matcher(value).matches()) {
                return boundedDate(LocalDate.of(Integer.parseInt(value), 1, 1));
            }
            Matcher monthSlashYear = MONTH_SLASH_YEAR.matcher(value);
            if (monthSlashYear.matches()) {
                return boundedDate(LocalDate.of(Integer.parseInt(monthSlashYear.group(2)),
                        Integer.parseInt(monthSlashYear.group(1)), 1));
            }
        } catch (DateTimeException e) {
            log.debug("Ngày AI trả về khớp dạng nhưng không tồn tại, bỏ qua");
            return null;
        }

        log.debug("Ngày AI trả về không theo dạng nào đã biết, bỏ qua");
        return null;
    }

    private static LocalDate boundedDate(LocalDate date) {
        return date.getYear() < MIN_YEAR || date.getYear() > MAX_YEAR ? null : date;
    }

    private static String joinTechStack(List<String> techStack) {
        if (techStack == null) {
            return null;
        }

        String joined = techStack.stream()
                .map(ProfileMapper::blankToNull)
                .filter(Objects::nonNull)
                .distinct()
                .limit(MAX_TECH_ITEMS)
                .collect(Collectors.joining(TECH_STACK_SEPARATOR));

        return clamp(joined, TECH_STACK_LENGTH);
    }

    private static <T> List<T> nonNullItems(List<T> items) {
        return items == null ? List.of() : items.stream().filter(Objects::nonNull).toList();
    }

    private static <T> List<T> limit(List<T> items, int max) {
        List<T> present = nonNullItems(items);
        if (present.size() <= max) {
            return present;
        }
        log.debug("AI trả {} phần tử, giữ {} phần tử đầu", present.size(), max);
        return present.subList(0, max);
    }
}
