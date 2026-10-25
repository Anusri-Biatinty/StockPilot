package com.stockpilot.entity;

public enum MovementType {
    /** Manual goods-in that is not linked to a purchase order. */
    RECEIPT,
    /** Manual goods-out (internal use, samples, write-off of sold-elsewhere stock). */
    ISSUE,
    TRANSFER_IN,
    TRANSFER_OUT,
    /** Cycle-count correction; always carries a reason. */
    ADJUSTMENT,
    PURCHASE_RECEIPT,
    SALES_SHIPMENT
}
