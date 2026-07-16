package com.osmar.boutiqueos.onboarding;

public record OnboardingCompleteResponse(
        boolean completed,
        String username
) {
}
