package com.stockpilot.dto;

import com.stockpilot.entity.MovementType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    public record BalanceResponse(Long id, Long productId, String sku, String productName, String categoryName,
                                  Long warehouseId, String warehouseCode, String warehouseName,
                                  int onHand, int reserved, int available, int reorderThreshold,
                                  BigDecimal unitCost, BigDecimal stockValue) {
    }

    public record MovementResponse(Long id, Long productId, String sku, String productName, Long warehouseId,
                                   String warehouseCode, MovementType type, int quantity, int balanceAfter,
                                   BigDecimal unitCost, String referenceType, Long referenceId, String reason,
                                   String performedBy, Instant createdAt) {
    }

    public record ReceiptRequest(
            @NotNull Long productId,
            @NotNull Long warehouseId,
            @NotNull @Min(1) @Max(1_000_000) Integer quantity,
            @Size(max = 500) String reason) {
    }

    public record IssueRequest(
            @NotNull Long productId,
            @NotNull Long warehouseId,
            @NotNull @Min(1) @Max(1_000_000) Integer quantity,
            @NotBlank(message = "A reason is required for stock issues") @Size(max = 500) String reason) {
    }

    public record TransferRequest(
            @NotNull Long productId,
            @NotNull Long fromWarehouseId,
            @NotNull Long toWarehouseId,
            @NotNull @Min(1) @Max(1_000_000) Integer quantity,
            @Size(max = 500) String notes) {
    }

    /** Signed change: positive adds stock, negative removes it. Zero is rejected. */
    public record AdjustmentRequest(
            @NotNull Long productId,
            @NotNull Long warehouseId,
            @NotNull @Min(-1_000_000) @Max(1_000_000) Integer quantityChange,
            @NotBlank(message = "A reason is required for stock adjustments") @Size(max = 500) String reason) {
    }

    public record TransferResponse(Long id, String transferNumber, Long productId, String sku, String productName,
                                   String fromWarehouseCode, String toWarehouseCode, int quantity, String notes,
                                   String createdBy, Instant createdAt) {
    }

    public record StockAlert(Long productId, String sku, String name, long onHand, int reorderThreshold,
                             StockStatus status) {
    }
}
