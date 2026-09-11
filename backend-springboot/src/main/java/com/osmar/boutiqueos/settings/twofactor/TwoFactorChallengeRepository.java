package com.osmar.boutiqueos.settings.twofactor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TwoFactorChallengeRepository extends JpaRepository<TwoFactorChallenge, String> {
    List<TwoFactorChallenge> findByAccountIdAndPurposeAndUsedAtIsNull(Long accountId, TwoFactorPurpose purpose);
    Optional<TwoFactorChallenge> findFirstByAccountIdAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(
            Long accountId, TwoFactorPurpose purpose);
    void deleteByExpiresAtBefore(Instant cutoff);
}
