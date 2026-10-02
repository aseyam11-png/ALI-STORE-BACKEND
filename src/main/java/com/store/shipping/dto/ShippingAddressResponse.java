package com.store.shipping.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ShippingAddressResponse {
    private Long id;
    private String street;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private String label;
    private String recipientName;
    private String phone;
    private String area;
    private String building;
    private String apartment;
    private String notes;
    private Boolean isDefault;
}
