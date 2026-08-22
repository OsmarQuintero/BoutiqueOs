package com.osmar.boutiqueos.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 20) String phone,
        @Size(max = 1000) String notes
) {
}
