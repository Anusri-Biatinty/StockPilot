package com.stockpilot.exception;

public class InsufficientStockException extends ConflictException {

    public InsufficientStockException(String sku, String warehouseCode, int requested, int available) {
        super("INSUFFICIENT_STOCK", "Insufficient stock for " + sku + " in " + warehouseCode
                + ": requested " + requested + ", available " + available);
    }
}
