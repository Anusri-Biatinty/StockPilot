package com.stockpilot.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SalesOrderIntegrationTest extends IntegrationTestBase {

    private long customer;
    private long warehouse;
    private long chair;
    private long desk;

    @BeforeEach
    void setUp() throws Exception {
        long category = createCategory("Furniture");
        chair = createProduct(category, "CHAIR-1", 150.00, 90, 2);
        desk = createProduct(category, "DESK-1", 320.50, 200, 1);
        warehouse = createWarehouse("MAIN");
        customer = createCustomer("Acme Corp");
        receiveStock(chair, warehouse, 10);
        receiveStock(desk, warehouse, 3);
    }

    private JsonNode createOrder(boolean confirm, int chairs, int desks) throws Exception {
        return body(doPost("/api/v1/sales-orders", sales, Map.of("customerId", customer, "warehouseId", warehouse,
                "confirm", confirm, "items", List.of(Map.of("productId", chair, "quantity", chairs),
                        Map.of("productId", desk, "quantity", desks)))));
    }

    @Test
    void totalsAreComputedOnServerIgnoringClientPrices() throws Exception {
        JsonNode order = body(doPost("/api/v1/sales-orders", sales, Map.of("customerId", customer, "warehouseId", warehouse,
                "totalAmount", 1, "items", List.of(Map.of("productId", chair, "quantity", 2, "unitPrice", 0.01, "lineTotal", 0.02),
                        Map.of("productId", desk, "quantity", 1)))));
        assertThat(order.get("status").asText()).isEqualTo("DRAFT");
        assertThat(order.get("totalAmount").decimalValue()).isEqualByComparingTo("620.50");
        assertThat(order.get("items").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(order.get("shippingAddress").asText()).isEqualTo("1 Main St");
        assertThat(reserved(chair, warehouse)).isZero();
    }

    @Test
    void confirmReservesStockWithoutChangingOnHand() throws Exception {
        long id = createOrder(false, 4, 1).get("id").asLong();
        doPost("/api/v1/sales-orders/" + id + "/confirm", sales, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(onHand(chair, warehouse)).isEqualTo(10);
        assertThat(reserved(chair, warehouse)).isEqualTo(4);
        assertThat(reserved(desk, warehouse)).isEqualTo(1);
    }

    @Test
    void insufficientStockRejectsWholeOrderAndReservesNothing() throws Exception {
        doPost("/api/v1/sales-orders", sales, Map.of("customerId", customer, "warehouseId", warehouse, "confirm", true,
                "items", List.of(Map.of("productId", chair, "quantity", 2), Map.of("productId", desk, "quantity", 4))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message").value("Insufficient stock for DESK-1 in MAIN: requested 4, available 3"));
        assertThat(reserved(chair, warehouse)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales_orders", Integer.class)).isZero();
    }

    @Test
    void reservedStockIsNotAvailableToOtherOrdersOrManualIssues() throws Exception {
        createOrder(true, 8, 1);
        doPost("/api/v1/sales-orders", sales, Map.of("customerId", customer, "warehouseId", warehouse, "confirm", true,
                "items", List.of(Map.of("productId", chair, "quantity", 3))))
                .andExpect(status().isConflict());
        doPost("/api/v1/inventory/issues", inventory,
                Map.of("productId", chair, "warehouseId", warehouse, "quantity", 3, "reason", "Samples"))
                .andExpect(status().isConflict());
    }

    @Test
    void cancellingConfirmedOrderReleasesReservation() throws Exception {
        long id = createOrder(true, 5, 2).get("id").asLong();
        assertThat(reserved(chair, warehouse)).isEqualTo(5);
        doPost("/api/v1/sales-orders/" + id + "/cancel", sales, null)
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(reserved(chair, warehouse)).isZero();
        assertThat(reserved(desk, warehouse)).isZero();
        assertThat(onHand(chair, warehouse)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_reservations WHERE status = 'RELEASED'", Integer.class)).isEqualTo(2);
    }

    @Test
    void shippingIssuesStockAndDeliveryCompletesOrder() throws Exception {
        long id = createOrder(true, 3, 1).get("id").asLong();
        JsonNode shipped = body(doPost("/api/v1/sales-orders/" + id + "/ship", sales,
                Map.of("carrier", "UPS", "trackingNumber", "1Z123")));
        assertThat(shipped.get("status").asText()).isEqualTo("SHIPPED");
        assertThat(shipped.get("shipment").get("status").asText()).isEqualTo("IN_TRANSIT");
        assertThat(onHand(chair, warehouse)).isEqualTo(7);
        assertThat(reserved(chair, warehouse)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements WHERE movement_type = 'SALES_SHIPMENT'",
                Integer.class)).isEqualTo(2);

        long shipment = shipped.get("shipment").get("id").asLong();
        doPut("/api/v1/shipments/" + shipment + "/status", sales, Map.of("status", "OUT_FOR_DELIVERY")).andExpect(status().isOk());
        doPut("/api/v1/shipments/" + shipment + "/status", sales, Map.of("status", "DELIVERED")).andExpect(status().isOk());
        doGet("/api/v1/sales-orders/" + id, viewer).andExpect(jsonPath("$.status").value("DELIVERED"));
        doPut("/api/v1/shipments/" + shipment + "/status", sales, Map.of("status", "IN_TRANSIT"))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidTransitionsAreRejected() throws Exception {
        long draft = createOrder(false, 1, 1).get("id").asLong();
        doPost("/api/v1/sales-orders/" + draft + "/ship", sales, Map.of("carrier", "UPS"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        long confirmed = createOrder(true, 1, 1).get("id").asLong();
        doPost("/api/v1/sales-orders/" + confirmed + "/ship", sales, Map.of("carrier", "UPS"));
        doPost("/api/v1/sales-orders/" + confirmed + "/cancel", sales, null).andExpect(status().isConflict());
        doPut("/api/v1/sales-orders/" + confirmed, sales, Map.of("customerId", customer, "warehouseId", warehouse,
                "items", List.of(Map.of("productId", chair, "quantity", 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SO_NOT_EDITABLE"));
    }
}
