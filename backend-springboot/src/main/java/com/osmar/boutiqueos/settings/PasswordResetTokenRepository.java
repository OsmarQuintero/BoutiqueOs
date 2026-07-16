package com.osmar.boutiqueos.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {

    void deleteByAccountId(Long accountId);

    void deleteByExpiresAtBefore(Instant instant);
}
