package com.stockpilot.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryIntegrationTest extends IntegrationTestBase {

    private long product;
    private long east;
    private long west;

    @BeforeEach
    void setUp() throws Exception {
        long category = createCategory("Packaging");
        product = createProduct(category, "BOX-1", 20, 10, 15);
        east = createWarehouse("EAST");
        west = createWarehouse("WEST");
    }

    @Test
    void receiptIncreasesStockAndWritesMovement() throws Exception {
        doPost("/api/v1/inventory/receipts", inventory, Map.of("productId", product, "warehouseId", east, "quantity", 40))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("RECEIPT"))
                .andExpect(jsonPath("$.quantity").value(40))
                .andExpect(jsonPath("$.balanceAfter").value(40))
                .andExpect(jsonPath("$.performedBy").value("inventory user"));
        assertThat(onHand(product, east)).isEqualTo(40);
        assertThat(movementCount(product)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'STOCK_RECEIVED'", Integer.class)).isEqualTo(1);
    }

    @Test
    void transferMovesStockBetweenWarehousesWithTwoMovements() throws Exception {
        receiveStock(product, east, 30);
        doPost("/api/v1/inventory/transfers", inventory,
                Map.of("productId", product, "fromWarehouseId", east, "toWarehouseId", west, "quantity", 12))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromWarehouseCode").value("EAST"))
                .andExpect(jsonPath("$.toWarehouseCode").value("WEST"));
        assertThat(onHand(product, east)).isEqualTo(18);
        assertThat(onHand(product, west)).isEqualTo(12);
        assertThat(jdbc.queryForList("SELECT movement_type FROM inventory_movements WHERE reference_type = 'STOCK_TRANSFER' ORDER BY id",
                String.class)).containsExactly("TRANSFER_OUT", "TRANSFER_IN");
    }

    @Test
    void cannotIssueTransferOrAdjustBelowZero() throws Exception {
        receiveStock(product, east, 5);
        doPost("/api/v1/inventory/issues", inventory,
                Map.of("productId", product, "warehouseId", east, "quantity", 6, "reason", "Samples"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        doPost("/api/v1/inventory/transfers", inventory,
                Map.of("productId", product, "fromWarehouseId", east, "toWarehouseId", west, "quantity", 6))
                .andExpect(status().isConflict());
        doPost("/api/v1/inventory/adjustments", inventory,
                Map.of("productId", product, "warehouseId", east, "quantityChange", -6, "reason", "Count"))
                .andExpect(status().isConflict());

        // Nothing changed and no stray movements were written by the failed attempts.
        assertThat(onHand(product, east)).isEqualTo(5);
        assertThat(onHand(product, west)).isZero();
        assertThat(movementCount(product)).isEqualTo(1);
    }

    @Test
    void adjustmentRequiresReasonAndNonZeroQuantity() throws Exception {
        receiveStock(product, east, 10);
        doPost("/api/v1/inventory/adjustments", inventory,
                Map.of("productId", product, "warehouseId", east, "quantityChange", -2, "reason", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
        doPost("/api/v1/inventory/adjustments", inventory,
                Map.of("productId", product, "warehouseId", east, "quantityChange", 0, "reason", "Count"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ZERO_ADJUSTMENT"));
        doPost("/api/v1/inventory/adjustments", inventory,
                Map.of("productId", product, "warehouseId", east, "quantityChange", -2, "reason", "Damaged in cycle count"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reason").value("Damaged in cycle count"));
        assertThat(onHand(product, east)).isEqualTo(8);
    }

    @Test
    void transferToSameWarehouseIsRejected() throws Exception {
        receiveStock(product, east, 10);
        doPost("/api/v1/inventory/transfers", inventory,
                Map.of("productId", product, "fromWarehouseId", east, "toWarehouseId", east, "quantity", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAME_WAREHOUSE"));
    }

    @Test
    void crossingReorderThresholdNotifiesInventoryManagersOnce() throws Exception {
        receiveStock(product, east, 20);
        doPost("/api/v1/inventory/issues", inventory,
                Map.of("productId", product, "warehouseId", east, "quantity", 6, "reason", "Internal use"));
        doPost("/api/v1/inventory/issues", inventory,
                Map.of("productId", product, "warehouseId", east, "quantity", 1, "reason", "Internal use"));

        doGet("/api/v1/notifications", inventory)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("LOW_STOCK"));
        doGet("/api/v1/notifications", sales).andExpect(jsonPath("$.totalElements").value(0));

        doPost("/api/v1/inventory/issues", inventory,
                Map.of("productId", product, "warehouseId", east, "quantity", 13, "reason", "Write-off"));
        doGet("/api/v1/notifications?type=OUT_OF_STOCK", admin).andExpect(jsonPath("$.totalElements").value(1));
        doGet("/api/v1/inventory/alerts", viewer).andExpect(jsonPath("$[0].status").value("OUT_OF_STOCK"));
    }

    @Test
    void warehouseWithStockCannotBeDeactivated() throws Exception {
        receiveStock(product, east, 1);
        doPut("/api/v1/warehouses/" + east, inventory, Map.of("code", "EAST", "name", "East", "active", false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WAREHOUSE_NOT_EMPTY"));
    }

    @Test
    void movementHistoryIsFilterable() throws Exception {
        receiveStock(product, east, 10);
        receiveStock(product, west, 4);
        doGet("/api/v1/inventory/movements?warehouseId=" + west, viewer)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].warehouseCode").value("WEST"));
        doGet("/api/v1/products/" + product + "/movements", viewer).andExpect(jsonPath("$.totalElements").value(2));
    }
}
