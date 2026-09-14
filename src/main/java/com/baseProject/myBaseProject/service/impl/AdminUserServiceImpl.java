package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.admin.AdminPageResponse;
import com.baseProject.myBaseProject.dto.admin.AdminUserDetailResponse;
import com.baseProject.myBaseProject.dto.admin.AdminUserSummaryResponse;
import com.baseProject.myBaseProject.dto.admin.UpdateUserStatusRequest;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.repository.CvDocumentRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.JobDescriptionDocumentRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.AdminUserService;
import com.baseProject.myBaseProject.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserServiceImpl implements AdminUserService {
    private final UserAccountRepository users;
    private final CvDocumentRepository cvDocuments;
    private final JobDescriptionDocumentRepository jobDescriptions;
    private final InterviewSessionRepository sessions;
    private final RefreshTokenService refreshTokens;
    private final Clock clock;

    @Override
    public AdminPageResponse<AdminUserSummaryResponse> list(
            String keyword, UserRole role, Boolean enabled, int page, int size) {
        Page<UserAccount> result = users.searchForAdmin(
                normalizeKeyword(keyword), role, enabled, pageRequest(page, size));
        return new AdminPageResponse<>(
                result.getContent().stream().map(this::toSummary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserDetailResponse get(Long userId) {
        UserAccount user = users.findById(userId)
                .orElseThrow(() -> new DomainException(ErrorCode.USER_NOT_FOUND));
        return toDetail(user);
    }

    @Override
    @Transactional
    public AdminUserDetailResponse updateStatus(
            Long adminId, Long userId, UpdateUserStatusRequest request) {
        UserAccount user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new DomainException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() == UserRole.ADMIN) {
            throw new DomainException(ErrorCode.ADMIN_USER_STATUS_PROTECTED);
        }

        boolean enabled = request.enabled();
        if (user.isEnabled() != enabled) {
            user.setEnabled(enabled);
            user.setUpdatedAt(nextUpdatedAt(user.getUpdatedAt()));
        }
        if (!enabled) {
            refreshTokens.revokeAllForUser(userId);
        }

        log.info("Admin {} set user {} enabled={}", adminId, userId, enabled);
        return toDetail(user);
    }

    private AdminUserSummaryResponse toSummary(UserAccount user) {
        return new AdminUserSummaryResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    private AdminUserDetailResponse toDetail(UserAccount user) {
        Long userId = user.getId();
        return new AdminUserDetailResponse(
                userId,
                user.getFullName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                new AdminUserDetailResponse.Activity(
                        cvDocuments.countByUserIdAndActiveTrue(userId),
                        jobDescriptions.countByOwnerIdAndActiveTrue(userId),
                        sessions.countByUserId(userId),
                        sessions.countByUserIdAndStatus(
                                userId, InterviewSessionStatus.COMPLETED)));
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.strip().toLowerCase(Locale.ROOT) + "%";
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    private Instant nextUpdatedAt(Instant current) {
        Instant now = clock.instant();
        return now.isAfter(current) ? now : current.plusNanos(1000);
    }
}
