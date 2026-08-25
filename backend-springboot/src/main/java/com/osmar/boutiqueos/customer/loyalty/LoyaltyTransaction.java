package com.osmar.boutiqueos.customer.loyalty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "loyalty_transactions")
public class LoyaltyTransaction {

    public enum TransactionType {
        EARNED,      // Puntos acumulados por compra
        REDEEMED,    // Puntos canjeados por premio
        EXPIRED,     // Puntos expirados
        ADJUSTED     // Ajuste manual (regalo, corrección)
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private int points;

    private Long saleId;

    private Long rewardId;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant expiresAt;

    public Long getId() { return id; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }

    public Long getSaleId() { return saleId; }
    public void setSaleId(Long saleId) { this.saleId = saleId; }

    public Long getRewardId() { return rewardId; }
    public void setRewardId(Long rewardId) { this.rewardId = rewardId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
