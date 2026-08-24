package com.baseProject.myBaseProject.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baseProject.myBaseProject.constant.Message;
import com.baseProject.myBaseProject.dto.cv.CvParseOutcome;
import com.baseProject.myBaseProject.dto.cv.CvParsePayload;
import com.baseProject.myBaseProject.dto.profile.CandidateProfileResponse;
import com.baseProject.myBaseProject.dto.profile.UpdateCandidateProfileRequest;
import com.baseProject.myBaseProject.entity.CandidateProfile;
import com.baseProject.myBaseProject.entity.CvDocument;
import com.baseProject.myBaseProject.entity.CvParseResult;
import com.baseProject.myBaseProject.entity.ProfileEducation;
import com.baseProject.myBaseProject.entity.ProfileProject;
import com.baseProject.myBaseProject.entity.ProfileSkill;
import com.baseProject.myBaseProject.enums.CvDocumentStatus;
import com.baseProject.myBaseProject.enums.ProfileSource;
import com.baseProject.myBaseProject.exception.InvalidProfileDataException;
import com.baseProject.myBaseProject.exception.ResourceNotFoundException;
import com.baseProject.myBaseProject.repository.CandidateProfileRepository;
import com.baseProject.myBaseProject.repository.CvDocumentRepository;
import com.baseProject.myBaseProject.repository.CvParseResultRepository;
import com.baseProject.myBaseProject.repository.ProfileEducationRepository;
import com.baseProject.myBaseProject.repository.ProfileProjectRepository;
import com.baseProject.myBaseProject.repository.ProfileSkillRepository;
import com.baseProject.myBaseProject.service.CandidateProfileService;

import lombok.RequiredArgsConstructor;

/**
 * Hồ sơ ứng viên: dựng từ kết quả AI đọc CV (US-2), sau đó người dùng sửa lại và xác nhận (US-3).
 *
 * <p>Mỗi user chỉ có một hồ sơ, trỏ tới CV đang dùng. Ba bảng con (học vấn / kỹ năng / dự án)
 * luôn được ghi đè toàn bộ: xoá hết rồi chèn lại theo đúng danh sách nhận được, nên không cần
 * logic so khớp từng dòng.
 */
@Service
@RequiredArgsConstructor
public class CandidateProfileServiceImpl implements CandidateProfileService {
    private final CandidateProfileRepository profileRepository;
    private final ProfileEducationRepository educationRepository;
    private final ProfileSkillRepository skillRepository;
    private final ProfileProjectRepository projectRepository;
    private final CvDocumentRepository cvDocumentRepository;
    private final CvParseResultRepository parseResultRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public CandidateProfileResponse currentProfile(Long userId) {
        return toResponse(requireProfile(userId));
    }

    @Override
    @Transactional
    public CandidateProfileResponse update(Long userId, UpdateCandidateProfileRequest request) {
        CandidateProfile profile = requireProfile(userId);
        validateDateRanges(request);

        profile.setHeadline(trimToNull(request.headline()));
        profile.setYearsExperience(scaleYears(request.yearsExperience()));
        profile.setTargetPosition(trimToNull(request.targetPosition()));
        profile.setSeniorityLevel(request.seniorityLevel());
        profile.setSource(ProfileSource.USER_EDITED);
        // Sửa xong phải xác nhận lại, tránh mang hồ sơ chưa kiểm tra vào buổi phỏng vấn.
        profile.setConfirmedAt(null);
        profileRepository.save(profile);

        deleteChildren(profile.getId());
        saveEducations(profile, request.educationList());
        saveSkills(profile, request.skillList());
        saveProjects(profile, request.projectList());

        return toResponse(profile);
    }

    @Override
    @Transactional
    public CandidateProfileResponse confirm(Long userId) {
        CandidateProfile profile = requireProfile(userId);
        profile.setConfirmedAt(clock.instant());
        profileRepository.save(profile);
        return toResponse(profile);
    }

