package com.stockpilot.dto;

public enum StockStatus {
    IN_STOCK,
    LOW_STOCK,
    OUT_OF_STOCK;

    /** Low stock means at or below the reorder threshold while still above zero. */
    public static StockStatus of(long onHand, int reorderThreshold) {
        if (onHand <= 0) {
            return OUT_OF_STOCK;
        }
        return onHand <= reorderThreshold ? LOW_STOCK : IN_STOCK;
    }
}
