package com.osmar.boutiqueos.settings;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "login_attempts")
public class LoginAttempt {

    @EmbeddedId
    private AttemptKey id;

    @Column(nullable = false)
    private int count;

    @Column(nullable = false)
    private Instant windowExpiresAt;

    private Instant blockedUntil;

    public AttemptKey getId() { return id; }
    public void setId(AttemptKey id) { this.id = id; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
    public Instant getWindowExpiresAt() { return windowExpiresAt; }
    public void setWindowExpiresAt(Instant windowExpiresAt) { this.windowExpiresAt = windowExpiresAt; }
    public Instant getBlockedUntil() { return blockedUntil; }
    public void setBlockedUntil(Instant blockedUntil) { this.blockedUntil = blockedUntil; }
}
