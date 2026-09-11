package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.InterviewVoiceConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InterviewVoiceConnectionRepository
        extends JpaRepository<InterviewVoiceConnection, Long> {

    Optional<InterviewVoiceConnection> findByIdAndSessionId(Long id, Long sessionId);

    Optional<InterviewVoiceConnection> findFirstBySessionIdAndDisconnectedAtIsNullOrderByCreatedAtDesc(
            Long sessionId);
}
