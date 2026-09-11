package com.osmar.boutiqueos.layaway;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Apartado: la clienta deja un anticipo y la mercancia se reserva hasta que
 * liquide. Al liquidar se registra la venta; si se cancela, la mercancia regresa.
 */
@Entity
@Table(name = "layaways")
public class Layaway {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false, length = 200)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LayawayStatus status = LayawayStatus.OPEN;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    /** Suma de abonos (sin restar lo devuelto al cancelar). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal paid = BigDecimal.ZERO;

    /** Lo que se le devolvio a la clienta al cancelar. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal refunded = BigDecimal.ZERO;

    private LocalDate dueDate;

    @Column(length = 500)
    private String notes;

    @Column(length = 120)
    private String createdByName;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant completedAt;

    private Instant cancelledAt;

    @Column(length = 300)
    private String cancelReason;

    /** La venta que se registro al liquidar. */
    private Long saleId;

    @OneToMany(mappedBy = "layaway", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<LayawayItem> items = new ArrayList<>();

    // Set y no List: dos listas EAGER en la misma entidad no las puede traer Hibernate.
    @OneToMany(mappedBy = "layaway", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private Set<LayawayPayment> payments = new LinkedHashSet<>();

    public BigDecimal getRemaining() {
        return total.subtract(paid).max(BigDecimal.ZERO);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public LayawayStatus getStatus() { return status; }
    public void setStatus(LayawayStatus status) { this.status = status; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public BigDecimal getPaid() { return paid; }
    public void setPaid(BigDecimal paid) { this.paid = paid; }
    public BigDecimal getRefunded() { return refunded; }
    public void setRefunded(BigDecimal refunded) { this.refunded = refunded; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public Long getSaleId() { return saleId; }
    public void setSaleId(Long saleId) { this.saleId = saleId; }
    public List<LayawayItem> getItems() { return items; }
    public Set<LayawayPayment> getPayments() { return payments; }
}
