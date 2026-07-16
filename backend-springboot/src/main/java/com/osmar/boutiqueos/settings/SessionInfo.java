package com.osmar.boutiqueos.settings;

import java.time.Instant;

public record SessionInfo(Long accountId, Instant expiresAt) {
}
