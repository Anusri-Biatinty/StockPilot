package com.stockpilot.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Builds a small, fully known data set and checks the report and dashboard arithmetic against it.
 */
class ReportIntegrationTest extends IntegrationTestBase {

    private long tools;
    private long drill;
    private long saw;
    private long main;
    private long customer;

    @BeforeEach
    void setUp() throws Exception {
        tools = createCategory("Tools");
        long office = createCategory("Office");
        drill = createProduct(tools, "DRILL", 100.00, 60.00, 5);
        saw = createProduct(tools, "SAW", 50.00, 20.00, 50);
        long pen = createProduct(office, "PEN", 2.00, 0.50, 0);
        main = createWarehouse("MAIN");
        long spare = createWarehouse("SPARE");
        receiveStock(drill, main, 10);   // 10 x 60 = 600
        receiveStock(saw, main, 30);     // 30 x 20 = 600
        receiveStock(pen, spare, 100);   // 100 x 0.50 = 50
        customer = createCustomer("Builder Co");

        // Confirmed order: 2 drills (200) + 5 saws (250) = 450 revenue.
        doPost("/api/v1/sales-orders", sales, Map.of("customerId", customer, "warehouseId", main, "confirm", true,
                "items", List.of(Map.of("productId", drill, "quantity", 2), Map.of("productId", saw, "quantity", 5))));
        // Draft order must be ignored by revenue figures.
        doPost("/api/v1/sales-orders", sales, Map.of("customerId", customer, "warehouseId", main,
                "items", List.of(Map.of("productId", drill, "quantity", 7))));
    }

    @Test
    void valuationReportSumsCostAndRetailByCategory() throws Exception {
        JsonNode report = body(doGet("/api/v1/reports/valuation", viewer));
        JsonNode toolsRow = rowWhere(report, "category", "Tools");
        assertThat(toolsRow.get("units").asInt()).isEqualTo(40);
        assertThat(toolsRow.get("cost_value").decimalValue()).isEqualByComparingTo("1200.00");
        assertThat(toolsRow.get("retail_value").decimalValue()).isEqualByComparingTo("2500.00");
        assertThat(toolsRow.get("potential_margin").decimalValue()).isEqualByComparingTo("1300.00");

        JsonNode spareOnly = body(doGet("/api/v1/reports/valuation?warehouseId=" + main, viewer));
        assertThat(rowWhere(spareOnly, "category", "Office").get("units").asInt()).isZero();
    }

    @Test
    void lowStockReportUsesTotalOnHandAgainstThreshold() throws Exception {
        doGet("/api/v1/reports/low-stock", viewer)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.rows[0].sku").value("SAW"))
                .andExpect(jsonPath("$.rows[0].suggested_order").value(70));
    }

    @Test
    void productPerformanceCountsOnlyBookedOrders() throws Exception {
        JsonNode report = body(doGet("/api/v1/reports/product-performance", viewer));
        JsonNode drillRow = rowWhere(report, "sku", "DRILL");
        assertThat(drillRow.get("units_sold").asInt()).isEqualTo(2);
        assertThat(drillRow.get("revenue").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(drillRow.get("gross_margin").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(drillRow.get("margin_pct").decimalValue()).isEqualByComparingTo("40.0");
        assertThat(rowWhere(report, "sku", "PEN").get("units_sold").asInt()).isZero();
    }

    @Test
    void dashboardKpisMatchTheData() throws Exception {
        JsonNode kpis = body(doGet("/api/v1/dashboard", viewer)).get("kpis");
        assertThat(kpis.get("totalProducts").asInt()).isEqualTo(3);
        assertThat(kpis.get("inventoryValue").decimalValue()).isEqualByComparingTo("1250.00");
        assertThat(kpis.get("totalUnits").asInt()).isEqualTo(140);
        assertThat(kpis.get("salesRevenue").decimalValue()).isEqualByComparingTo("450.00");
        assertThat(kpis.get("salesOrderCount").asInt()).isEqualTo(1);
        assertThat(kpis.get("pendingSalesOrders").asInt()).isEqualTo(2);
        assertThat(kpis.get("lowStockCount").asInt()).isEqualTo(1);

        JsonNode dashboard = body(doGet("/api/v1/dashboard", viewer));
        assertThat(dashboard.get("topProducts").get(0).get("sku").asText()).isEqualTo("SAW");
        String month = LocalDate.now(ZoneOffset.UTC).toString().substring(0, 7);
        JsonNode trends = dashboard.get("trends");
        JsonNode current = trends.get(trends.size() - 1);
        assertThat(current.get("month").asText()).isEqualTo(month);
        assertThat(current.get("sales").decimalValue()).isEqualByComparingTo("450.00");
    }

    @Test
    void salesReportFiltersByDateRange() throws Exception {
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String lastYear = LocalDate.now(ZoneOffset.UTC).minusYears(1).toString();
        doGet("/api/v1/reports/sales?from=" + today + "&to=" + today, viewer).andExpect(jsonPath("$.totalElements").value(1));
        doGet("/api/v1/reports/sales?from=" + lastYear + "&to=" + lastYear, viewer).andExpect(jsonPath("$.totalElements").value(0));
        doGet("/api/v1/reports/sales?from=" + today + "&to=" + lastYear, viewer).andExpect(status().isBadRequest());
    }

    @Test
    void reportSortingIsWhitelistedAndPaginated() throws Exception {
        doGet("/api/v1/reports/inventory?sort=stock_value&direction=desc&size=1", viewer)
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3));
        doGet("/api/v1/reports/inventory?sort=sku;DROP TABLE products", viewer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT"));
        doGet("/api/v1/reports/unknown", viewer).andExpect(status().isNotFound());
    }

    @Test
    void csvExportContainsAllRowsAndEscapesFormulas() throws Exception {
        doPost("/api/v1/products", inventory, Map.of("sku", "EVIL-1", "name", "=HYPERLINK(\"x\")", "categoryId", tools,
                "unitPrice", 1, "costPrice", 1, "reorderThreshold", 0));
        receiveStock(body(doGet("/api/v1/products?search=EVIL", viewer)).get("content").get(0).get("id").asLong(), main, 1);

        String csv = doGet("/api/v1/reports/inventory/export?sort=sku", viewer)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("stockpilot-inventory-")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        List<String> lines = csv.lines().toList();
        assertThat(lines.get(0)).contains("SKU,Product,Category,Warehouse,On hand");
        assertThat(lines).hasSize(5); // header + 4 balances
        assertThat(lines.get(1)).startsWith("DRILL,Product DRILL,Tools,MAIN,10,2,8,60.00,600.00");
        assertThat(csv).contains("\"'=HYPERLINK(\"\"x\"\")\"");
    }

    private static JsonNode rowWhere(JsonNode report, String key, String value) {
        for (JsonNode row : report.get("rows")) {
            if (value.equals(row.get(key).asText())) {
                return row;
            }
        }
        throw new AssertionError("No row with " + key + "=" + value);
    }
}
