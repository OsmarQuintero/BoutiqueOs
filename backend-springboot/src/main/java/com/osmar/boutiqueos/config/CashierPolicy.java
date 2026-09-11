package com.osmar.boutiqueos.config;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Lo que una cuenta de caja NO puede hacer. Todo lo demas si.
 *
 * <p>La caja cobra, consulta, registra clientas, canjea puntos, guarda el conteo
 * de caja y registra entradas y salidas de efectivo. No toca precios, catalogo,
 * promociones, compras, inventario, devoluciones, cancelaciones, cierre del dia,
 * respaldos, suscripcion ni configuracion.
 *
 * <p>Se aplica en el servidor (ApiSessionInterceptor), no solo escondiendo
 * botones: un boton escondido se salta con una llamada directa a la API.
 */
@Component
public class CashierPolicy {

    private record Rule(Pattern method, Pattern path) {
    }

    private static Rule rule(String methods, String pathRegex) {
        return new Rule(Pattern.compile(methods.equals("*") ? ".*" : methods), Pattern.compile(pathRegex));
    }

    private static final List<Rule> FORBIDDEN = List.of(
            rule("PUT", "/api/settings(/.*)?"),
            rule("POST", "/api/settings/credentials/code"),
            rule("*", "/api/backup(/.*)?"),
            rule("*", "/api/staff(/.*)?"),
            rule("POST", "/api/subscription/(checkout|cancel)"),
            rule("POST|PUT|DELETE", "/api/(promotions|products|product-categories|purchases)(/.*)?"),
            rule("POST", "/api/inventory/adjustments"),
            rule("DELETE", "/api/inventory/movements/.*"),
            rule("POST", "/api/reports/cash-count/today/(close|reopen)"),
            rule("DELETE", "/api/reports/cash-movements/.*"),
            rule("POST", "/api/sales/[^/]+/(refund|cancel)"),
            rule("POST|PUT|DELETE", "/api/loyalty/rewards(/.*)?"),
            rule("POST", "/api/loyalty/adjust/.*"),
            rule("DELETE", "/api/customers/.*")
    );

    public boolean isForbiddenForCashier(String method, String path) {
        String m = method == null ? "" : method.toUpperCase();
        String p = path == null ? "" : path;
        return FORBIDDEN.stream().anyMatch(r -> r.method().matcher(m).matches() && r.path().matcher(p).matches());
    }
}
