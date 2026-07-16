package com.osmar.boutiqueos.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppSettingsRepository extends JpaRepository<AppSettings, Long> {

    Optional<AppSettings> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);
}
