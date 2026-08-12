package com.baseProject.myBaseProject.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.baseProject.myBaseProject.entity.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    boolean existsByEmail(String email);

    Optional<UserAccount> findByEmail(String email);

    boolean existsByGoogleId(String googleId);

    Optional<UserAccount> findByGoogleId(String googleId);
}
