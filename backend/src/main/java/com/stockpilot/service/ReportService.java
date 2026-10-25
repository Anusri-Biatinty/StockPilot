package com.stockpilot.service;

import com.stockpilot.dto.ReportDtos;
import com.stockpilot.exception.BadRequestException;
import com.stockpilot.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Report catalogue. Each report is a read-only SQL query; this class adds filtering, whitelisted sorting,
 * pagination and CSV export on top. Values always come straight from the database.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final int MAX_EXPORT_ROWS = 100_000;

    private record Definition(String key, String title, String description, boolean dateRange,
                              boolean warehouseFilter, List<ReportDtos.Column> columns, String sql, String defaultSort,
                              String defaultDirection) {
    }

    private static ReportDtos.Column col(String key, String label, String type) {
        return new ReportDtos.Column(key, label, type);
    }

    private static final String WAREHOUSE_FILTER = "(CAST(:warehouseId AS BIGINT) IS NULL OR %s = :warehouseId)";

    private static final List<Definition> DEFINITIONS = List.of(
            new Definition("inventory", "Current inventory", "Stock on hand, reserved and available per product and warehouse",
                    false, true,
                    List.of(col("sku", "SKU", "text"), col("product", "Product", "text"), col("category", "Category", "text"),
                            col("warehouse", "Warehouse", "text"), col("on_hand", "On hand", "number"),
                            col("reserved", "Reserved", "number"), col("available", "Available", "number"),
                            col("unit_cost", "Unit cost", "money"), col("stock_value", "Stock value", "money")),
                    """
                    SELECT p.sku, p.name AS product, c.name AS category, w.code AS warehouse,
                           b.quantity_on_hand AS on_hand, b.quantity_reserved AS reserved,
                           b.quantity_on_hand - b.quantity_reserved AS available, p.cost_price AS unit_cost,
                           b.quantity_on_hand * p.cost_price AS stock_value
                    FROM inventory_balances b
                    JOIN products p ON p.id = b.product_id
                    JOIN categories c ON c.id = p.category_id
                    JOIN warehouses w ON w.id = b.warehouse_id
                    WHERE %s
                    """.formatted(WAREHOUSE_FILTER.formatted("b.warehouse_id")), "sku", "asc"),

            new Definition("low-stock", "Low-stock inventory", "Active products at or below their reorder threshold, with a suggested reorder quantity",
                    false, false,
                    List.of(col("sku", "SKU", "text"), col("product", "Product", "text"), col("category", "Category", "text"),
                            col("on_hand", "On hand", "number"), col("reserved", "Reserved", "number"),
                            col("reorder_threshold", "Reorder threshold", "number"),
                            col("suggested_order", "Suggested order", "number"), col("status", "Status", "text"),
                            col("preferred_supplier", "Preferred supplier", "text")),
                    """
                    SELECT p.sku, p.name AS product, c.name AS category,
                           COALESCE(SUM(b.quantity_on_hand), 0) AS on_hand,
                           COALESCE(SUM(b.quantity_reserved), 0) AS reserved,
                           p.reorder_threshold,
                           GREATEST(p.reorder_threshold * 2 - COALESCE(SUM(b.quantity_on_hand), 0), 0) AS suggested_order,
                           CASE WHEN COALESCE(SUM(b.quantity_on_hand), 0) = 0 THEN 'OUT_OF_STOCK' ELSE 'LOW_STOCK' END AS status,
                           (SELECT s.name FROM supplier_products sp JOIN suppliers s ON s.id = sp.supplier_id
                             WHERE sp.product_id = p.id AND s.active ORDER BY sp.unit_cost, sp.lead_time_days LIMIT 1) AS preferred_supplier
                    FROM products p
                    JOIN categories c ON c.id = p.category_id
                    LEFT JOIN inventory_balances b ON b.product_id = p.id
                    WHERE p.status = 'ACTIVE'
                    GROUP BY p.id, c.name
                    HAVING COALESCE(SUM(b.quantity_on_hand), 0) <= p.reorder_threshold
                    """, "on_hand", "asc"),

            new Definition("valuation", "Inventory valuation", "Stock value at cost and at selling price, by category",
                    false, true,
                    List.of(col("category", "Category", "text"), col("products", "Products", "number"),
                            col("units", "Units", "number"), col("cost_value", "Cost value", "money"),
                            col("retail_value", "Retail value", "money"), col("potential_margin", "Potential margin", "money")),
                    """
                    SELECT c.name AS category, COUNT(DISTINCT p.id) AS products,
                           COALESCE(SUM(b.quantity_on_hand), 0) AS units,
                           COALESCE(SUM(b.quantity_on_hand * p.cost_price), 0) AS cost_value,
                           COALESCE(SUM(b.quantity_on_hand * p.unit_price), 0) AS retail_value,
                           COALESCE(SUM(b.quantity_on_hand * (p.unit_price - p.cost_price)), 0) AS potential_margin
                    FROM categories c
                    LEFT JOIN products p ON p.category_id = c.id
                    LEFT JOIN inventory_balances b ON b.product_id = p.id AND %s
                    GROUP BY c.id, c.name
                    """.formatted(WAREHOUSE_FILTER.formatted("b.warehouse_id")), "cost_value", "desc"),

            new Definition("movements", "Stock movement history", "Every stock change in the period with its reference and value",
                    true, true,
                    List.of(col("created_at", "Date", "datetime"), col("sku", "SKU", "text"), col("product", "Product", "text"),
                            col("warehouse", "Warehouse", "text"), col("type", "Type", "text"),
                            col("quantity", "Quantity", "number"), col("balance_after", "Balance after", "number"),
                            col("value", "Value", "money"), col("reference", "Reference", "text"),
                            col("reason", "Reason", "text"), col("performed_by", "Performed by", "text")),
                    """
                    SELECT m.created_at, p.sku, p.name AS product, w.code AS warehouse, m.movement_type AS type,
                           m.quantity, m.balance_after, m.quantity * m.unit_cost AS value,
                           CONCAT_WS(' #', m.reference_type, m.reference_id::text) AS reference, m.reason,
                           u.full_name AS performed_by
                    FROM inventory_movements m
                    JOIN products p ON p.id = m.product_id
                    JOIN warehouses w ON w.id = m.warehouse_id
                    LEFT JOIN users u ON u.id = m.performed_by
                    WHERE m.created_at >= CAST(:from AS date) AND m.created_at < CAST(:to AS date) + 1 AND %s
                    """.formatted(WAREHOUSE_FILTER.formatted("m.warehouse_id")), "created_at", "desc"),

            new Definition("sales", "Sales by date range", "Sales orders placed in the period (excluding drafts)",
                    true, true,
                    List.of(col("order_number", "Order", "text"), col("order_date", "Date", "date"),
                            col("customer", "Customer", "text"), col("warehouse", "Warehouse", "text"),
                            col("status", "Status", "text"), col("lines", "Lines", "number"),
                            col("units", "Units", "number"), col("total_amount", "Total", "money")),
                    """
                    SELECT s.order_number, s.order_date, c.name AS customer, w.code AS warehouse, s.status,
                           (SELECT COUNT(*) FROM sales_order_items i WHERE i.sales_order_id = s.id) AS lines,
                           (SELECT COALESCE(SUM(i.quantity), 0) FROM sales_order_items i WHERE i.sales_order_id = s.id) AS units,
                           s.total_amount
                    FROM sales_orders s
                    JOIN customers c ON c.id = s.customer_id
                    JOIN warehouses w ON w.id = s.warehouse_id
                    WHERE s.status <> 'DRAFT' AND s.order_date BETWEEN :from AND :to AND %s
                    """.formatted(WAREHOUSE_FILTER.formatted("s.warehouse_id")), "order_date", "desc"),

            new Definition("purchases-by-supplier", "Purchases by supplier", "Ordered vs received value per supplier for approved orders in the period",
                    true, false,
                    List.of(col("supplier_code", "Code", "text"), col("supplier", "Supplier", "text"),
                            col("orders", "Orders", "number"), col("units_ordered", "Units ordered", "number"),
                            col("units_received", "Units received", "number"), col("ordered_value", "Ordered value", "money"),
                            col("received_value", "Received value", "money"), col("outstanding_value", "Outstanding", "money")),
                    """
                    SELECT s.code AS supplier_code, s.name AS supplier, COUNT(DISTINCT po.id) AS orders,
                           COALESCE(SUM(i.quantity_ordered), 0) AS units_ordered,
                           COALESCE(SUM(i.quantity_received), 0) AS units_received,
                           COALESCE(SUM(i.line_total), 0) AS ordered_value,
                           COALESCE(SUM(i.quantity_received * i.unit_cost), 0) AS received_value,
                           COALESCE(SUM((i.quantity_ordered - i.quantity_received) * i.unit_cost), 0) AS outstanding_value
                    FROM suppliers s
                    JOIN purchase_orders po ON po.supplier_id = s.id
                         AND po.status IN ('APPROVED','PARTIALLY_RECEIVED','RECEIVED')
                         AND po.order_date BETWEEN :from AND :to
                    JOIN purchase_order_items i ON i.purchase_order_id = po.id
                    GROUP BY s.id
                    """, "ordered_value", "desc"),

            new Definition("product-performance", "Product performance", "Units sold, revenue and gross margin per product in the period",
                    true, true,
                    List.of(col("sku", "SKU", "text"), col("product", "Product", "text"), col("category", "Category", "text"),
                            col("orders", "Orders", "number"), col("units_sold", "Units sold", "number"),
                            col("revenue", "Revenue", "money"), col("cost_of_goods", "Cost of goods", "money"),
                            col("gross_margin", "Gross margin", "money"), col("margin_pct", "Margin %", "percent"),
                            col("on_hand", "On hand now", "number")),
                    """
                    SELECT p.sku, p.name AS product, c.name AS category,
                           COUNT(DISTINCT s.id) AS orders,
                           COALESCE(SUM(i.quantity), 0) AS units_sold,
                           COALESCE(SUM(i.line_total), 0) AS revenue,
                           COALESCE(SUM(i.quantity * p.cost_price), 0) AS cost_of_goods,
                           COALESCE(SUM(i.line_total - i.quantity * p.cost_price), 0) AS gross_margin,
                           CASE WHEN COALESCE(SUM(i.line_total), 0) = 0 THEN 0
                                ELSE ROUND(100 * SUM(i.line_total - i.quantity * p.cost_price) / SUM(i.line_total), 1) END AS margin_pct,
                           (SELECT COALESCE(SUM(b.quantity_on_hand), 0) FROM inventory_balances b WHERE b.product_id = p.id) AS on_hand
                    FROM products p
                    JOIN categories c ON c.id = p.category_id
                    LEFT JOIN (sales_order_items i
                               JOIN sales_orders s ON s.id = i.sales_order_id
                                    AND s.status IN ('CONFIRMED','SHIPPED','DELIVERED')
                                    AND s.order_date BETWEEN :from AND :to AND %s)
                           ON i.product_id = p.id
                    GROUP BY p.id, c.name
                    """.formatted(WAREHOUSE_FILTER.formatted("s.warehouse_id")), "revenue", "desc"),

            new Definition("warehouse-performance", "Warehouse performance", "Inbound and outbound volume, shipments and current stock per warehouse",
                    true, false,
                    List.of(col("code", "Code", "text"), col("warehouse", "Warehouse", "text"),
                            col("units_in", "Units in", "number"), col("units_out", "Units out", "number"),
                            col("movements", "Movements", "number"), col("orders_shipped", "Orders shipped", "number"),
                            col("goods_receipts", "Goods receipts", "number"), col("units_on_hand", "Units on hand", "number"),
                            col("stock_value", "Stock value", "money")),
                    """
                    SELECT w.code, w.name AS warehouse,
                           COALESCE(m.units_in, 0) AS units_in, COALESCE(m.units_out, 0) AS units_out,
                           COALESCE(m.movements, 0) AS movements,
                           (SELECT COUNT(*) FROM sales_orders s WHERE s.warehouse_id = w.id AND s.shipped_at IS NOT NULL
                              AND s.shipped_at >= CAST(:from AS date) AND s.shipped_at < CAST(:to AS date) + 1) AS orders_shipped,
                           (SELECT COUNT(*) FROM goods_receipts g WHERE g.warehouse_id = w.id
                              AND g.received_at >= CAST(:from AS date) AND g.received_at < CAST(:to AS date) + 1) AS goods_receipts,
                           (SELECT COALESCE(SUM(b.quantity_on_hand), 0) FROM inventory_balances b WHERE b.warehouse_id = w.id) AS units_on_hand,
                           (SELECT COALESCE(SUM(b.quantity_on_hand * p.cost_price), 0) FROM inventory_balances b
                              JOIN products p ON p.id = b.product_id WHERE b.warehouse_id = w.id) AS stock_value
                    FROM warehouses w
                    LEFT JOIN (
                        SELECT warehouse_id,
                               SUM(CASE WHEN quantity > 0 THEN quantity ELSE 0 END) AS units_in,
                               SUM(CASE WHEN quantity < 0 THEN -quantity ELSE 0 END) AS units_out,
                               COUNT(*) AS movements
                        FROM inventory_movements
                        WHERE created_at >= CAST(:from AS date) AND created_at < CAST(:to AS date) + 1
                        GROUP BY warehouse_id
                    ) m ON m.warehouse_id = w.id
                    """, "units_out", "desc")
    );

    private static final Map<String, Definition> BY_KEY = DEFINITIONS.stream()
            .collect(Collectors.toMap(Definition::key, Function.identity(), (a, b) -> a, LinkedHashMap::new));

    private final NamedParameterJdbcTemplate jdbc;

    public List<ReportDtos.Info> catalogue() {
        return DEFINITIONS.stream()
                .map(d -> new ReportDtos.Info(d.key(), d.title(), d.description(), d.dateRange(), d.warehouseFilter()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReportDtos.Response run(String key, LocalDate from, LocalDate to, Long warehouseId,
                                   String sort, String direction, int page, int size) {
        Definition def = definition(key);
        if (page < 0 || size < 1 || size > 500) {
            throw new BadRequestException("INVALID_PAGE", "page must be >= 0 and size between 1 and 500");
        }
        MapSqlParameterSource params = params(from, to, warehouseId);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + def.sql() + ") r", params, Long.class);
        params.addValue("limit", size).addValue("offset", (long) page * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM (" + def.sql() + ") r " + orderBy(def, sort, direction) + " LIMIT :limit OFFSET :offset", params)
                .stream().map(ReportService::normalize).toList();
        long count = total == null ? 0 : total;
        return new ReportDtos.Response(def.key(), def.title(), def.columns(), rows, page, size, count,
                (int) Math.ceil(count / (double) size));
    }

    /** Streams the full (unpaginated) report as CSV. */
    @Transactional(readOnly = true)
    public void exportCsv(String key, LocalDate from, LocalDate to, Long warehouseId, String sort, String direction,
                          Writer out) throws IOException {
        Definition def = definition(key);
        MapSqlParameterSource params = params(from, to, warehouseId).addValue("limit", MAX_EXPORT_ROWS);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM (" + def.sql() + ") r " + orderBy(def, sort, direction) + " LIMIT :limit", params);

        out.write('﻿'); // BOM so spreadsheet apps detect UTF-8
        out.write(def.columns().stream().map(c -> csv(c.label())).collect(Collectors.joining(",")));
        out.write("\r\n");
        for (Map<String, Object> row : rows) {
            Map<String, Object> normalized = normalize(row);
            out.write(def.columns().stream().map(c -> csv(normalized.get(c.key()))).collect(Collectors.joining(",")));
            out.write("\r\n");
        }
        out.flush();
    }

    public String fileName(String key) {
        return "stockpilot-" + definition(key).key() + "-" + LocalDate.now(ZoneOffset.UTC) + ".csv";
    }

    private static Definition definition(String key) {
        Definition def = BY_KEY.get(key);
        if (def == null) {
            throw new NotFoundException("Unknown report '" + key + "'");
        }
        return def;
    }

    private static MapSqlParameterSource params(LocalDate from, LocalDate to, Long warehouseId) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.minusDays(89);
        if (start.isAfter(end)) {
            throw new BadRequestException("INVALID_RANGE", "'from' must be on or before 'to'");
        }
        return new MapSqlParameterSource()
                .addValue("from", start)
                .addValue("to", end)
                .addValue("warehouseId", warehouseId);
    }

    /** Sort keys are matched against the report's own column list, so user input never reaches the SQL text. */
    private static String orderBy(Definition def, String sort, String direction) {
        String column = sort == null || sort.isBlank() ? def.defaultSort() : sort;
        if (def.columns().stream().noneMatch(c -> c.key().equals(column))) {
            throw new BadRequestException("INVALID_SORT", "Cannot sort report by '" + column + "'");
        }
        String dir = direction == null || direction.isBlank() ? def.defaultDirection() : direction.toLowerCase();
        if (!dir.equals("asc") && !dir.equals("desc")) {
            throw new BadRequestException("INVALID_SORT", "direction must be asc or desc");
        }
        return "ORDER BY \"" + column + "\" " + dir + " NULLS LAST";
    }

    private static Map<String, Object> normalize(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        row.forEach((k, v) -> result.put(k, switch (v) {
            case Timestamp ts -> ts.toInstant();
            case java.sql.Date d -> d.toLocalDate();
            case null -> null;
            default -> v;
        }));
        return result;
    }

    /** RFC 4180 quoting plus a guard against spreadsheet formula injection in text cells. */
    static String csv(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal bd) {
            return bd.toPlainString();
        }
        if (value instanceof Number) {
            return value.toString();
        }
        String text = value.toString();
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
