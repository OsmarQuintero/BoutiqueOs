package com.osmar.boutiqueos.subscription;

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
@Table(name = "account_subscriptions")
public class AccountSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanType plan = PlanType.FREE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(unique = true)
    private String stripeCustomerId;

    @Column(unique = true)
    private String stripeSubscriptionId;

    private String stripePriceId;

    private Instant currentPeriodStart;

    private Instant currentPeriodEnd;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public PlanType getPlan() { return plan; }
    public void setPlan(PlanType plan) { this.plan = plan; }

    public SubscriptionStatus getStatus() { return status; }
    public void setStatus(SubscriptionStatus status) { this.status = status; }

    public String getStripeCustomerId() { return stripeCustomerId; }
    public void setStripeCustomerId(String stripeCustomerId) { this.stripeCustomerId = stripeCustomerId; }

    public String getStripeSubscriptionId() { return stripeSubscriptionId; }
    public void setStripeSubscriptionId(String stripeSubscriptionId) { this.stripeSubscriptionId = stripeSubscriptionId; }

    public String getStripePriceId() { return stripePriceId; }
    public void setStripePriceId(String stripePriceId) { this.stripePriceId = stripePriceId; }

    public Instant getCurrentPeriodStart() { return currentPeriodStart; }
    public void setCurrentPeriodStart(Instant currentPeriodStart) { this.currentPeriodStart = currentPeriodStart; }

    public Instant getCurrentPeriodEnd() { return currentPeriodEnd; }
    public void setCurrentPeriodEnd(Instant currentPeriodEnd) { this.currentPeriodEnd = currentPeriodEnd; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIALING;
    }
}
