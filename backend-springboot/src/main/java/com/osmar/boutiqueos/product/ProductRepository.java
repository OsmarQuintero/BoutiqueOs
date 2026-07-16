package com.osmar.boutiqueos.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByAccountIdOrderByCreatedAtDesc(Long accountId);
    List<Product> findByAccountIdAndNameContainingIgnoreCaseOrAccountIdAndCategoryContainingIgnoreCase(Long accountIdForName, String name, Long accountIdForCategory, String category);
    long countByAccountIdAndCategoryIgnoreCase(Long accountId, String category);
    java.util.Optional<Product> findByIdAndAccountId(Long id, Long accountId);
    void deleteByIdAndAccountId(Long id, Long accountId);
}
