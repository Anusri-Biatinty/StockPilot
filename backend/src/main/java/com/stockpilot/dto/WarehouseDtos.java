package com.stockpilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class WarehouseDtos {

    private WarehouseDtos() {
    }

    public record Request(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9-]{1,19}$",
                    message = "Code must be 2-20 letters, digits or dashes") String code,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 255) String address,
            @Size(max = 80) String city,
            @Size(max = 80) String country,
            Boolean active) {
    }

    public record Response(Long id, String code, String name, String address, String city, String country,
                           boolean active, long productCount, long totalUnits, BigDecimal stockValue) {
    }
}
