package com.osmar.boutiqueos.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CredentialsCodeRequest(@NotBlank @Size(max = 100) String currentPassword) {
}
