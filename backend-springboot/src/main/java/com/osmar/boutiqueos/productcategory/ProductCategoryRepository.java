package com.osmar.boutiqueos.productcategory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {
    List<ProductCategory> findAllByOrderByNameAsc();
    Optional<ProductCategory> findByNameIgnoreCase(String name);
}
