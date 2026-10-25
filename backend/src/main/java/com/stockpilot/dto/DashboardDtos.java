package com.stockpilot.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record Response(LocalDate from, LocalDate to, Kpis kpis, List<MonthlyTrend> trends,
                           List<TopProduct> topProducts, List<CategoryStock> stockByCategory,
                           List<InventoryDtos.MovementResponse> recentMovements,
                           List<InventoryDtos.StockAlert> alerts) {
    }

    /** Stock figures are a live snapshot; revenue, spend and order counts respect the date range. */
    public record Kpis(long totalProducts, long totalCategories, long totalWarehouses, BigDecimal inventoryValue,
                       long totalUnits, long lowStockCount, long outOfStockCount, long pendingPurchaseOrders,
                       long pendingSalesOrders, BigDecimal salesRevenue, BigDecimal purchaseSpend,
                       long salesOrderCount) {
    }

    public record MonthlyTrend(String month, BigDecimal sales, BigDecimal purchases, long orders) {
    }

    public record TopProduct(Long productId, String sku, String name, long quantitySold, BigDecimal revenue) {
    }

    public record CategoryStock(Long categoryId, String name, long units, BigDecimal value) {
    }
}
