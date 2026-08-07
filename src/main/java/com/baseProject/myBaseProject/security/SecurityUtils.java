package com.baseProject.myBaseProject.security;

import com.baseProject.myBaseProject.enums.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {
    private SecurityUtils() {
    }
    public static Optional<CustomUserDetails> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof CustomUserDetails userDetails
                ? Optional.of(userDetails)
                : Optional.empty();
    }

    public static Optional<Long> currentUserId() {
        return currentUser().map(CustomUserDetails::getId);
    }

    // check
    public static boolean hasRole(UserRole role) {
        return currentUser().map(user -> user.getRole() == role).orElse(false);
    }
}
