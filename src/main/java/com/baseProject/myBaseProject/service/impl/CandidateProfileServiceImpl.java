package com.baseProject.myBaseProject.service.impl;

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
import com.baseProject.myBaseProject.exception.DuplicateSkillNameException;
import com.baseProject.myBaseProject.exception.ProfileItemNotFoundException;
import com.baseProject.myBaseProject.exception.ProfileNotFoundException;
import com.baseProject.myBaseProject.mapper.ProfileMapper;
import com.baseProject.myBaseProject.repository.CandidateProfileRepository;
import com.baseProject.myBaseProject.repository.ProfileEducationRepository;
import com.baseProject.myBaseProject.repository.ProfileProjectRepository;
import com.baseProject.myBaseProject.repository.ProfileSkillRepository;
import com.baseProject.myBaseProject.service.CandidateProfileService;

import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Hồ sơ ứng viên. Xem {@link CandidateProfileService} về hợp đồng.
 *
 * <p>Ba danh sách con được lấy bằng ba truy vấn riêng, không bằng {@code @OneToMany}:
 * {@link CandidateProfile} cố ý không khai báo quan hệ ngược, nên đọc code là thấy rõ chỗ nào
 * tốn bao nhiêu truy vấn, và không có truy vấn nào phát sinh lúc Jackson serialize.
 *
 * <p>Thứ tự ghi khi sửa hồ sơ là phần tinh tế nhất của lớp này, xem {@link #update}.
 */
@Service
@RequiredArgsConstructor
public class CandidateProfileServiceImpl implements CandidateProfileService {

    private final CandidateProfileRepository candidateProfileRepository;
    private final ProfileEducationRepository educationRepository;
    private final ProfileSkillRepository skillRepository;
    private final ProfileProjectRepository projectRepository;
    private final ProfileMapper profileMapper;
    private final EntityManager entityManager;
    private final Clock clock;

    @Override
    @Transactional
    public void createFromParse(CvDocument document, CvParsedPayload payload, Instant now) {
        if (candidateProfileRepository.existsByCvDocumentId(document.getId())) {
            throw new IllegalStateException(
                    "cv_documents id=" + document.getId() + " đã có hồ sơ, không dựng thêm");
        }

        CandidateProfile profile =
                candidateProfileRepository.save(profileMapper.newProfile(document, payload, now));

        educationRepository.saveAll(profileMapper.newEducations(profile, payload));
        skillRepository.saveAll(profileMapper.newSkills(profile, payload));
        projectRepository.saveAll(profileMapper.newProjects(profile, payload));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfileSummaryResponse> list(Long userId) {
        return candidateProfileRepository
                .findByUserIdAndCvDocumentActiveTrueOrderByCreatedAtDesc(userId).stream()
                .map(profile -> profileMapper.toSummary(profile,
                        Math.toIntExact(educationRepository.countByProfileId(profile.getId())),
                        Math.toIntExact(skillRepository.countByProfileId(profile.getId())),
                        Math.toIntExact(projectRepository.countByProfileId(profile.getId()))))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CandidateProfileResponse get(Long userId, Long profileId) {
        return toResponse(requireProfile(userId, profileId));
    }

    @Override
    @Transactional
    public CandidateProfileResponse update(Long userId, Long profileId, ProfileUpdateRequest request) {
        CandidateProfile profile = requireProfile(userId, profileId);

        rejectDuplicateSkillNames(request.skills());

        ChildPlan<ProfileEducation> educations = planChildren(
                educationRepository.findByProfileIdOrderByDisplayOrderAsc(profileId),
                request.educations(),
                ProfileEducation::getId, ProfileEducationDto::id,
                ProfileItemNotFoundException::education);

        ChildPlan<ProfileSkill> skills = planChildren(
                skillRepository.findByProfileIdOrderByDisplayOrderAsc(profileId),
                request.skills(),
                ProfileSkill::getId, ProfileSkillDto::id,
                ProfileItemNotFoundException::skill);

        ChildPlan<ProfileProject> projects = planChildren(
                projectRepository.findByProfileIdOrderByDisplayOrderAsc(profileId),
                request.projects(),
                ProfileProject::getId, ProfileProjectDto::id,
                ProfileItemNotFoundException::project);

        profileMapper.applyScalars(profile, request, clock.instant());

        // delete and update change to db
        educationRepository.deleteAll(educations.removals());
        skillRepository.deleteAll(skills.removals());
        projectRepository.deleteAll(projects.removals());
        entityManager.flush();

        // update old data and create new data
        List<ProfileEducation> newEducations = new ArrayList<>();
        for (int i = 0; i < request.educations().size(); i++) {
            ProfileEducationDto dto = request.educations().get(i);
            if (dto.id() == null) {
                newEducations.add(profileMapper.newEducation(profile, dto, (short) i));
            } else {
                profileMapper.apply(educations.byId(dto.id()), dto, (short) i);
            }
        }

        List<ProfileSkill> newSkills = new ArrayList<>();
        for (int i = 0; i < request.skills().size(); i++) {
            ProfileSkillDto dto = request.skills().get(i);
            if (dto.id() == null) {
                newSkills.add(profileMapper.newSkill(profile, dto, (short) i));
            } else {
                profileMapper.apply(skills.byId(dto.id()), dto, (short) i);
            }
        }

        List<ProfileProject> newProjects = new ArrayList<>();
        for (int i = 0; i < request.projects().size(); i++) {
            ProfileProjectDto dto = request.projects().get(i);
            if (dto.id() == null) {
                newProjects.add(profileMapper.newProject(profile, dto, (short) i));
            } else {
                profileMapper.apply(projects.byId(dto.id()), dto, (short) i);
            }
        }
        entityManager.flush(); // save old data update

        // insert new data
        educationRepository.saveAll(newEducations);
        skillRepository.saveAll(newSkills);
        projectRepository.saveAll(newProjects);
        entityManager.flush();

        return toResponse(profile);
    }

    @Override
    @Transactional
    public CandidateProfileResponse confirm(Long userId, Long profileId) {
        CandidateProfile profile = requireProfile(userId, profileId);

        if (!profile.isConfirmed()) {
            Instant now = clock.instant();
            profile.setConfirmedAt(now);
            profile.setUpdatedAt(now);

            entityManager.flush();
        }

        return toResponse(profile);
    }

    private CandidateProfile requireProfile(Long userId, Long profileId) {
        return candidateProfileRepository.findByIdAndUserIdAndCvDocumentActiveTrue(profileId, userId)
                .orElseThrow(ProfileNotFoundException::new);
    }

    private void rejectDuplicateSkillNames(List<ProfileSkillDto> skills) {
        Set<String> seen = new HashSet<>();
        for (ProfileSkillDto skill : skills) {
            // name đã qua @NotBlank nên normalizeSkillName không thể trả null.
            String normalized = ProfileMapper.normalizeSkillName(skill.name());
            if (!seen.add(normalized.toLowerCase(Locale.ROOT))) {
                throw new DuplicateSkillNameException(normalized);
            }
        }
    }

    //get keep and remove item ids
    private <E, D> ChildPlan<E> planChildren(List<E> existing,
                                             List<D> payload,
                                             Function<E, Long> entityId,
                                             Function<D, Long> dtoId,
                                             Function<Long, RuntimeException> notFound) {

        // convert to map
        Map<Long, E> existingById = new LinkedHashMap<>();
        for (E entity : existing) {
            existingById.put(entityId.apply(entity), entity);
        }

        // check diff
        Set<Long> keptIds = new HashSet<>();
        for (D dto : payload) {
            Long id = dtoId.apply(dto);
            if (id == null) {
                continue;
            }

            if (!existingById.containsKey(id)) {
                throw notFound.apply(id);
            }

            keptIds.add(id);
        }

        List<E> removals = new ArrayList<>();
        existingById.forEach((id, entity) -> {
            if (!keptIds.contains(id)) {
                removals.add(entity);
            }
        });

        return new ChildPlan<>(existingById, removals);
    }


    private record ChildPlan<E>(Map<Long, E> existingById, List<E> removals) {
        E byId(Long id) {
            return existingById.get(id);
        }
    }

    private CandidateProfileResponse toResponse(CandidateProfile profile) {
        Long profileId = profile.getId();

        return profileMapper.toResponse(profile,
                educationRepository.findByProfileIdOrderByDisplayOrderAsc(profileId),
                skillRepository.findByProfileIdOrderByDisplayOrderAsc(profileId),
                projectRepository.findByProfileIdOrderByDisplayOrderAsc(profileId));
    }
}
