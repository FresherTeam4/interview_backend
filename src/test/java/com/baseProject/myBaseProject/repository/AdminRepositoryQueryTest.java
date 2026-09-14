package com.baseProject.myBaseProject.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@Transactional(readOnly = true)
class AdminRepositoryQueryTest {
    @Autowired
    private UserAccountRepository users;

    @Autowired
    private InterviewSessionRepository sessions;

    @Test
    void optionalAdminFiltersExecuteWithNullValues() {
        assertThatCode(() -> users.searchForAdmin(
                null, null, null, PageRequest.of(0, 1)).getTotalElements())
                .doesNotThrowAnyException();

        assertThatCode(() -> sessions.searchForAdmin(
                null, null, null, null, null, PageRequest.of(0, 1))
                .getTotalElements())
                .doesNotThrowAnyException();

        assertThatCode(() -> sessions.countStatusesCreatedAfter(Instant.EPOCH))
                .doesNotThrowAnyException();
    }
}
