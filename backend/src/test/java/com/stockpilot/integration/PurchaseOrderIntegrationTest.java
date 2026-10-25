package com.stockpilot.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PurchaseOrderIntegrationTest extends IntegrationTestBase {

    private long supplier;
    private long warehouse;
    private long tape;
    private long boxes;

    @BeforeEach
    void setUp() throws Exception {
        long category = createCategory("Packaging");
        tape = createProduct(category, "TAPE-1", 5, 2, 10);
        boxes = createProduct(category, "BOX-1", 20, 12.5, 10);
        supplier = createSupplier("PackRight");
        warehouse = createWarehouse("MAIN");
        doPut("/api/v1/suppliers/" + supplier + "/products", inventory,
                Map.of("productId", boxes, "unitCost", 11.75, "leadTimeDays", 3));
    }

    private JsonNode createPo() throws Exception {
        return body(doPost("/api/v1/purchase-orders", inventory, Map.of(
                "supplierId", supplier, "warehouseId", warehouse,
                "items", List.of(Map.of("productId", tape, "quantity", 100, "unitCost", 1.5),
                        Map.of("productId", boxes, "quantity", 40)))));
    }

    private long approvedPo() throws Exception {
        long id = createPo().get("id").asLong();
        doPost("/api/v1/purchase-orders/" + id + "/submit", inventory, null).andExpect(status().isOk());
        doPost("/api/v1/purchase-orders/" + id + "/approve", admin, null).andExpect(status().isOk());
        return id;
    }

    private long itemId(long poId, long productId) throws Exception {
        for (JsonNode item : body(doGet("/api/v1/purchase-orders/" + poId, inventory)).get("items")) {
            if (item.get("productId").asLong() == productId) {
                return item.get("id").asLong();
            }
        }
        throw new AssertionError("item not found");
    }

    @Test
    void createComputesTotalsServerSideUsingSupplierCost() throws Exception {
        JsonNode po = createPo();
        assertThat(po.get("status").asText()).isEqualTo("DRAFT");
        assertThat(po.get("poNumber").asText()).startsWith("PO-");
        // 100 x 1.50 + 40 x 11.75 (supplier catalogue cost, not product cost 12.50)
        assertThat(po.get("totalAmount").decimalValue()).isEqualByComparingTo("620.00");
    }

    @Test
    void creatorCannotApproveOwnOrderButAnotherManagerCan() throws Exception {
        long id = createPo().get("id").asLong();
        doPost("/api/v1/purchase-orders/" + id + "/approve", admin, null)
                .andExpect(status().isConflict()); // still DRAFT: cannot skip SUBMITTED
        doPost("/api/v1/purchase-orders/" + id + "/submit", inventory, null).andExpect(jsonPath("$.status").value("SUBMITTED"));
        doPost("/api/v1/purchase-orders/" + id + "/approve", inventory, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELF_APPROVAL_NOT_ALLOWED"));

        String otherManager = token(createUser("manager2", com.stockpilot.entity.RoleName.INVENTORY_MANAGER));
        doPost("/api/v1/purchase-orders/" + id + "/approve", otherManager, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedBy").value("manager2 user"));
        doPost("/api/v1/purchase-orders/" + id + "/approve", sales, null).andExpect(status().isForbidden());
    }

    @Test
    void submittingNotifiesApproversAndRejectReturnsToDraft() throws Exception {
        long id = createPo().get("id").asLong();
        doPost("/api/v1/purchase-orders/" + id + "/submit", inventory, null);
        doGet("/api/v1/notifications?type=PO_PENDING_APPROVAL", admin).andExpect(jsonPath("$.totalElements").value(1));

        doPost("/api/v1/purchase-orders/" + id + "/reject", admin, Map.of("reason", "Wrong quantities"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.rejectionReason").value("Wrong quantities"));
        doGet("/api/v1/notifications?type=PO_REJECTED", inventory).andExpect(jsonPath("$.totalElements").value(1));

        doPut("/api/v1/purchase-orders/" + id, inventory, Map.of("supplierId", supplier, "warehouseId", warehouse,
                "items", List.of(Map.of("productId", tape, "quantity", 50, "unitCost", 1.5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalAmount").value(75.0));
    }

    @Test
    void partialThenCompleteReceiptUpdatesStockAndStatus() throws Exception {
        long id = approvedPo();
        long tapeLine = itemId(id, tape);
        long boxLine = itemId(id, boxes);

        doPost("/api/v1/purchase-orders/" + id + "/receipts", inventory,
                Map.of("items", List.of(Map.of("purchaseOrderItemId", tapeLine, "quantity", 60))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalUnits").value(60));
        doGet("/api/v1/purchase-orders/" + id, inventory).andExpect(jsonPath("$.status").value("PARTIALLY_RECEIVED"));
        assertThat(onHand(tape, warehouse)).isEqualTo(60);

        doPost("/api/v1/purchase-orders/" + id + "/receipts", inventory, Map.of("items", List.of(
                Map.of("purchaseOrderItemId", tapeLine, "quantity", 40),
                Map.of("purchaseOrderItemId", boxLine, "quantity", 40))))
                .andExpect(status().isCreated());
        doGet("/api/v1/purchase-orders/" + id, inventory)
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.receipts.length()").value(2));
        assertThat(onHand(tape, warehouse)).isEqualTo(100);
        assertThat(onHand(boxes, warehouse)).isEqualTo(40);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements WHERE movement_type = 'PURCHASE_RECEIPT'",
                Integer.class)).isEqualTo(3);
    }

    @Test
    void receivingMoreThanOutstandingIsRejected() throws Exception {
        long id = approvedPo();
        long tapeLine = itemId(id, tape);
        doPost("/api/v1/purchase-orders/" + id + "/receipts", inventory,
                Map.of("items", List.of(Map.of("purchaseOrderItemId", tapeLine, "quantity", 101))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OVER_RECEIPT"));
        assertThat(onHand(tape, warehouse)).isZero();
    }

    @Test
    void replayingAReceiptWithTheSameIdempotencyKeyDoesNotAddStockTwice() throws Exception {
        long id = approvedPo();
        String body = json.writeValueAsString(Map.of("items", List.of(Map.of("purchaseOrderItemId", itemId(id, tape), "quantity", 30))));
        java.util.Set<String> receiptNumbers = new java.util.HashSet<>();
        for (int i = 0; i < 3; i++) {
            String response = mvc.perform(post("/api/v1/purchase-orders/" + id + "/receipts")
                            .header("Authorization", "Bearer " + inventory)
                            .header("Idempotency-Key", "delivery-note-42")
                            .contentType("application/json").content(body))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            receiptNumbers.add(json.readTree(response).get("receiptNumber").asText());
        }
        assertThat(receiptNumbers).hasSize(1);
        assertThat(onHand(tape, warehouse)).isEqualTo(30);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM goods_receipts", Integer.class)).isEqualTo(1);
    }

    @Test
    void cannotReceiveUnapprovedOrCancelAfterReceipt() throws Exception {
        long draft = createPo().get("id").asLong();
        doPost("/api/v1/purchase-orders/" + draft + "/receipts", inventory,
                Map.of("items", List.of(Map.of("purchaseOrderItemId", itemId(draft, tape), "quantity", 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PO_NOT_RECEIVABLE"));
        doPost("/api/v1/purchase-orders/" + draft + "/cancel", inventory, null).andExpect(jsonPath("$.status").value("CANCELLED"));

        long id = approvedPo();
        doPost("/api/v1/purchase-orders/" + id + "/receipts", inventory,
                Map.of("items", List.of(Map.of("purchaseOrderItemId", itemId(id, tape), "quantity", 1))));
        doPost("/api/v1/purchase-orders/" + id + "/cancel", inventory, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PO_HAS_RECEIPTS"));
    }

    @Test
    void approvedOrderCannotBeEdited() throws Exception {
        long id = approvedPo();
        doPut("/api/v1/purchase-orders/" + id, inventory, Map.of("supplierId", supplier, "warehouseId", warehouse,
                "items", List.of(Map.of("productId", tape, "quantity", 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PO_NOT_EDITABLE"));
    }

    @Test
    void duplicateLinesAreRejected() throws Exception {
        doPost("/api/v1/purchase-orders", inventory, Map.of("supplierId", supplier, "warehouseId", warehouse,
                "items", List.of(Map.of("productId", tape, "quantity", 1), Map.of("productId", tape, "quantity", 2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_LINE"));
    }
}
