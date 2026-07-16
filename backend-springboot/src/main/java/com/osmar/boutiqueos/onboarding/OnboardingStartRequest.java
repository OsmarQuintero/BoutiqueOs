package com.osmar.boutiqueos.onboarding;

import jakarta.validation.constraints.NotBlank;

public record OnboardingStartRequest(
        @NotBlank String sessionId
) {
}
