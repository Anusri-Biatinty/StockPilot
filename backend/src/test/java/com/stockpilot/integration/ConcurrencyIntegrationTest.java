package com.stockpilot.integration;

import com.stockpilot.dto.InventoryDtos;
import com.stockpilot.exception.InsufficientStockException;
import com.stockpilot.service.InventoryService;
import com.stockpilot.service.SalesOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fires many simultaneous stock operations at the service layer (each in its own transaction and thread)
 * and checks that row locking prevents overselling and negative stock.
 */
class ConcurrencyIntegrationTest extends IntegrationTestBase {

    @Autowired SalesOrderService salesOrderService;
    @Autowired InventoryService inventoryService;

    @Test
    void concurrentOrderConfirmationsNeverOversell() throws Exception {
        long category = createCategory("Hot items");
        long product = createProduct(category, "HOT-1", 10, 5, 0);
        long warehouse = createWarehouse("MAIN");
        long customer = createCustomer("Rush Buyer");
        receiveStock(product, warehouse, 20);

        List<Long> orderIds = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            orderIds.add(body(doPost("/api/v1/sales-orders", sales, Map.of("customerId", customer, "warehouseId", warehouse,
                    "items", List.of(Map.of("productId", product, "quantity", 3))))).get("id").asLong());
        }

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        runConcurrently(orderIds.stream().<Callable<Void>>map(id -> () -> {
            try {
                salesOrderService.confirm(id);
                succeeded.incrementAndGet();
            } catch (InsufficientStockException e) {
                rejected.incrementAndGet();
            }
            return null;
        }).toList());

        // 20 units / 3 per order = 6 orders fit; the other 6 must be refused.
        assertThat(succeeded.get()).isEqualTo(6);
        assertThat(rejected.get()).isEqualTo(6);
        assertThat(reserved(product, warehouse)).isEqualTo(18);
        assertThat(onHand(product, warehouse)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales_orders WHERE status = 'CONFIRMED'", Integer.class)).isEqualTo(6);
    }

    @Test
    void concurrentIssuesAndOpposingTransfersKeepStockConsistent() throws Exception {
        long category = createCategory("Parts");
        long product = createProduct(category, "PART-1", 10, 5, 0);
        long east = createWarehouse("EAST");
        long west = createWarehouse("WEST");
        receiveStock(product, east, 50);
        receiveStock(product, west, 50);

        AtomicInteger issued = new AtomicInteger();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            tasks.add(() -> {
                try {
                    inventoryService.issue(new InventoryDtos.IssueRequest(product, east, 2, "Concurrent issue"));
                    issued.addAndGet(2);
                } catch (InsufficientStockException ignored) {
                    // expected once the warehouse runs dry
                }
                return null;
            });
        }
        // Opposing transfers lock both rows; the fixed lock order must prevent deadlocks.
        for (int i = 0; i < 10; i++) {
            boolean eastToWest = i % 2 == 0;
            tasks.add(() -> {
                try {
                    inventoryService.transfer(new InventoryDtos.TransferRequest(product,
                            eastToWest ? east : west, eastToWest ? west : east, 1, null));
                } catch (InsufficientStockException ignored) {
                    // possible if east is already empty
                }
                return null;
            });
        }
        runConcurrently(tasks);

        int eastNow = onHand(product, east);
        int westNow = onHand(product, west);
        assertThat(eastNow).isGreaterThanOrEqualTo(0);
        assertThat(eastNow + westNow).isEqualTo(100 - issued.get());
        Integer ledger = jdbc.queryForObject("SELECT SUM(quantity) FROM inventory_movements WHERE product_id = ?", Integer.class, product);
        assertThat(ledger).isEqualTo(eastNow + westNow);
    }

    private static void runConcurrently(List<Callable<Void>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(tasks.size(), 16));
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            for (Future<Void> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
