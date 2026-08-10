package com.baseProject.myBaseProject.service;

import org.springframework.security.core.userdetails.UserDetails;

public interface JwtService {
    String extractUsername(String token);
    String generateAccessToken(UserDetails userDetails);
    boolean isTokenValid(String token, UserDetails userDetails);
}
