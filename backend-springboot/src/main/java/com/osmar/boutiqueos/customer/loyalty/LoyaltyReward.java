package com.osmar.boutiqueos.customer.loyalty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "loyalty_rewards")
public class LoyaltyReward {

    public enum RewardType {
        PRODUCT,    // Canje por prenda específica
        DISCOUNT    // Canje por descuento en efectivo
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private int pointsRequired;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RewardType rewardType;

    @Column(precision = 12, scale = 2)
    private BigDecimal discountAmount;

    private Long productId;

    @Column(nullable = false)
    private boolean active = true;

    public Long getId() { return id; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getPointsRequired() { return pointsRequired; }
    public void setPointsRequired(int pointsRequired) { this.pointsRequired = pointsRequired; }

    public RewardType getRewardType() { return rewardType; }
    public void setRewardType(RewardType rewardType) { this.rewardType = rewardType; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
