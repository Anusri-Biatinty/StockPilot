package com.stockpilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record Request(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 500) String description,
            Boolean active) {
    }

    public record Response(Long id, String name, String description, boolean active, long productCount) {
    }
}
