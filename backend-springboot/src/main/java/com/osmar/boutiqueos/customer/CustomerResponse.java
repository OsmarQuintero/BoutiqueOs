package com.osmar.boutiqueos.customer;

import java.time.Instant;

public record CustomerResponse(
        Long id,
        String name,
        String phone,
        String notes,
        Instant createdAt
) {
    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(c.getId(), c.getName(), c.getPhone(), c.getNotes(), c.getCreatedAt());
    }
}
