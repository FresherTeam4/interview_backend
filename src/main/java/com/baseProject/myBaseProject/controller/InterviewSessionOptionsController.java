package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.session.InterviewSessionOptionsResponse;
import com.baseProject.myBaseProject.security.authorization.IsAuthenticated;
import com.baseProject.myBaseProject.service.InterviewSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interview-session-options")
@IsAuthenticated
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Phiên phỏng vấn")
public class InterviewSessionOptionsController {
    private final InterviewSessionService service;

    @GetMapping
    @Operation(
            summary = "Lấy tùy chọn phiên phỏng vấn",
            description = "Trả về các ngôn ngữ, thời lượng và phong cách người phỏng vấn được hệ thống hỗ trợ.")
    public InterviewSessionOptionsResponse options() {
        return service.options();
    }
}
