package com.osmar.boutiqueos.settings.twofactor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Un codigo de verificacion enviado por correo.
 *
 * <p>Nunca se guarda el codigo: solo su hash, amarrado al id del desafio para que
 * el mismo codigo no valga en otro desafio.
 */
@Entity
@Table(name = "two_factor_challenges")
public class TwoFactorChallenge {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private Long accountId;

    /** Cuenta de caja que esta entrando; null = la duena. */
    private Long staffUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TwoFactorPurpose purpose;

    @Column(nullable = false, length = 64)
    private String codeHash;

    @Column(nullable = false, length = 200)
    private String email;

    @Column(nullable = false)
    private int failedAttempts;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant lastSentAt;

    private Instant usedAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public TwoFactorPurpose getPurpose() { return purpose; }
    public void setPurpose(TwoFactorPurpose purpose) { this.purpose = purpose; }
    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public int getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getLastSentAt() { return lastSentAt; }
    public void setLastSentAt(Instant lastSentAt) { this.lastSentAt = lastSentAt; }
    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Long getStaffUserId() { return staffUserId; }
    public void setStaffUserId(Long staffUserId) { this.staffUserId = staffUserId; }
}
