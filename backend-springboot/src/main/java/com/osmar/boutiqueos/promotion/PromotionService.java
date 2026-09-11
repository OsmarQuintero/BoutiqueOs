package com.osmar.boutiqueos.promotion;

import com.osmar.boutiqueos.config.AccountContext;
import com.osmar.boutiqueos.customer.CustomerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final CustomerRepository customerRepository;
    private final AccountContext accountContext;

    public PromotionService(
            PromotionRepository promotionRepository,
            CustomerRepository customerRepository,
            AccountContext accountContext
    ) {
        this.promotionRepository = promotionRepository;
        this.customerRepository = customerRepository;
        this.accountContext = accountContext;
    }

    public List<Promotion> list() {
        return promotionRepository.findAllByAccountIdOrderByCreatedAtDesc(accountContext.requireAccountId());
    }

    @Transactional
    public Promotion create(PromotionRequest request) {
        Long accountId = accountContext.requireAccountId();
        Promotion promotion = new Promotion();
        promotion.setAccountId(accountId);
        apply(promotion, request, accountId);
        return promotionRepository.save(promotion);
    }

    @Transactional
    public Promotion update(Long id, PromotionRequest request) {
        Long accountId = accountContext.requireAccountId();
        Promotion promotion = get(id, accountId);
        apply(promotion, request, accountId);
        promotion.setUpdatedAt(Instant.now());
        return promotionRepository.save(promotion);
    }

    @Transactional
    public void delete(Long id) {
        Long accountId = accountContext.requireAccountId();
        promotionRepository.delete(get(id, accountId));
    }

    /**
     * Cuanto descuenta una promocion en una venta, con las mismas reglas que usa
     * el punto de venta para mostrarla. Si ya no aplica, rechaza la venta con la
     * razon: es preferible a cobrar un precio distinto al que se le dijo a la clienta.
     *
     * @param subtotal suma de las lineas (para la compra minima, igual que el POS)
     * @param base     subtotal menos el descuento manual (sobre esto se calcula)
     */
    public PromotionDiscount discountFor(Long promotionId, Long customerId, BigDecimal subtotal, BigDecimal base) {
        Long accountId = accountContext.requireAccountId();
        Promotion promotion = promotionRepository.findByIdAndAccountId(promotionId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("La promocion seleccionada ya no existe"));

        String code = promotion.getCode();
        if (!promotion.isActive()) {
            throw new IllegalArgumentException("La promocion " + code + " esta desactivada");
        }
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        if (promotion.getStartsAt() != null && today.isBefore(promotion.getStartsAt())) {
            throw new IllegalArgumentException("La promocion " + code + " todavia no empieza");
        }
        if (promotion.getEndsAt() != null && today.isAfter(promotion.getEndsAt())) {
            throw new IllegalArgumentException("La promocion " + code + " ya vencio");
        }
        if (promotion.getCustomerId() != null && !promotion.getCustomerId().equals(customerId)) {
            throw new IllegalArgumentException("La promocion " + code + " es exclusiva de otra clienta");
        }
        if (subtotal.compareTo(promotion.getMinSubtotal()) < 0) {
            throw new IllegalArgumentException("La promocion " + code + " pide una compra minima de $"
                    + promotion.getMinSubtotal().setScale(2, RoundingMode.HALF_UP));
        }

        BigDecimal safeBase = base.max(BigDecimal.ZERO);
        BigDecimal raw = promotion.getType() == PromotionType.PERCENT
                ? safeBase.multiply(promotion.getValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : promotion.getValue();
        BigDecimal amount = raw.max(BigDecimal.ZERO).min(safeBase).setScale(2, RoundingMode.HALF_UP);
        return new PromotionDiscount(promotion, amount);
    }

    private Promotion get(Long id, Long accountId) {
        return promotionRepository.findByIdAndAccountId(id, accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promotion not found: " + id));
    }

    private void apply(Promotion promotion, PromotionRequest request, Long accountId) {
        String code = normalizeCode(request.code());
        if (code.isEmpty()) {
            throw new IllegalArgumentException("El codigo de la promocion no puede quedar vacio");
        }
        promotionRepository.findByAccountIdAndCodeIgnoreCase(accountId, code).ifPresent(existing -> {
            if (promotion.getId() == null || !existing.getId().equals(promotion.getId())) {
                throw new IllegalArgumentException("Ya existe una promocion con el codigo " + code);
            }
        });
        if (request.type() == PromotionType.PERCENT && request.value().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Un descuento en porcentaje no puede pasar de 100");
        }
        LocalDate startsAt = request.startsAt() == null ? LocalDate.now(ZoneId.systemDefault()) : request.startsAt();
        if (request.endsAt() != null && request.endsAt().isBefore(startsAt)) {
            throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la de inicio");
        }
        if (request.customerId() != null
                && customerRepository.findByIdAndAccountId(request.customerId(), accountId).isEmpty()) {
            throw new IllegalArgumentException("La clienta de la promocion no existe");
        }
        promotion.setName(request.name().trim());
        promotion.setCode(code);
        promotion.setType(request.type());
        promotion.setValue(request.value().setScale(2, RoundingMode.HALF_UP));
        promotion.setMinSubtotal(request.minSubtotal() == null
                ? BigDecimal.ZERO
                : request.minSubtotal().setScale(2, RoundingMode.HALF_UP));
        promotion.setCustomerId(request.customerId());
        promotion.setStartsAt(startsAt);
        promotion.setEndsAt(request.endsAt());
        promotion.setActive(request.active() == null || request.active());
        promotion.setNotes(request.notes() == null ? null : request.notes().trim());
    }

    /** Mismo criterio que el POS: mayusculas y sin espacios. */
    static String normalizeCode(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    public record PromotionDiscount(Promotion promotion, BigDecimal amount) {
    }
}
