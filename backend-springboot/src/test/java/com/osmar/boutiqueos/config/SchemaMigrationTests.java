package com.osmar.boutiqueos.config;

import com.osmar.boutiqueos.settings.AttemptKey;
import com.osmar.boutiqueos.settings.LoginAttempt;
import com.osmar.boutiqueos.settings.LoginAttemptRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Simula la base de produccion al subir esta version: el runner corre DESPUES de
 * que Hibernate actualizo el esquema, asi que lo que arregle tiene que quedar
 * listo en ese mismo arranque.
 */
@SpringBootTest
class SchemaMigrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SchemaMigrationRunner runner;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @Test
    void legacyLoginAttemptsTableIsRecreatedInTheSameBoot() {
        jdbcTemplate.execute("DROP TABLE login_attempts");
        jdbcTemplate.execute("""
                CREATE TABLE login_attempts (
                    "key" VARCHAR(512) NOT NULL PRIMARY KEY,
                    count INTEGER NOT NULL,
                    window_expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                    blocked_until TIMESTAMP(6) WITH TIME ZONE
                )
                """);

        runner.run();

        LoginAttempt attempt = new LoginAttempt();
        attempt.setId(new AttemptKey("login:prueba"));
        attempt.setCount(2);
        attempt.setWindowExpiresAt(Instant.now().plusSeconds(60));
        loginAttemptRepository.saveAndFlush(attempt);

        assertEquals(2, loginAttemptRepository.findById(new AttemptKey("login:prueba")).orElseThrow().getCount());
        loginAttemptRepository.deleteAll();
    }

    @Test
    void cashCountColumnsMissingInProductionAreAddedWithZero() {
        try {
            jdbcTemplate.execute("ALTER TABLE daily_cash_counts DROP COLUMN opening_float");
            jdbcTemplate.execute("ALTER TABLE daily_cash_counts DROP COLUMN expected_cash");
            jdbcTemplate.execute("ALTER TABLE daily_cash_counts DROP COLUMN difference");
            // Una fila vieja: es la que impedia a Hibernate agregar las columnas NOT NULL.
            jdbcTemplate.update("""
                    INSERT INTO daily_cash_counts (id, account_id, business_date, actual_cash, closed, updated_at)
                    VALUES (987654, 987, DATE '2020-01-01', 150, FALSE, CURRENT_TIMESTAMP)
                    """);

            runner.run();

            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT opening_float, expected_cash, difference FROM daily_cash_counts WHERE account_id = 987");
            assertTrue(row.values().stream().allMatch(v -> ((BigDecimal) v).signum() == 0), row.toString());
        } finally {
            // La base es compartida con las demas pruebas: siempre se deja como estaba.
            runner.run();
            jdbcTemplate.update("DELETE FROM daily_cash_counts WHERE account_id = 987");
        }
    }
}
