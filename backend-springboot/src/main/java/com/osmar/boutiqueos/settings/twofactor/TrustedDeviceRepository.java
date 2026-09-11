package com.osmar.boutiqueos.settings.twofactor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, Long> {
    Optional<TrustedDevice> findByTokenHash(String tokenHash);
    void deleteAllByAccountId(Long accountId);
    void deleteAllByStaffUserId(Long staffUserId);
    void deleteByExpiresAtBefore(Instant cutoff);
}
