package com.osmar.boutiqueos.customer.loyalty;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, Long> {

    List<LoyaltyTransaction> findByAccountIdAndCustomerIdOrderByCreatedAtDesc(Long accountId, Long customerId);

    @Modifying
    @Transactional
    @Query("UPDATE LoyaltyTransaction lt SET lt.type = 'EXPIRED' " +
           "WHERE lt.accountId = :accountId AND lt.type = 'EARNED' AND lt.expiresAt < :now")
    int expireOldTransactions(Long accountId, Instant now);

    java.util.List<LoyaltyTransaction> findAllByAccountId(Long accountId);
    void deleteAllByAccountId(Long accountId);
}
