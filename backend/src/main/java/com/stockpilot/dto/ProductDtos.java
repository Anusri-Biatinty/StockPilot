package com.stockpilot.dto;

import com.stockpilot.entity.ProductStatus;
import com.stockpilot.validation.HttpUrl;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ProductDtos {

    private ProductDtos() {
    }

    /**
     * Create/update payload. Only these fields can be set by clients (no status, id or stock),
     * which prevents mass assignment. Leave sku blank on create to have one generated.
     */
    public record Request(
            @Pattern(regexp = "^$|^[A-Za-z0-9][A-Za-z0-9-]{2,39}$",
                    message = "SKU must be 3-40 letters, digits or dashes") String sku,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 4000) String description,
            @NotNull Long categoryId,
            @NotNull @DecimalMin(value = "0.00", message = "Unit price cannot be negative")
            @Digits(integer = 12, fraction = 2) BigDecimal unitPrice,
            @NotNull @DecimalMin(value = "0.00", message = "Cost price cannot be negative")
            @Digits(integer = 12, fraction = 2) BigDecimal costPrice,
            @NotNull @Min(value = 0, message = "Reorder threshold cannot be negative") @Max(1_000_000) Integer reorderThreshold,
            @HttpUrl String imageUrl,
            /* Required on update for optimistic locking; ignored on create. */
            Long version) {
    }

    public record Response(Long id, String sku, String name, String description, Long categoryId, String categoryName,
                           BigDecimal unitPrice, BigDecimal costPrice, int reorderThreshold, String imageUrl,
                           ProductStatus status, long quantityOnHand, long quantityReserved, long quantityAvailable,
                           StockStatus stockStatus, long version, Instant createdAt, Instant updatedAt) {
    }

    public record Detail(Response product, List<WarehouseStock> stock, List<SupplierOffer> suppliers,
                         BigDecimal stockValue) {
    }

    public record WarehouseStock(Long warehouseId, String warehouseCode, String warehouseName,
                                 int onHand, int reserved, int available) {
    }

    public record SupplierOffer(Long supplierId, String supplierName, String supplierSku, BigDecimal unitCost,
                                int leadTimeDays) {
    }
}
