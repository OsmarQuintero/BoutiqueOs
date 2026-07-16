package com.osmar.boutiqueos.settings;

import jakarta.validation.constraints.NotBlank;

public record TicketSettingsRequest(
        @NotBlank String storeName,
        String phone,
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
        Boolean showLogoOnTicket,
        Boolean showAddressOnTicket,
        Boolean showPhoneOnTicket,
        Boolean showCustomerOnTicket,
        Boolean showSavingsOnTicket,
        Boolean showChangeOnTicket,
        Boolean autoOpenTicket
) {
}
