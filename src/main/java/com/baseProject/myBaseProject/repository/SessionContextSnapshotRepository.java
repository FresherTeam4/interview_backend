package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.SessionContextSnapshot;

import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface SessionContextSnapshotRepository
        extends Repository<SessionContextSnapshot, Long> {

    SessionContextSnapshot save(SessionContextSnapshot snapshot);

    Optional<SessionContextSnapshot> findBySessionId(Long sessionId);

    Optional<SessionContextSnapshot> findBySessionIdAndSessionUserId(
            Long sessionId,
            Long userId);
}
