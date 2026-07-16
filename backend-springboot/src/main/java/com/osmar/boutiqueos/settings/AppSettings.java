package com.osmar.boutiqueos.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "app_settings")
public class AppSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String storeName = "Boutique OS";

    private String phone = "";

    private String address = "";

    private String street = "";

    private String neighborhood = "";

    private String city = "";

    private String postalCode = "";

    private String contactEmail = "";

    private String instagramHandle = "";

    @Lob
    private String logoUrl = "";

    @Column(length = 500)
    private String thankYouMessage = "Gracias por tu compra";

    @Column(length = 20)
    private String ticketPrefix = "BOS";

    @Column(length = 1_000)
    private String ticketFooterNote = "";

    @Column(nullable = false)
    private String ticketPaperSize = "THERMAL_80";

    @Column(nullable = false)
    private boolean showLogoOnTicket = true;

    @Column(nullable = false)
    private boolean showAddressOnTicket = true;

    @Column(nullable = false)
    private boolean showPhoneOnTicket = true;

    @Column(nullable = false)
    private boolean showCustomerOnTicket = true;

    @Column(nullable = false)
    private boolean showSavingsOnTicket = true;

    @Column(nullable = false)
    private boolean showChangeOnTicket = true;

    @Column(nullable = false)
    private boolean autoOpenTicket = true;

    @Column(nullable = false, unique = true)
    private String username = "admin";

    @Column(nullable = false)
    private String password = "admin";

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    private Instant registrationCompletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getNeighborhood() { return neighborhood; }
    public void setNeighborhood(String neighborhood) { this.neighborhood = neighborhood; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getInstagramHandle() { return instagramHandle; }
    public void setInstagramHandle(String instagramHandle) { this.instagramHandle = instagramHandle; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getThankYouMessage() { return thankYouMessage; }
    public void setThankYouMessage(String thankYouMessage) { this.thankYouMessage = thankYouMessage; }

    public String getTicketPrefix() { return ticketPrefix; }
    public void setTicketPrefix(String ticketPrefix) { this.ticketPrefix = ticketPrefix; }

    public String getTicketFooterNote() { return ticketFooterNote; }
    public void setTicketFooterNote(String ticketFooterNote) { this.ticketFooterNote = ticketFooterNote; }

    public String getTicketPaperSize() { return ticketPaperSize; }
    public void setTicketPaperSize(String ticketPaperSize) { this.ticketPaperSize = ticketPaperSize; }

    public boolean isShowLogoOnTicket() { return showLogoOnTicket; }
    public void setShowLogoOnTicket(boolean showLogoOnTicket) { this.showLogoOnTicket = showLogoOnTicket; }

    public boolean isShowAddressOnTicket() { return showAddressOnTicket; }
    public void setShowAddressOnTicket(boolean showAddressOnTicket) { this.showAddressOnTicket = showAddressOnTicket; }

    public boolean isShowPhoneOnTicket() { return showPhoneOnTicket; }
    public void setShowPhoneOnTicket(boolean showPhoneOnTicket) { this.showPhoneOnTicket = showPhoneOnTicket; }

    public boolean isShowCustomerOnTicket() { return showCustomerOnTicket; }
    public void setShowCustomerOnTicket(boolean showCustomerOnTicket) { this.showCustomerOnTicket = showCustomerOnTicket; }

    public boolean isShowSavingsOnTicket() { return showSavingsOnTicket; }
    public void setShowSavingsOnTicket(boolean showSavingsOnTicket) { this.showSavingsOnTicket = showSavingsOnTicket; }

    public boolean isShowChangeOnTicket() { return showChangeOnTicket; }
    public void setShowChangeOnTicket(boolean showChangeOnTicket) { this.showChangeOnTicket = showChangeOnTicket; }

    public boolean isAutoOpenTicket() { return autoOpenTicket; }
    public void setAutoOpenTicket(boolean autoOpenTicket) { this.autoOpenTicket = autoOpenTicket; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getRegistrationCompletedAt() { return registrationCompletedAt; }
    public void setRegistrationCompletedAt(Instant registrationCompletedAt) { this.registrationCompletedAt = registrationCompletedAt; }
}
