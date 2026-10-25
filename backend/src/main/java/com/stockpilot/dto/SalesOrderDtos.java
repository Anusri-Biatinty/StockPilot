package com.stockpilot.dto;

import com.stockpilot.entity.SalesOrderStatus;
import com.stockpilot.entity.ShipmentStatus;
import jakarta.validation.Valid;
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

public final class SalesOrderDtos {

    private SalesOrderDtos() {
    }

    /** No prices or totals are accepted from the client; they are always computed on the server. */
    public record Request(
            @NotNull Long customerId,
            @NotNull Long warehouseId,
            @Size(max = 500) String shippingAddress,
            @Size(max = 1000) String notes,
            @NotEmpty(message = "At least one line item is required") @Size(max = 200) List<@Valid Line> items,
            boolean confirm) {
    }

    public record Line(@NotNull Long productId, @NotNull @Min(1) @Max(100_000) Integer quantity) {
    }

    public record Summary(Long id, String orderNumber, Long customerId, String customerName, String warehouseCode,
                          SalesOrderStatus status, LocalDate orderDate, BigDecimal totalAmount, int itemCount,
                          Instant createdAt) {
    }

    public record Detail(Long id, String orderNumber, Long customerId, String customerName, Long warehouseId,
                         String warehouseCode, String warehouseName, SalesOrderStatus status, LocalDate orderDate,
                         String shippingAddress, String notes, BigDecimal totalAmount, String createdBy,
                         Instant createdAt, Instant confirmedAt, Instant shippedAt, Instant deliveredAt,
                         Instant cancelledAt, List<Item> items, ShipmentResponse shipment) {
    }

    /** availableInWarehouse is the live available quantity, useful when deciding whether to confirm. */
    public record Item(Long id, Long productId, String sku, String productName, int quantity, BigDecimal unitPrice,
                       BigDecimal lineTotal, int availableInWarehouse) {
    }

    public record ShipRequest(@NotBlank @Size(max = 80) String carrier, @Size(max = 80) String trackingNumber) {
    }

    public record ShipmentStatusRequest(@NotNull ShipmentStatus status) {
    }

    public record ShipmentResponse(Long id, String shipmentNumber, Long salesOrderId, String orderNumber,
                                   String customerName, String carrier, String trackingNumber, ShipmentStatus status,
                                   Instant shippedAt, Instant deliveredAt) {
    }
}
