package com.stockpilot.entity;

public enum ShipmentStatus {
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED;

    public boolean canTransitionTo(ShipmentStatus target) {
        return target.ordinal() > this.ordinal();
    }
}
