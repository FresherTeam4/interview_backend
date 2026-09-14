package com.baseProject.myBaseProject.service.impl;

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
import com.baseProject.myBaseProject.service.RefreshTokenService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-09-14T08:00:00Z");

    @Test
    void disablesUserAndRevokesEveryRefreshToken() {
        Fixture fixture = new Fixture();
        UserAccount user = fixture.user(UserRole.USER, true);
        when(fixture.users.findByIdForUpdate(15L)).thenReturn(Optional.of(user));

        var response = fixture.service.updateStatus(
                3L, 15L, new UpdateUserStatusRequest(false));

        assertThat(response.enabled()).isFalse();
        assertThat(user.getUpdatedAt()).isEqualTo(NOW);
        verify(fixture.refreshTokens).revokeAllForUser(15L);
    }

    @Test
    void protectsAdministratorAccountsFromStatusChanges() {
        Fixture fixture = new Fixture();
        UserAccount admin = fixture.user(UserRole.ADMIN, true);
        when(fixture.users.findByIdForUpdate(15L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> fixture.service.updateStatus(
                3L, 15L, new UpdateUserStatusRequest(false)))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.ADMIN_USER_STATUS_PROTECTED));

        verify(fixture.refreshTokens, never()).revokeAllForUser(15L);
    }

    @Test
    void returnsUserActivityCounts() {
        Fixture fixture = new Fixture();
        UserAccount user = fixture.user(UserRole.USER, true);
        when(fixture.users.findById(15L)).thenReturn(Optional.of(user));
        when(fixture.cvDocuments.countByUserIdAndActiveTrue(15L)).thenReturn(2L);
        when(fixture.jobDescriptions.countByOwnerIdAndActiveTrue(15L)).thenReturn(3L);
        when(fixture.sessions.countByUserId(15L)).thenReturn(5L);
        when(fixture.sessions.countByUserIdAndStatus(
                15L, InterviewSessionStatus.COMPLETED)).thenReturn(4L);

        var response = fixture.service.get(15L);

        assertThat(response.activity().activeCvCount()).isEqualTo(2);
        assertThat(response.activity().activeJobDescriptionCount()).isEqualTo(3);
        assertThat(response.activity().interviewSessionCount()).isEqualTo(5);
        assertThat(response.activity().completedInterviewSessionCount()).isEqualTo(4);
    }

    private static final class Fixture {
        private final UserAccountRepository users = mock(UserAccountRepository.class);
        private final CvDocumentRepository cvDocuments = mock(CvDocumentRepository.class);
        private final JobDescriptionDocumentRepository jobDescriptions =
                mock(JobDescriptionDocumentRepository.class);
        private final InterviewSessionRepository sessions =
                mock(InterviewSessionRepository.class);
        private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
        private final AdminUserServiceImpl service = new AdminUserServiceImpl(
                users,
                cvDocuments,
                jobDescriptions,
                sessions,
                refreshTokens,
                Clock.fixed(NOW, ZoneOffset.UTC));

        private UserAccount user(UserRole role, boolean enabled) {
            return UserAccount.builder()
                    .id(15L)
                    .fullName("Minh")
                    .email("minh@example.com")
                    .role(role)
                    .enabled(enabled)
                    .createdAt(NOW.minusSeconds(3600))
                    .updatedAt(NOW.minusSeconds(60))
                    .build();
        }
    }
}
