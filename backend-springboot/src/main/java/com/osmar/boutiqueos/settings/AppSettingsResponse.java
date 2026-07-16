package com.osmar.boutiqueos.settings;

import java.time.Instant;

public record AppSettingsResponse(
        String storeName,
        String phone,
        String address,
        String street,
        String neighborhood,
        String city,
        String postalCode,
        String contactEmail,
        String instagramHandle,
        String logoUrl,
        String thankYouMessage,
        String ticketPrefix,
        String ticketFooterNote,
        String ticketPaperSize,
        boolean showLogoOnTicket,
        boolean showAddressOnTicket,
        boolean showPhoneOnTicket,
        boolean showCustomerOnTicket,
        boolean showSavingsOnTicket,
        boolean showChangeOnTicket,
        boolean autoOpenTicket,
        String username,
        Instant updatedAt,
        Instant registrationCompletedAt
) {
    public static AppSettingsResponse from(AppSettings settings) {
        return new AppSettingsResponse(
                settings.getStoreName(),
                settings.getPhone(),
                settings.getAddress(),
                settings.getStreet(),
                settings.getNeighborhood(),
                settings.getCity(),
                settings.getPostalCode(),
                settings.getContactEmail(),
                settings.getInstagramHandle(),
                settings.getLogoUrl(),
                settings.getThankYouMessage(),
                settings.getTicketPrefix(),
                settings.getTicketFooterNote(),
                settings.getTicketPaperSize(),
                settings.isShowLogoOnTicket(),
                settings.isShowAddressOnTicket(),
                settings.isShowPhoneOnTicket(),
                settings.isShowCustomerOnTicket(),
                settings.isShowSavingsOnTicket(),
                settings.isShowChangeOnTicket(),
                settings.isAutoOpenTicket(),
                settings.getUsername(),
                settings.getUpdatedAt(),
                settings.getRegistrationCompletedAt()
        );
    }
}
