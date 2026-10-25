package com.stockpilot.service;

import com.stockpilot.dto.DashboardDtos;
import com.stockpilot.dto.InventoryDtos;
import com.stockpilot.dto.StockStatus;
import com.stockpilot.exception.BadRequestException;
import com.stockpilot.mapper.InventoryMapper;
import com.stockpilot.repository.InventoryMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Aggregates for the dashboard, computed with SQL directly against the operational tables.
 *
 * <p>Definitions: sales revenue counts CONFIRMED, SHIPPED and DELIVERED orders by order date; purchase spend
 * counts APPROVED, PARTIALLY_RECEIVED and RECEIVED orders by order date; "pending" purchase orders are
 * SUBMITTED, APPROVED or PARTIALLY_RECEIVED; "pending" sales orders are DRAFT or CONFIRMED.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    static final String BOOKED_SALES = "('CONFIRMED','SHIPPED','DELIVERED')";
    static final String COMMITTED_PURCHASES = "('APPROVED','PARTIALLY_RECEIVED','RECEIVED')";

    private final NamedParameterJdbcTemplate jdbc;
    private final InventoryMovementRepository movementRepository;
    private final InventoryService inventoryService;

    @Transactional(readOnly = true)
    public DashboardDtos.Response get(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.minusMonths(5).withDayOfMonth(1);
        if (start.isAfter(end)) {
            throw new BadRequestException("INVALID_RANGE", "'from' must be on or before 'to'");
        }
        if (start.plusYears(5).isBefore(end)) {
            throw new BadRequestException("INVALID_RANGE", "Date range cannot exceed 5 years");
        }
        var params = new MapSqlParameterSource().addValue("from", start).addValue("to", end);

        List<InventoryDtos.StockAlert> alerts = inventoryService.alerts();
        long low = alerts.stream().filter(a -> a.status() == StockStatus.LOW_STOCK).count();
        long out = alerts.stream().filter(a -> a.status() == StockStatus.OUT_OF_STOCK).count();

        DashboardDtos.Kpis kpis = jdbc.queryForObject("""
                SELECT
                  (SELECT COUNT(*) FROM products WHERE status = 'ACTIVE') AS total_products,
                  (SELECT COUNT(*) FROM categories WHERE active) AS total_categories,
                  (SELECT COUNT(*) FROM warehouses WHERE active) AS total_warehouses,
                  (SELECT COALESCE(SUM(b.quantity_on_hand * p.cost_price), 0)
                     FROM inventory_balances b JOIN products p ON p.id = b.product_id) AS inventory_value,
                  (SELECT COALESCE(SUM(quantity_on_hand), 0) FROM inventory_balances) AS total_units,
                  (SELECT COUNT(*) FROM purchase_orders
                     WHERE status IN ('SUBMITTED','APPROVED','PARTIALLY_RECEIVED')) AS pending_po,
                  (SELECT COUNT(*) FROM sales_orders WHERE status IN ('DRAFT','CONFIRMED')) AS pending_so,
                  (SELECT COALESCE(SUM(total_amount), 0) FROM sales_orders
                     WHERE status IN %s AND order_date BETWEEN :from AND :to) AS sales_revenue,
                  (SELECT COUNT(*) FROM sales_orders
                     WHERE status IN %s AND order_date BETWEEN :from AND :to) AS sales_count,
                  (SELECT COALESCE(SUM(total_amount), 0) FROM purchase_orders
                     WHERE status IN %s AND order_date BETWEEN :from AND :to) AS purchase_spend
                """.formatted(BOOKED_SALES, BOOKED_SALES, COMMITTED_PURCHASES), params, (rs, i) ->
                new DashboardDtos.Kpis(rs.getLong("total_products"), rs.getLong("total_categories"),
                        rs.getLong("total_warehouses"), rs.getBigDecimal("inventory_value"), rs.getLong("total_units"),
                        low, out, rs.getLong("pending_po"), rs.getLong("pending_so"),
                        rs.getBigDecimal("sales_revenue"), rs.getBigDecimal("purchase_spend"), rs.getLong("sales_count")));

        List<DashboardDtos.MonthlyTrend> trends = jdbc.query("""
                WITH months AS (
                  SELECT generate_series(date_trunc('month', CAST(:from AS date)),
                                         date_trunc('month', CAST(:to AS date)), INTERVAL '1 month')::date AS m
                ),
                sales AS (
                  SELECT date_trunc('month', order_date)::date AS m, SUM(total_amount) AS amount, COUNT(*) AS orders
                  FROM sales_orders WHERE status IN %s AND order_date BETWEEN :from AND :to GROUP BY 1
                ),
                purchases AS (
                  SELECT date_trunc('month', order_date)::date AS m, SUM(total_amount) AS amount
                  FROM purchase_orders WHERE status IN %s AND order_date BETWEEN :from AND :to GROUP BY 1
                )
                SELECT to_char(months.m, 'YYYY-MM') AS month, COALESCE(s.amount, 0) AS sales,
                       COALESCE(p.amount, 0) AS purchases, COALESCE(s.orders, 0) AS orders
                FROM months LEFT JOIN sales s ON s.m = months.m LEFT JOIN purchases p ON p.m = months.m
                ORDER BY months.m
                """.formatted(BOOKED_SALES, COMMITTED_PURCHASES), params, (rs, i) ->
                new DashboardDtos.MonthlyTrend(rs.getString("month"), rs.getBigDecimal("sales"),
                        rs.getBigDecimal("purchases"), rs.getLong("orders")));

        List<DashboardDtos.TopProduct> top = jdbc.query("""
                SELECT p.id, p.sku, p.name, SUM(i.quantity) AS qty, SUM(i.line_total) AS revenue
                FROM sales_order_items i
                JOIN sales_orders s ON s.id = i.sales_order_id
                JOIN products p ON p.id = i.product_id
                WHERE s.status IN %s AND s.order_date BETWEEN :from AND :to
                GROUP BY p.id ORDER BY revenue DESC, qty DESC LIMIT 5
                """.formatted(BOOKED_SALES), params, (rs, i) ->
                new DashboardDtos.TopProduct(rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
                        rs.getLong("qty"), rs.getBigDecimal("revenue")));

        List<DashboardDtos.CategoryStock> byCategory = jdbc.query("""
                SELECT c.id, c.name, COALESCE(SUM(b.quantity_on_hand), 0) AS units,
                       COALESCE(SUM(b.quantity_on_hand * p.cost_price), 0) AS value
                FROM categories c
                LEFT JOIN products p ON p.category_id = c.id
                LEFT JOIN inventory_balances b ON b.product_id = p.id
                GROUP BY c.id, c.name ORDER BY value DESC
                """, params, (rs, i) -> new DashboardDtos.CategoryStock(rs.getLong("id"), rs.getString("name"),
                rs.getLong("units"), rs.getBigDecimal("value")));

        var recent = movementRepository.findTop10ByOrderByCreatedAtDescIdDesc().stream()
                .map(InventoryMapper::toResponse).toList();

        return new DashboardDtos.Response(start, end, kpis, trends, top, byCategory, recent,
                alerts.stream().limit(8).toList());
    }
}
