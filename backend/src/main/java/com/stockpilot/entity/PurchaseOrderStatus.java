package com.stockpilot.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Purchase order lifecycle:
 * <pre>
 * DRAFT -> SUBMITTED -> APPROVED -> PARTIALLY_RECEIVED -> RECEIVED
 *   ^          |  (reject)
 *   +----------+
 * DRAFT / SUBMITTED / APPROVED -> CANCELLED   (only while nothing has been received)
 * </pre>
 */
public enum PurchaseOrderStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CANCELLED;

    public boolean canTransitionTo(PurchaseOrderStatus target) {
        return allowedTargets().contains(target);
    }

    public boolean isReceivable() {
        return this == APPROVED || this == PARTIALLY_RECEIVED;
    }

    private Set<PurchaseOrderStatus> allowedTargets() {
        return switch (this) {
            case DRAFT -> EnumSet.of(SUBMITTED, CANCELLED);
            case SUBMITTED -> EnumSet.of(APPROVED, DRAFT, CANCELLED);
            case APPROVED -> EnumSet.of(PARTIALLY_RECEIVED, RECEIVED, CANCELLED);
            case PARTIALLY_RECEIVED -> EnumSet.of(PARTIALLY_RECEIVED, RECEIVED);
            case RECEIVED, CANCELLED -> EnumSet.noneOf(PurchaseOrderStatus.class);
        };
    }
}
