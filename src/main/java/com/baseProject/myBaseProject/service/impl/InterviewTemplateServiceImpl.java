package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.template.InterviewTemplateResponse;
import com.baseProject.myBaseProject.dto.template.InterviewTemplateSummaryResponse;
import com.baseProject.myBaseProject.dto.template.TemplatePageResponse;
import com.baseProject.myBaseProject.dto.template.UpdateInterviewTemplateRequest;
import com.baseProject.myBaseProject.entity.InterviewTemplate;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.jobdescription.mapper.JobAnalysisJsonMapper;
import com.baseProject.myBaseProject.jobdescription.validation.JobAnalysisValidator;
import com.baseProject.myBaseProject.mapper.InterviewTemplateMapper;
import com.baseProject.myBaseProject.repository.InterviewTemplateRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.InterviewTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewTemplateServiceImpl implements InterviewTemplateService {
    private final InterviewTemplateRepository templates;
    private final UserAccountRepository users;
    private final InterviewTemplateMapper mapper;
    private final JobAnalysisJsonMapper analysisJsonMapper;
    private final JobAnalysisValidator validator;
    private final Clock clock;

    @Override
    public InterviewTemplateResponse get(Long userId, Long id) {
        InterviewTemplate template = templates.findByIdAndOwnerId(id, userId).orElse(null);
        if (template == null) {
            template = templates.findByIdAndPublishedAtIsNotNullAndArchivedAtIsNull(id)
                    .orElseThrow(this::notFound);
        }

        return mapper.toResponse(template);
    }

    @Override
    public TemplatePageResponse<InterviewTemplateSummaryResponse> list(
            Long userId, String scope, int page, int size) {
        PageRequest pageable = pageRequest(page, size);
        if ("mine".equals(scope)) {
            return page(templates.findByOwnerId(userId, pageable).map(mapper::toSummary));
        }
        if ("public".equals(scope)) {
            return page(templates.findByPublishedAtIsNotNullAndArchivedAtIsNull(pageable)
                    .map(mapper::toSummary));
        }

        throw new DomainException(ErrorCode.VALIDATION_FAILED);
    }

    @Override
    @Transactional
    public InterviewTemplateResponse update(
            Long userId, Long id, UpdateInterviewTemplateRequest request) {
        if (request == null || request.content() == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        InterviewTemplate template = ownedForUpdate(userId, id);

        requireActive(template);
        requireDraft(template);
        requireVersion(template, requireExpectedVersion(request.expectedVersion()));
        String title = normalizeTitle(request.title());
        var content = validator.validate(request.content());

        template.setTitle(title);
        template.setJobTitle(content.jobTitle());
        template.setTargetSeniority(content.targetSeniority());
        template.setContentJson(analysisJsonMapper.toJson(content));
        touch(template);
        templates.flush();

        return mapper.toResponse(template);
    }

    @Override
    @Transactional
    public InterviewTemplateResponse confirm(
            Long userId, Long id, long expectedVersion) {
        InterviewTemplate template = ownedForUpdate(userId, id);

        requireActive(template);
        if (template.isConfirmed()) {
            return mapper.toResponse(template);
        }
        requireVersion(template, expectedVersion);
        Instant now = clock.instant();

        template.setConfirmedAt(now);
        template.setUpdatedAt(now);
        templates.flush();

        return mapper.toResponse(template);
    }

    @Override
    @Transactional
    public InterviewTemplateResponse publish(
            Long userId, Long id, long expectedVersion) {
        requireAdmin(userId);
        InterviewTemplate template = ownedForUpdate(userId, id);

        requireActive(template);
        requireConfirmed(template);
        if (template.isPublished()) {
            return mapper.toResponse(template);
        }
        requireVersion(template, expectedVersion);
        Instant now = clock.instant();

        template.setPublishedAt(now);
        template.setUpdatedAt(now);
        templates.flush();

        return mapper.toResponse(template);
    }

    @Override
    @Transactional
    public InterviewTemplateResponse unpublish(Long userId, Long id, long expectedVersion) {
        requireAdmin(userId);
        InterviewTemplate template = ownedForUpdate(userId, id);

        requireActive(template);
        if (!template.isPublished()) {
            return mapper.toResponse(template);
        }
        requireVersion(template, expectedVersion);

        template.setPublishedAt(null);
        touch(template);
        templates.flush();

        return mapper.toResponse(template);
    }

    @Override
    @Transactional
    public InterviewTemplateResponse archive(Long userId, Long id, long expectedVersion) {
        InterviewTemplate template = ownedForUpdate(userId, id);

        if (template.getArchivedAt() != null) {
            return mapper.toResponse(template);
        }
        requireVersion(template, expectedVersion);

        template.setArchivedAt(clock.instant());
        template.setPublishedAt(null);
        touch(template);
        templates.flush();

        return mapper.toResponse(template);
    }

    // helper

    private InterviewTemplate ownedForUpdate(Long userId, Long id) {
        return templates.findOwnedForUpdate(id, userId).orElseThrow(this::notFound);
    }

    private void requireAdmin(Long userId) {
        var user = users.findById(userId)
                .orElseThrow(() -> new DomainException(ErrorCode.AUTHENTICATION_REQUIRED));
        if (!user.isEnabled() || user.getRole() != UserRole.ADMIN) {
            throw new DomainException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void requireActive(InterviewTemplate template) {
        if (template.getArchivedAt() != null) {
            throw new DomainException(ErrorCode.TEMPLATE_ARCHIVED);
        }
    }

    private void requireDraft(InterviewTemplate template) {
        if (template.isConfirmed()) {
            throw new DomainException(ErrorCode.TEMPLATE_ALREADY_CONFIRMED);
        }
    }

    private void requireConfirmed(InterviewTemplate template) {
        if (!template.isConfirmed()) {
            throw new DomainException(ErrorCode.TEMPLATE_CONFIRM_REQUIRED);
        }
    }

    private void requireVersion(InterviewTemplate template, long expectedVersion) {
        if (expectedVersion < 0) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        if (template.getVersion() != expectedVersion) {
            throw new DomainException(ErrorCode.TEMPLATE_VERSION_CONFLICT);
        }
    }

    private long requireExpectedVersion(Long expectedVersion) {
        if (expectedVersion == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        return expectedVersion;
    }

    private String normalizeTitle(String value) {
        if (value == null || value.isBlank() || value.length() > 200) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        return value.strip();
    }

    private void touch(InterviewTemplate template) {
        Instant now = clock.instant();
        template.setUpdatedAt(now.isAfter(template.getUpdatedAt())
                ? now : template.getUpdatedAt().plusNanos(1000));
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    private <T> TemplatePageResponse<T> page(Page<T> page) {
        return new TemplatePageResponse<>(page.getContent(), page.getNumber(),
                page.getSize(), page.getTotalElements());
    }

    private DomainException notFound() {
        return new DomainException(ErrorCode.TEMPLATE_NOT_FOUND);
    }
}
