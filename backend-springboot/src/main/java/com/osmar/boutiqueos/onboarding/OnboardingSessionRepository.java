package com.osmar.boutiqueos.onboarding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OnboardingSessionRepository extends JpaRepository<OnboardingSession, String> {
    Optional<OnboardingSession> findByStripeSessionId(String stripeSessionId);
}
