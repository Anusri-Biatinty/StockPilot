package com.stockpilot.unit;

import com.stockpilot.entity.PurchaseOrderStatus;
import com.stockpilot.entity.SalesOrderStatus;
import com.stockpilot.entity.ShipmentStatus;
import org.junit.jupiter.api.Test;

import static com.stockpilot.entity.PurchaseOrderStatus.APPROVED;
import static com.stockpilot.entity.PurchaseOrderStatus.CANCELLED;
import static com.stockpilot.entity.PurchaseOrderStatus.DRAFT;
import static com.stockpilot.entity.PurchaseOrderStatus.PARTIALLY_RECEIVED;
import static com.stockpilot.entity.PurchaseOrderStatus.RECEIVED;
import static com.stockpilot.entity.PurchaseOrderStatus.SUBMITTED;
import static org.assertj.core.api.Assertions.assertThat;

class StatusTransitionTest {

    @Test
    void purchaseOrderWorkflow() {
        assertThat(DRAFT.canTransitionTo(SUBMITTED)).isTrue();
        assertThat(DRAFT.canTransitionTo(APPROVED)).isFalse();
        assertThat(SUBMITTED.canTransitionTo(APPROVED)).isTrue();
        assertThat(SUBMITTED.canTransitionTo(DRAFT)).isTrue(); // rejection
        assertThat(APPROVED.canTransitionTo(PARTIALLY_RECEIVED)).isTrue();
        assertThat(PARTIALLY_RECEIVED.canTransitionTo(RECEIVED)).isTrue();
        assertThat(PARTIALLY_RECEIVED.canTransitionTo(CANCELLED)).isFalse();
        assertThat(RECEIVED.canTransitionTo(CANCELLED)).isFalse();
        assertThat(CANCELLED.canTransitionTo(DRAFT)).isFalse();
        assertThat(APPROVED.isReceivable()).isTrue();
        assertThat(SUBMITTED.isReceivable()).isFalse();
        for (PurchaseOrderStatus s : PurchaseOrderStatus.values()) {
            assertThat(RECEIVED.canTransitionTo(s)).isFalse();
        }
    }

    @Test
    void salesOrderWorkflow() {
        assertThat(SalesOrderStatus.DRAFT.canTransitionTo(SalesOrderStatus.CONFIRMED)).isTrue();
        assertThat(SalesOrderStatus.DRAFT.canTransitionTo(SalesOrderStatus.SHIPPED)).isFalse();
        assertThat(SalesOrderStatus.CONFIRMED.canTransitionTo(SalesOrderStatus.CANCELLED)).isTrue();
        assertThat(SalesOrderStatus.SHIPPED.canTransitionTo(SalesOrderStatus.CANCELLED)).isFalse();
        assertThat(SalesOrderStatus.SHIPPED.canTransitionTo(SalesOrderStatus.DELIVERED)).isTrue();
        assertThat(SalesOrderStatus.CANCELLED.canTransitionTo(SalesOrderStatus.CONFIRMED)).isFalse();
    }

    @Test
    void shipmentsOnlyMoveForward() {
        assertThat(ShipmentStatus.IN_TRANSIT.canTransitionTo(ShipmentStatus.DELIVERED)).isTrue();
        assertThat(ShipmentStatus.OUT_FOR_DELIVERY.canTransitionTo(ShipmentStatus.IN_TRANSIT)).isFalse();
        assertThat(ShipmentStatus.DELIVERED.canTransitionTo(ShipmentStatus.DELIVERED)).isFalse();
    }
}
