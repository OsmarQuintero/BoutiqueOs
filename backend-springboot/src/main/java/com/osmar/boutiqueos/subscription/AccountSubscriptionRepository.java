package com.osmar.boutiqueos.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AccountSubscriptionRepository extends JpaRepository<AccountSubscription, Long> {
    Optional<AccountSubscription> findByAccountId(Long accountId);
    Optional<AccountSubscription> findByStripeSubscriptionId(String stripeSubscriptionId);
    Optional<AccountSubscription> findByStripeCustomerId(String stripeCustomerId);
}
