package com.osmar.boutiqueos.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findAllByAccountIdOrderByCreatedAtDesc(Long accountId);
    List<Customer> findByAccountIdAndNameContainingIgnoreCaseOrderByCreatedAtDesc(Long accountId, String query);
    java.util.Optional<Customer> findByIdAndAccountId(Long id, Long accountId);
    long countByAccountId(Long accountId);
    void deleteByIdAndAccountId(Long id, Long accountId);

    @Query("SELECT DISTINCT c.accountId FROM Customer c")
    List<Long> findDistinctAccountIds();

    List<Customer> findAllByAccountId(Long accountId);
    void deleteAllByAccountId(Long accountId);
}
