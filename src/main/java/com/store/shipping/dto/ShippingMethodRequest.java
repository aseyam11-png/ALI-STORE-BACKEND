package com.store.shipping.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ShippingMethodRequest {
    @NotBlank @Size(max = 60) private String code;
    @NotBlank @Size(max = 160) private String name;
    @Size(max = 500) private String description;
    @NotNull @DecimalMin("0.00") private BigDecimal price = BigDecimal.ZERO;
    @NotBlank @Size(min = 3, max = 3) private String currency = "USD";
    @NotNull @Min(0) private Integer estimatedMinDays = 0;
    @NotNull @Min(0) private Integer estimatedMaxDays = 0;
    @Size(min = 2, max = 2) private String countryCode;
    private Boolean active = true;
    private Integer priority = 0;
}