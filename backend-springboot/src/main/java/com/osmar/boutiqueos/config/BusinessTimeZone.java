package com.osmar.boutiqueos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * El "dia" del negocio es el de la tienda, no el del servidor.
 *
 * <p>En Render el servidor corre en UTC: sin esto, una venta a las 7 pm en
 * Monterrey (1 am UTC del dia siguiente) caia en el corte y en los reportes del
 * dia siguiente. Se fija la zona por defecto de la JVM para que todo lo que usa
 * {@code ZoneId.systemDefault()} / {@code LocalDate.now()} hable del dia de la
 * tienda. La base de datos sigue guardando en UTC
 * ({@code hibernate.jdbc.time_zone=UTC}), asi que los instantes no cambian.
 */
@Configuration
public class BusinessTimeZone {

    private static final Logger log = LoggerFactory.getLogger(BusinessTimeZone.class);

    public BusinessTimeZone(@Value("${app.business-zone:America/Mexico_City}") String zone) {
        ZoneId id = ZoneId.of(zone);
        TimeZone.setDefault(TimeZone.getTimeZone(id));
        log.info("Zona horaria del negocio: {}", id);
    }
}
