package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.InterviewFocusArea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewFocusAreaRepository extends JpaRepository<InterviewFocusArea, Long> {
    List<InterviewFocusArea> findBySessionIdOrderByDisplayOrderAsc(Long sessionId);

    void deleteBySessionId(Long sessionId);
}
