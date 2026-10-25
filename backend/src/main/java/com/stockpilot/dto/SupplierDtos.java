package com.stockpilot.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class SupplierDtos {

    private SupplierDtos() {
    }

    public record Request(
            @Pattern(regexp = "^$|^[A-Za-z0-9][A-Za-z0-9-]{1,19}$", message = "Code must be 2-20 letters, digits or dashes")
            String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 120) String contactName,
            @Email @Size(max = 160) String email,
            @Size(max = 40) String phone,
            @Size(max = 255) String address,
            Boolean active) {
    }

    public record Response(Long id, String code, String name, String contactName, String email, String phone,
                           String address, boolean active, long productCount, long openOrders, BigDecimal totalSpend) {
    }

    public record ProductRequest(
            @NotNull Long productId,
            @Size(max = 60) String supplierSku,
            @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal unitCost,
            @NotNull @Min(0) @Max(365) Integer leadTimeDays) {
    }

    public record ProductResponse(Long id, Long productId, String sku, String productName, String supplierSku,
                                  BigDecimal unitCost, int leadTimeDays) {
    }
}
