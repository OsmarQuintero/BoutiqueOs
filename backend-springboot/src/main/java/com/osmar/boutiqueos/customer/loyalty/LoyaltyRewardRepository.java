package com.osmar.boutiqueos.customer.loyalty;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoyaltyRewardRepository extends JpaRepository<LoyaltyReward, Long> {

    List<LoyaltyReward> findByAccountIdAndActiveTrueOrderByNameAsc(Long accountId);

    List<LoyaltyReward> findByAccountIdOrderByNameAsc(Long accountId);
}
