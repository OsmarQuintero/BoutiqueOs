package com.osmar.boutiqueos.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SchemaMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public SchemaMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        migrateEnum("sales", "status", "ENUM('PENDING', 'CONFIRMED', 'PARTIALLY_REFUNDED', 'CANCELLED', 'REFUNDED')");
        migrateEnum("products", "status", "ENUM('ACTIVE', 'OUT_OF_STOCK', 'ARCHIVED')");
        addColumnIfMissing("sales", "refunded_total", "DECIMAL(12,2) DEFAULT 0 NOT NULL");
        addColumnIfMissing("sales", "refunded_profit", "DECIMAL(12,2) DEFAULT 0 NOT NULL");
        addColumnIfMissing("sales", "refunded_at", "TIMESTAMP");
        addColumnIfMissing("sale_items", "refunded_quantity", "INT DEFAULT 0 NOT NULL");
        alterColumnIfPossible("products", "image_url", "CLOB");
    }

    private void migrateEnum(String table, String column, String enumDefinition) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " ALTER COLUMN " + column + " " + enumDefinition);
        } catch (Exception ignored) {
            // The app still boots on fresh schemas or if the enum already matches.
        }
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS " + column + " " + definition);
        } catch (Exception ignored) {
            // Best-effort migration for existing local databases.
        }
    }

    private void alterColumnIfPossible(String table, String column, String definition) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " ALTER COLUMN " + column + " " + definition);
        } catch (Exception ignored) {
            // Safe on fresh schemas or databases that already match.
        }
    }
}
