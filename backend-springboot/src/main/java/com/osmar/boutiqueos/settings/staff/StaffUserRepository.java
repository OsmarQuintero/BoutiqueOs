package com.osmar.boutiqueos.settings.staff;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffUserRepository extends JpaRepository<StaffUser, Long> {
    List<StaffUser> findAllByAccountIdOrderByCreatedAtAsc(Long accountId);
    Optional<StaffUser> findByIdAndAccountId(Long id, Long accountId);
    Optional<StaffUser> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);

    long countByAccountIdAndActiveTrue(Long accountId);
}
