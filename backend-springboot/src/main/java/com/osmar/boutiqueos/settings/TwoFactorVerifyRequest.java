package com.osmar.boutiqueos.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TwoFactorVerifyRequest(
        @NotBlank @Size(max = 36) String challengeId,
        @NotBlank @Size(max = 12) String code,
        Boolean rememberDevice
) {
}
