package com.osmar.boutiqueos.customer;

import jakarta.validation.constraints.NotBlank;

public record CustomerRequest(
        @NotBlank String name,
        String phone,
        String notes
) {
}
