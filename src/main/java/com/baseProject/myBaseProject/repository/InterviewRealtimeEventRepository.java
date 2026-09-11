package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.InterviewRealtimeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewRealtimeEventRepository
        extends JpaRepository<InterviewRealtimeEvent, Long> {

    Optional<InterviewRealtimeEvent> findByConnectionIdAndProviderEventId(
            Long connectionId, String providerEventId);

    Optional<InterviewRealtimeEvent> findByConnectionIdAndSequenceNumber(
            Long connectionId, long sequenceNumber);

    List<InterviewRealtimeEvent> findByConnectionIdAndProcessedAtIsNullOrderBySequenceNumberAsc(
            Long connectionId);
}
