package com.store.shipping.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ShippingAddressRequest {

    private String label;

    private String recipientName;

    private String phone;

    @NotBlank(message = "Street is required")
    private String street;

    private String area;

    private String building;

    private String apartment;

    private String notes;

    @NotBlank(message = "City is required")
    private String city;

    private String state;

    @NotBlank(message = "Country is required")
    private String country;

    private String postalCode;

    private Boolean isDefault;
}
