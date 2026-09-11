package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.realtime.CreateRealtimeSessionRequest;
import com.baseProject.myBaseProject.dto.realtime.CreateRealtimeResumeRequest;
import com.baseProject.myBaseProject.dto.realtime.DisconnectRealtimeConnectionRequest;
import com.baseProject.myBaseProject.dto.realtime.RealtimeConnectionResponse;
import com.baseProject.myBaseProject.dto.realtime.RealtimeEventBatchRequest;
import com.baseProject.myBaseProject.dto.realtime.RealtimeEventBatchResponse;
import com.baseProject.myBaseProject.dto.realtime.RealtimeSessionGrantResponse;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsAuthenticated;
import com.baseProject.myBaseProject.service.RealtimeInterviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interview-sessions")
@IsAuthenticated
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Phỏng vấn realtime")
public class RealtimeInterviewController {
    private final RealtimeInterviewService service;

    @PostMapping("/{id}/realtime/session-grants")
    @Operation(
            summary = "Cấp quyền kết nối phỏng vấn realtime",
            description = "Tạo ephemeral token và cấu hình voice để client kết nối trực tiếp tới realtime provider.")
    public RealtimeSessionGrantResponse createGrant(
            @CurrentUser CustomUserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody CreateRealtimeSessionRequest request) {
        return service.createGrant(user.getId(), id, request);
    }

    @PostMapping("/{id}/realtime/connections/{connectionId}/events")
    @Operation(
            summary = "Ghi nhận sự kiện phỏng vấn realtime",
            description = "Lưu event theo sequence, chống ghi trùng và chuyển transcript hoàn chỉnh thành interview turn.")
    public RealtimeEventBatchResponse recordEvents(
            @CurrentUser CustomUserDetails user,
            @PathVariable Long id,
            @PathVariable Long connectionId,
            @Valid @RequestBody RealtimeEventBatchRequest request) {
        return service.recordEvents(user.getId(), id, connectionId, request);
    }

    @PostMapping("/{id}/realtime/connections/{connectionId}/resume-grants")
    @Operation(
            summary = "Cấp quyền nối lại phỏng vấn realtime",
            description = "Dùng resumption handle mới nhất để tạo WebSocket grant và connection id mới.")
    public RealtimeSessionGrantResponse resumeGrant(
            @CurrentUser CustomUserDetails user,
            @PathVariable Long id,
            @PathVariable Long connectionId,
            @Valid @RequestBody CreateRealtimeResumeRequest request) {
        return service.resumeGrant(user.getId(), id, connectionId, request);
    }

    @PostMapping("/{id}/realtime/connections/{connectionId}/disconnect")
    @Operation(
            summary = "Đóng kết nối phỏng vấn realtime",
            description = "Lưu lý do/latency và có thể chuyển session sang voice turn-based khi realtime không phục hồi được.")
    public RealtimeConnectionResponse disconnect(
            @CurrentUser CustomUserDetails user,
            @PathVariable Long id,
            @PathVariable Long connectionId,
            @Valid @RequestBody DisconnectRealtimeConnectionRequest request) {
        return service.disconnect(user.getId(), id, connectionId, request);
    }
}
