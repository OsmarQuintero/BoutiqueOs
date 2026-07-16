package com.osmar.boutiqueos.onboarding;

import java.time.Instant;

public record OnboardingStartResponse(
        boolean ready,
        String onboardingToken,
        String email,
        Instant expiresAt
) {
}
