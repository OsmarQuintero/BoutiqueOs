package com.osmar.boutiqueos.settings.staff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Cuenta de caja: una persona que cobra en la tienda con permisos limitados.
 * La crea la duena desde Configuracion (plan Pro).
 */
@Entity
@Table(name = "staff_users")
public class StaffUser {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false, length = 200)
    private String password;

    @Column(nullable = false)
    private boolean active = true;

    /** Tope del descuento manual que puede dar esta cajera, en % del subtotal. */
    @Column(nullable = false)
    private int maxDiscountPercent = 10;

    /** Tokens emitidos antes de esto ya no valen (al desactivarla o cambiarle la contrasena). */
    private Instant sessionsValidAfter;

    private Instant lastLoginAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getMaxDiscountPercent() { return maxDiscountPercent; }
    public void setMaxDiscountPercent(int maxDiscountPercent) { this.maxDiscountPercent = maxDiscountPercent; }
    public Instant getSessionsValidAfter() { return sessionsValidAfter; }
    public void setSessionsValidAfter(Instant sessionsValidAfter) { this.sessionsValidAfter = sessionsValidAfter; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
