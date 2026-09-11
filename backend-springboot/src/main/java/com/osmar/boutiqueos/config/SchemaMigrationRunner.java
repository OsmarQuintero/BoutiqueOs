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
        // Pago mixto. H2 guarda el enum como tipo ENUM; Postgres como texto con
        // un CHECK que Hibernate no actualiza solo: sin esto la venta mixta falla.
        migrateEnum("sales", "payment_method", "ENUM('CASH', 'TRANSFER', 'CARD', 'MIXED')");
        dropConstraintIfPresent("sales", "sales_payment_method_check");
        // Apartados: nuevo tipo de movimiento de inventario.
        migrateEnum("inventory_movements", "type", "ENUM('PURCHASE', 'SALE', 'ADJUSTMENT', 'RETURN', 'LAYAWAY')");
        dropConstraintIfPresent("inventory_movements", "inventory_movements_type_check");
        makeIdentityIfPossible("app_settings", "id");
        addColumnIfMissing("sales", "refunded_total", "DECIMAL(12,2) DEFAULT 0 NOT NULL");
        addColumnIfMissing("sales", "refunded_profit", "DECIMAL(12,2) DEFAULT 0 NOT NULL");
        addColumnIfMissing("sales", "cash_received", "DECIMAL(12,2) DEFAULT 0 NOT NULL");
        addColumnIfMissing("sales", "change_due", "DECIMAL(12,2) DEFAULT 0 NOT NULL");
        addColumnIfMissing("sales", "refunded_at", "TIMESTAMP");
        addColumnIfMissing("sale_items", "refunded_quantity", "INT DEFAULT 0 NOT NULL");
        addColumnIfMissing("app_settings", "registration_completed_at", "TIMESTAMP");
        addColumnIfMissing("app_settings", "contact_email", "VARCHAR(255) DEFAULT '' NOT NULL");
        addColumnIfMissing("app_settings", "instagram_handle", "VARCHAR(255) DEFAULT '' NOT NULL");
        addColumnIfMissing("app_settings", "ticket_prefix", "VARCHAR(20) DEFAULT 'BOS' NOT NULL");
        addColumnIfMissing("app_settings", "ticket_footer_note", "VARCHAR(1000) DEFAULT '' NOT NULL");
        addColumnIfMissing("app_settings", "ticket_paper_size", "VARCHAR(32) DEFAULT 'THERMAL_80' NOT NULL");
        addColumnIfMissing("app_settings", "show_logo_on_ticket", "BOOLEAN DEFAULT TRUE NOT NULL");
        addColumnIfMissing("app_settings", "show_address_on_ticket", "BOOLEAN DEFAULT TRUE NOT NULL");
        addColumnIfMissing("app_settings", "show_phone_on_ticket", "BOOLEAN DEFAULT TRUE NOT NULL");
        addColumnIfMissing("app_settings", "show_customer_on_ticket", "BOOLEAN DEFAULT TRUE NOT NULL");
        addColumnIfMissing("app_settings", "show_savings_on_ticket", "BOOLEAN DEFAULT TRUE NOT NULL");
        addColumnIfMissing("app_settings", "show_change_on_ticket", "BOOLEAN DEFAULT TRUE NOT NULL");
        addColumnIfMissing("app_settings", "auto_open_ticket", "BOOLEAN DEFAULT TRUE NOT NULL");
        addColumnIfMissing("app_settings", "show_iva_on_ticket", "BOOLEAN DEFAULT TRUE NOT NULL");
        addColumnIfMissing("products", "account_id", "BIGINT DEFAULT 1 NOT NULL");
        addColumnIfMissing("product_categories", "account_id", "BIGINT DEFAULT 1 NOT NULL");
        addColumnIfMissing("customers", "account_id", "BIGINT DEFAULT 1 NOT NULL");
        addColumnIfMissing("inventory_movements", "account_id", "BIGINT DEFAULT 1 NOT NULL");
        addColumnIfMissing("purchases", "account_id", "BIGINT DEFAULT 1 NOT NULL");
        addColumnIfMissing("sales", "account_id", "BIGINT DEFAULT 1 NOT NULL");
        addColumnIfMissing("sale_refunds", "account_id", "BIGINT DEFAULT 1 NOT NULL");
        addColumnIfMissing("daily_cash_counts", "account_id", "BIGINT DEFAULT 1 NOT NULL");
        addColumnIfMissing("onboarding_sessions", "account_id", "BIGINT");
        alterColumnIfPossible("products", "image_url", "CLOB");
        dropUniqueConstraintIfPresent("product_categories", "NAME");
        dropUniqueConstraintIfPresent("daily_cash_counts", "BUSINESS_DATE");
        dropLegacyLoginAttempts();
    }

    /**
     * La columna se llamaba "key", palabra reservada en H2. Como la tabla solo
     * guarda estado temporal de bloqueo por intentos, se descarta y Hibernate la
     * vuelve a crear con el nombre nuevo; no se pierde nada de negocio.
     */
    private void dropLegacyLoginAttempts() {
        try {
            Integer legacy = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE UPPER(TABLE_NAME) = 'LOGIN_ATTEMPTS'
                      AND UPPER(COLUMN_NAME) = 'KEY'
                    """,
                    Integer.class
            );
            if (legacy != null && legacy > 0) {
                jdbcTemplate.execute("DROP TABLE login_attempts");
            }
        } catch (Exception ignored) {
            // En esquemas nuevos la tabla no existe todavia y no hay nada que limpiar.
        }
    }

    private void migrateEnum(String table, String column, String enumDefinition) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " ALTER COLUMN " + column + " " + enumDefinition);
        } catch (Exception ignored) {
            // The app still boots on fresh schemas or if the enum already matches.
        }
    }

    private void dropConstraintIfPresent(String table, String constraint) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + constraint);
        } catch (Exception ignored) {
            // No existe en H2 ni en esquemas nuevos.
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

    private void makeIdentityIfPossible(String table, String column) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " ALTER COLUMN " + column + " BIGINT GENERATED BY DEFAULT AS IDENTITY");
        } catch (Exception ignored) {
            // Safe if the column is already identity or the database does not support it.
        }
    }

    private void dropUniqueConstraintIfPresent(String table, String column) {
        try {
            var names = jdbcTemplate.queryForList(
                    """
                    SELECT DISTINCT tc.CONSTRAINT_NAME
                    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                    JOIN INFORMATION_SCHEMA.INDEXES idx
                      ON tc.CONSTRAINT_NAME = idx.INDEX_NAME
                    WHERE UPPER(tc.TABLE_NAME) = ?
                      AND tc.CONSTRAINT_TYPE = 'UNIQUE'
                      AND UPPER(idx.COLUMN_NAME) = ?
                    """,
                    String.class,
                    table.toUpperCase(),
                    column.toUpperCase()
            );
            for (String name : names) {
                jdbcTemplate.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + name);
            }
        } catch (Exception ignored) {
            // Best-effort cleanup for old single-tenant constraints.
        }
    }
}
