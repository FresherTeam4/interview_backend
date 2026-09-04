package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.interview.VoiceAttemptResponse;
import com.baseProject.myBaseProject.dto.interview.VoiceAttemptUploadRequest;

import org.springframework.web.multipart.MultipartFile;

public interface VoiceAttemptService {

    VoiceAttemptResponse upload(
            Long userId,
            Long sessionId,
            VoiceAttemptUploadRequest request,
            MultipartFile file);

    VoiceAttemptResponse get(Long userId, Long sessionId, Long attemptId);
}
