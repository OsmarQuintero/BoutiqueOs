package com.osmar.boutiqueos.productcategory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {
    List<ProductCategory> findAllByAccountIdOrderByNameAsc(Long accountId);
    Optional<ProductCategory> findByAccountIdAndNameIgnoreCase(Long accountId, String name);
    Optional<ProductCategory> findByIdAndAccountId(Long id, Long accountId);
}
