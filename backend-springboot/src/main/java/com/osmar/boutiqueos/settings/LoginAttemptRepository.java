package com.osmar.boutiqueos.settings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, AttemptKey> {

    @Modifying
    @Transactional
    @Query("DELETE FROM LoginAttempt la WHERE la.windowExpiresAt < :now AND (la.blockedUntil IS NULL OR la.blockedUntil < :now)")
    int deleteExpired(Instant now);
}
