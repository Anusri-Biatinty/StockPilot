package com.stockpilot.dto;

import java.time.Instant;
import java.util.List;

public final class GoodsReceiptDtos {

    private GoodsReceiptDtos() {
    }

    public record Response(Long id, String receiptNumber, Long purchaseOrderId, String poNumber, String supplierName,
                           String warehouseCode, String receivedBy, Instant receivedAt, String notes,
                           List<Item> items, int totalUnits) {
    }

    public record Item(Long productId, String sku, String productName, int quantityReceived) {
    }
}
