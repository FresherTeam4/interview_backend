package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.SessionStateTransition;

import org.springframework.data.repository.Repository;

import java.util.List;

public interface SessionStateTransitionRepository
        extends Repository<SessionStateTransition, Long> {

    SessionStateTransition save(SessionStateTransition transition);

    List<SessionStateTransition> findBySessionIdAndSessionUserIdOrderByOccurredAtAscIdAsc(
            Long sessionId,
            Long userId);
}
