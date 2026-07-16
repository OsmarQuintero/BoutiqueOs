package com.osmar.boutiqueos.customer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findAllByAccountIdOrderByCreatedAtDesc(Long accountId);
    List<Customer> findByAccountIdAndNameContainingIgnoreCaseOrderByCreatedAtDesc(Long accountId, String query);
    java.util.Optional<Customer> findByIdAndAccountId(Long id, Long accountId);
    void deleteByIdAndAccountId(Long id, Long accountId);
}
