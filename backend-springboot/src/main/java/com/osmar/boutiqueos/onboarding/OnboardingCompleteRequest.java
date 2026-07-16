package com.osmar.boutiqueos.onboarding;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OnboardingCompleteRequest(
        @NotBlank String token,
        @NotBlank String storeName,
        String phone,
        String street,
        String neighborhood,
        String city,
        String postalCode,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password
) {
}
