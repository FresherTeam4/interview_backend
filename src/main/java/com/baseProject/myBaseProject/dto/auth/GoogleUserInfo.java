package com.baseProject.myBaseProject.dto.auth;

public record GoogleUserInfo(
        String googleId,
        String email,
        String fullName,
        String avatarUrl
) { }
