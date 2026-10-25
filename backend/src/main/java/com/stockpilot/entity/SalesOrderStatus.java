package com.stockpilot.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Sales order lifecycle:
 * <pre>
 * DRAFT -> CONFIRMED (stock reserved) -> SHIPPED (stock issued) -> DELIVERED
 * DRAFT / CONFIRMED -> CANCELLED (reservations released)
 * </pre>
 */
public enum SalesOrderStatus {
    DRAFT,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    public boolean canTransitionTo(SalesOrderStatus target) {
        return allowedTargets().contains(target);
    }

    private Set<SalesOrderStatus> allowedTargets() {
        return switch (this) {
            case DRAFT -> EnumSet.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> EnumSet.of(SHIPPED, CANCELLED);
            case SHIPPED -> EnumSet.of(DELIVERED);
            case DELIVERED, CANCELLED -> EnumSet.noneOf(SalesOrderStatus.class);
        };
    }
}
