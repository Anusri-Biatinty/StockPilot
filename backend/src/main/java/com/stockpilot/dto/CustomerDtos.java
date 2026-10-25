package com.stockpilot.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record Request(
            @Pattern(regexp = "^$|^[A-Za-z0-9][A-Za-z0-9-]{1,19}$", message = "Code must be 2-20 letters, digits or dashes")
            String code,
            @NotBlank @Size(max = 160) String name,
            @Email @Size(max = 160) String email,
            @Size(max = 40) String phone,
            @Size(max = 255) String address,
            @Size(max = 80) String city,
            @Size(max = 80) String country,
            Boolean active) {
    }

    public record Response(Long id, String code, String name, String email, String phone, String address,
                           String city, String country, boolean active, long orderCount, BigDecimal totalRevenue) {
    }
}
