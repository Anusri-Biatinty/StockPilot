package com.stockpilot.dto;

import com.stockpilot.entity.PurchaseOrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PurchaseOrderDtos {

    private PurchaseOrderDtos() {
    }

    public record Request(
            @NotNull Long supplierId,
            @NotNull Long warehouseId,
            LocalDate expectedDate,
            @Size(max = 1000) String notes,
            @NotEmpty(message = "At least one line item is required") @Size(max = 200) List<@Valid Line> items) {
    }

    /** unitCost is optional: when omitted the supplier's catalogue cost (or the product cost) is used. */
    public record Line(
            @NotNull Long productId,
            @NotNull @Min(1) @Max(1_000_000) Integer quantity,
            @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal unitCost) {
    }

    public record Summary(Long id, String poNumber, Long supplierId, String supplierName, Long warehouseId,
                          String warehouseCode, PurchaseOrderStatus status, LocalDate orderDate, LocalDate expectedDate,
                          BigDecimal totalAmount, int itemCount, String createdBy, Instant createdAt) {
    }

    public record Detail(Long id, String poNumber, Long supplierId, String supplierName, Long warehouseId,
                         String warehouseCode, String warehouseName, PurchaseOrderStatus status, LocalDate orderDate,
                         LocalDate expectedDate, String notes, BigDecimal totalAmount, String rejectionReason,
                         String createdBy, Long createdById, Instant submittedAt, String approvedBy, Instant approvedAt,
                         Instant cancelledAt, Instant createdAt, List<Item> items, List<GoodsReceiptDtos.Response> receipts) {
    }

    public record Item(Long id, Long productId, String sku, String productName, int quantityOrdered,
                       int quantityReceived, int quantityRemaining, BigDecimal unitCost, BigDecimal lineTotal) {
    }

    public record RejectRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record ReceiveRequest(
            @NotEmpty(message = "At least one line must be received") List<@Valid ReceiveLine> items,
            @Size(max = 1000) String notes) {
    }

    public record ReceiveLine(@NotNull Long purchaseOrderItemId, @NotNull @Min(1) @Max(1_000_000) Integer quantity) {
    }
}
