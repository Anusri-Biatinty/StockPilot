package com.stockpilot.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductIntegrationTest extends IntegrationTestBase {

    private long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        categoryId = createCategory("Electronics");
    }

    private Map<String, Object> request(String sku) {
        Map<String, Object> req = new HashMap<>();
        req.put("sku", sku);
        req.put("name", "Scanner");
        req.put("categoryId", categoryId);
        req.put("unitPrice", 100.00);
        req.put("costPrice", 60.00);
        req.put("reorderThreshold", 5);
        req.put("imageUrl", "https://cdn.example.com/scanner.png");
        return req;
    }

    @Test
    void createsProductAndNormalisesSku() throws Exception {
        doPost("/api/v1/products", inventory, request("abc-100"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("ABC-100"))
                .andExpect(jsonPath("$.categoryName").value("Electronics"))
                .andExpect(jsonPath("$.quantityOnHand").value(0))
                .andExpect(jsonPath("$.stockStatus").value("OUT_OF_STOCK"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'PRODUCT_CREATED'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void generatesSkuWhenBlank() throws Exception {
        doPost("/api/v1/products", inventory, request(""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku", startsWith("ELE-")));
    }

    @Test
    void rejectsDuplicateSkuIgnoringCase() throws Exception {
        doPost("/api/v1/products", inventory, request("DUP-1")).andExpect(status().isCreated());
        doPost("/api/v1/products", inventory, request("dup-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SKU"));
    }

    @Test
    void rejectsInvalidPricesThresholdAndImageUrl() throws Exception {
        Map<String, Object> req = request("BAD-1");
        req.put("unitPrice", -1);
        req.put("costPrice", 10.999);
        req.put("reorderThreshold", -3);
        req.put("imageUrl", "javascript:alert(1)");
        doPost("/api/v1/products", inventory, req)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItems("unitPrice", "costPrice", "reorderThreshold", "imageUrl")));
    }

    @Test
    void rejectsUnknownOrInactiveCategory() throws Exception {
        Map<String, Object> req = request("CAT-1");
        req.put("categoryId", 9999);
        doPost("/api/v1/products", inventory, req).andExpect(status().isNotFound());

        doPut("/api/v1/categories/" + categoryId, inventory, Map.of("name", "Electronics", "active", false));
        doPost("/api/v1/products", inventory, request("CAT-2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_INACTIVE"));
    }

    @Test
    void staleVersionIsRejectedOnUpdate() throws Exception {
        JsonNode created = body(doPost("/api/v1/products", inventory, request("VER-1")));
        long id = created.get("id").asLong();

        Map<String, Object> update = request("VER-1");
        update.put("name", "Scanner v2");
        update.put("version", created.get("version").asLong());
        doPut("/api/v1/products/" + id, inventory, update)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Scanner v2"))
                .andExpect(jsonPath("$.version").value(created.get("version").asLong() + 1));

        // A second client still holding the old version must not overwrite the change.
        update.put("name", "Lost update");
        doPut("/api/v1/products/" + id, inventory, update)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void archiveHidesFromActiveFilterAndBlocksNewOrders() throws Exception {
        long id = body(doPost("/api/v1/products", inventory, request("ARC-1"))).get("id").asLong();
        doPost("/api/v1/products/" + id + "/archive", inventory, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        doGet("/api/v1/products?status=ACTIVE", viewer).andExpect(jsonPath("$.totalElements").value(0));

        long warehouse = createWarehouse("WH-A");
        doPost("/api/v1/inventory/receipts", inventory, Map.of("productId", id, "warehouseId", warehouse, "quantity", 5))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_ARCHIVED"));
    }

    @Test
    void searchPaginatesAndFiltersByStockStatus() throws Exception {
        long warehouse = createWarehouse("WH-S");
        long low = createProduct(categoryId, "LOW-1", 10, 5, 10);
        long ok = createProduct(categoryId, "OK-1", 10, 5, 10);
        createProduct(categoryId, "OUT-1", 10, 5, 10);
        receiveStock(low, warehouse, 3);
        receiveStock(ok, warehouse, 50);

        doGet("/api/v1/products?stockStatus=LOW_STOCK", viewer)
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].sku").value("LOW-1"));
        doGet("/api/v1/products?stockStatus=OUT_OF_STOCK", viewer)
                .andExpect(jsonPath("$.content[0].sku").value("OUT-1"));
        doGet("/api/v1/products?size=2&sort=sku,asc", viewer)
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].sku").value("LOW-1"));
        doGet("/api/v1/products?search=ok-", viewer).andExpect(jsonPath("$.totalElements").value(1));
        doGet("/api/v1/products?sort=doesNotExist", viewer).andExpect(status().isBadRequest());
    }

    @Test
    void categoryWithProductsCannotBeDeleted() throws Exception {
        createProduct(categoryId, "CAT-DEL", 1, 1, 0);
        doCall("/api/v1/categories/" + categoryId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));
    }

    private org.springframework.test.web.servlet.ResultActions doCall(String url) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url), inventory, null);
    }
}
