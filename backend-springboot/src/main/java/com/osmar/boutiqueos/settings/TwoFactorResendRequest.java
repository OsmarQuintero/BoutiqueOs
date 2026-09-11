package com.osmar.boutiqueos.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TwoFactorResendRequest(@NotBlank @Size(max = 36) String challengeId) {
}
