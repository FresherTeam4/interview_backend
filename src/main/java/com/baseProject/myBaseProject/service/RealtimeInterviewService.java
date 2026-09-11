package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.realtime.CreateRealtimeSessionRequest;
import com.baseProject.myBaseProject.dto.realtime.CreateRealtimeResumeRequest;
import com.baseProject.myBaseProject.dto.realtime.DisconnectRealtimeConnectionRequest;
import com.baseProject.myBaseProject.dto.realtime.RealtimeConnectionResponse;
import com.baseProject.myBaseProject.dto.realtime.RealtimeEventBatchRequest;
import com.baseProject.myBaseProject.dto.realtime.RealtimeEventBatchResponse;
import com.baseProject.myBaseProject.dto.realtime.RealtimeSessionGrantResponse;

public interface RealtimeInterviewService {
    RealtimeSessionGrantResponse createGrant(
            Long userId,
            Long sessionId,
            CreateRealtimeSessionRequest request);

    RealtimeEventBatchResponse recordEvents(
            Long userId,
            Long sessionId,
            Long connectionId,
            RealtimeEventBatchRequest request);

    RealtimeSessionGrantResponse resumeGrant(
            Long userId,
            Long sessionId,
            Long connectionId,
            CreateRealtimeResumeRequest request);

    RealtimeConnectionResponse disconnect(
            Long userId,
            Long sessionId,
            Long connectionId,
            DisconnectRealtimeConnectionRequest request);
}