    /**
     * Một transaction duy nhất: hồ sơ + 3 bảng con + cv_parse_results + trạng thái CV.
     * Lỗi ở bất kỳ bước nào cũng rollback hết, không để hồ sơ nửa vời.
     */
    @Override
    @Transactional
    public void applyParseResult(Long cvDocumentId, CvParseOutcome outcome) {
        CvDocument document = cvDocumentRepository.findById(cvDocumentId)
                .orElseThrow(() -> new ResourceNotFoundException(Message.CV_NOT_FOUND));
        CvParsePayload payload = outcome.payload();
        Instant now = clock.instant();

        CandidateProfile profile = profileRepository.findByUserId(document.getUser().getId())
                .orElseGet(() -> CandidateProfile.builder()
                        .user(document.getUser())
                        .createdAt(now)
                        .build());
        profile.setCvDocument(document);
        profile.setHeadline(CvParsePayload.text(payload.headline(), CvParsePayload.MAX_HEADLINE));
        profile.setYearsExperience(payload.yearsExperienceValue());
        profile.setTargetPosition(
                CvParsePayload.text(payload.targetPosition(), CvParsePayload.MAX_TARGET_POSITION));
        profile.setSeniorityLevel(payload.seniorityLevelValue());
        profile.setSource(ProfileSource.AUTO_PARSED);
        // Hồ sơ vừa dựng lại từ CV mới nên coi như chưa được người dùng xác nhận.
        profile.setConfirmedAt(null);
        profile = profileRepository.save(profile);

        deleteChildren(profile.getId());
        saveParsedEducations(profile, payload.educationList());
        saveParsedSkills(profile, payload.skillList());
        saveParsedProjects(profile, payload.projectList());
        saveParseResult(document, outcome, now);

        document.setStatus(CvDocumentStatus.PARSED);
        document.setStatusMessage(null);
        document.setParsedAt(now);
        cvDocumentRepository.save(document);
    }

    // ------------------------------------------------------------------ //
    //  Lookup & mapping                                                    //
    // ------------------------------------------------------------------ //

