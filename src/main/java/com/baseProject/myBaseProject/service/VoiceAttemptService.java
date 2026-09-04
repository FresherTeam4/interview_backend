package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.interview.TextAnswerAcceptedResponse;
import com.baseProject.myBaseProject.dto.interview.VoiceAttemptConfirmRequest;
import com.baseProject.myBaseProject.dto.interview.VoiceAttemptResponse;
import com.baseProject.myBaseProject.dto.interview.VoiceAttemptUploadRequest;
import com.baseProject.myBaseProject.dto.interview.VoiceTranscriptUpdateRequest;

import org.springframework.web.multipart.MultipartFile;

public interface VoiceAttemptService {

    VoiceAttemptResponse upload(
            Long userId,
            Long sessionId,
            VoiceAttemptUploadRequest request,
            MultipartFile file);

    VoiceAttemptResponse get(Long userId, Long sessionId, Long attemptId);

    VoiceAttemptResponse editTranscript(
            Long userId,
            Long sessionId,
            Long attemptId,
            VoiceTranscriptUpdateRequest request);

    TextAnswerAcceptedResponse confirm(
            Long userId,
            Long sessionId,
            Long attemptId,
            VoiceAttemptConfirmRequest request);
}
