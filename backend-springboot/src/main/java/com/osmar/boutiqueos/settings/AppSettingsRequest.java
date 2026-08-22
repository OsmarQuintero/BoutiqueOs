package com.osmar.boutiqueos.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AppSettingsRequest(
        @NotBlank @Size(max = 200) String storeName,
        @Size(max = 20) String phone,
        @Size(max = 300) String address,
        @Size(max = 200) String contactEmail,
        @Size(max = 100) String instagramHandle,
        @Size(max = 20) String socialNetwork,
        @Size(max = 10_000) String logoUrl,
        @Size(max = 500) String thankYouMessage,
        @Size(max = 20) String ticketPrefix,
        @Size(max = 500) String ticketFooterNote,
        @Size(max = 20) String ticketPaperSize,
        Boolean showLogoOnTicket,
        Boolean showAddressOnTicket,
        Boolean showPhoneOnTicket,
        Boolean showCustomerOnTicket,
        Boolean showSavingsOnTicket,
        Boolean showChangeOnTicket,
        Boolean autoOpenTicket,
        @NotBlank @Size(max = 100) String username,
        @Size(max = 100) String password
) {
}