    private CandidateProfile requireProfile(Long userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(Message.PROFILE_NOT_FOUND));
    }

    private CandidateProfileResponse toResponse(CandidateProfile profile) {
        Long profileId = profile.getId();
        return CandidateProfileResponse.of(
                profile,
                educationRepository.findByProfileIdOrderByDisplayOrderAsc(profileId),
                skillRepository.findByProfileIdOrderByDisplayOrderAsc(profileId),
                projectRepository.findByProfileIdOrderByDisplayOrderAsc(profileId));
    }

    // ------------------------------------------------------------------ //
    //  Xoá & ghi lại bảng con                                             //
    // ------------------------------------------------------------------ //

    private void deleteChildren(Long profileId) {
        educationRepository.deleteByProfileId(profileId);
        skillRepository.deleteByProfileId(profileId);
        projectRepository.deleteByProfileId(profileId);
    }

    /* ---------- US-3: lưu dữ liệu người dùng sửa ---------- */

    private void saveEducations(CandidateProfile profile,
                                List<UpdateCandidateProfileRequest.EducationRequest> list) {
        List<ProfileEducation> entities = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            var req = list.get(i);
            entities.add(ProfileEducation.builder()
                    .profile(profile)
                    .school(req.school())
                    .degree(req.degree())
                    .fieldOfStudy(req.fieldOfStudy())
                    .startYear(req.startYear())
                    .endYear(req.endYear())
                    .userEdited(true)
                    .displayOrder(i)
                    .build());
        }
        educationRepository.saveAll(entities);
    }

    private void saveSkills(CandidateProfile profile,
                            List<UpdateCandidateProfileRequest.SkillRequest> list) {
        // Loại trùng tên, giữ phần tử đầu tiên.
        Map<String, UpdateCandidateProfileRequest.SkillRequest> unique = new LinkedHashMap<>();
        for (var req : list) {
            unique.putIfAbsent(req.name().trim().toLowerCase(), req);
        }

        List<ProfileSkill> entities = new ArrayList<>();
        int order = 0;
        for (var req : unique.values()) {
            entities.add(ProfileSkill.builder()
                    .profile(profile)
                    .name(req.name().trim())
                    .category(req.category())
                    .userEdited(true)
                    .displayOrder(order++)
                    .build());
        }
        skillRepository.saveAll(entities);
    }

    private void saveProjects(CandidateProfile profile,
                              List<UpdateCandidateProfileRequest.ProjectRequest> list) {
        List<ProfileProject> entities = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            var req = list.get(i);
            entities.add(ProfileProject.builder()
                    .profile(profile)
                    .name(req.name())
                    .description(req.description())
                    .roleInProject(req.roleInProject())
                    .techStack(req.techStack())
                    .startDate(req.startDate())
                    .endDate(req.endDate())
                    .userEdited(true)
                    .displayOrder(i)
                    .build());
        }
        projectRepository.saveAll(entities);
    }

    /* ---------- US-2: lưu dữ liệu AI bóc tách ---------- */

    private void saveParsedEducations(CandidateProfile profile,
                                      List<CvParsePayload.ParsedEducation> list) {
        List<ProfileEducation> entities = new ArrayList<>();
        int order = 0;
        for (var parsed : list) {
            String school = CvParsePayload.text(parsed.school(), CvParsePayload.MAX_SCHOOL);
            if (school == null) continue; // bỏ mục không có tên trường
            entities.add(ProfileEducation.builder()
                    .profile(profile)
                    .school(school)
                    .degree(CvParsePayload.text(parsed.degree(), CvParsePayload.MAX_DEGREE))
                    .fieldOfStudy(CvParsePayload.text(parsed.fieldOfStudy(), CvParsePayload.MAX_FIELD_OF_STUDY))
                    .startYear(parsed.startYear())
                    .endYear(parsed.endYearValue())
                    .displayOrder(order++)
                    .build());
        }
        educationRepository.saveAll(entities);
    }

    private void saveParsedSkills(CandidateProfile profile,
                                  List<CvParsePayload.ParsedSkill> list) {
        Map<String, CvParsePayload.ParsedSkill> unique = new LinkedHashMap<>();
        for (var parsed : list) {
            String name = CvParsePayload.text(parsed.name(), CvParsePayload.MAX_SKILL_NAME);
            if (name != null) {
                unique.putIfAbsent(name.toLowerCase(), parsed);
            }
        }

        List<ProfileSkill> entities = new ArrayList<>();
        int order = 0;
        for (var entry : unique.entrySet()) {
            var parsed = entry.getValue();
            entities.add(ProfileSkill.builder()
                    .profile(profile)
                    .name(CvParsePayload.text(parsed.name(), CvParsePayload.MAX_SKILL_NAME))
                    .category(parsed.categoryValue())
                    .displayOrder(order++)
                    .build());
        }
        skillRepository.saveAll(entities);
    }

    private void saveParsedProjects(CandidateProfile profile,
                                    List<CvParsePayload.ParsedProject> list) {
        List<ProfileProject> entities = new ArrayList<>();
        int order = 0;
        for (var parsed : list) {
            String name = CvParsePayload.text(parsed.name(), CvParsePayload.MAX_PROJECT_NAME);
            if (name == null) continue;
            entities.add(ProfileProject.builder()
                    .profile(profile)
                    .name(name)
                    .description(parsed.description())
                    .roleInProject(CvParsePayload.text(parsed.roleInProject(), CvParsePayload.MAX_ROLE_IN_PROJECT))
                    .techStack(parsed.techStack())
                    .startDate(parsed.startDateValue())
                    .endDate(parsed.endDateValue())
                    .displayOrder(order++)
                    .build());
        }
        projectRepository.saveAll(entities);
    }

    // ------------------------------------------------------------------ //
    //  cv_parse_results                                                    //
    // ------------------------------------------------------------------ //

    private void saveParseResult(CvDocument document, CvParseOutcome outcome, Instant now) {
        CvParseResult result = CvParseResult.builder()
                .cvDocument(document)
                .rawJson(outcome.rawJson())
                .schemaVersion(CvParsePayload.SCHEMA_VERSION)
                .modelName(outcome.modelName())
                .durationMs(outcome.durationMs())
                .tokenCost(outcome.tokenCost())
                .createdAt(now)
                .build();
        parseResultRepository.save(result);
    }

    // ------------------------------------------------------------------ //
    //  Validation & utility                                                //
    // ------------------------------------------------------------------ //

    /**
     * Kiểm tra năm/ngày bắt đầu ≤ kết thúc cho education và project.
     * Bean validation chỉ kiểm tra từng field đơn lẻ, ràng buộc chéo giữa 2 field
     * phải kiểm tra ở đây.
     */
    private void validateDateRanges(UpdateCandidateProfileRequest request) {
        for (var edu : request.educationList()) {
            if (edu.startYear() != null && edu.endYear() != null
                    && edu.endYear() < edu.startYear()) {
                throw new InvalidProfileDataException(
                        Message.PROFILE_EDUCATION_YEARS_INVALID.formatted(edu.school()));
            }
        }
        for (var proj : request.projectList()) {
            if (proj.startDate() != null && proj.endDate() != null
                    && proj.endDate().isBefore(proj.startDate())) {
                throw new InvalidProfileDataException(
                        Message.PROFILE_PROJECT_DATES_INVALID.formatted(proj.name()));
            }
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static BigDecimal scaleYears(BigDecimal value) {
        if (value == null) return null;
        return value.setScale(1, RoundingMode.HALF_UP);
    }
}
