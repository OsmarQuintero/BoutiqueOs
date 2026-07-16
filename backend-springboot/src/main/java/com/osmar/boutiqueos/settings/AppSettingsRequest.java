package com.osmar.boutiqueos.settings;

import jakarta.validation.constraints.NotBlank;

public record AppSettingsRequest(
        @NotBlank String storeName,
        String phone,
        String address,
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
        Boolean autoOpenTicket,
        @NotBlank String username,
        String password
) {
}
