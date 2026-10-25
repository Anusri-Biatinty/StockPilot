package com.stockpilot.service;

import com.stockpilot.dto.InventoryDtos;
import com.stockpilot.dto.PageResponse;
import com.stockpilot.dto.StockStatus;
import com.stockpilot.entity.InventoryBalance;
import com.stockpilot.entity.InventoryMovement;
import com.stockpilot.entity.MovementType;
import com.stockpilot.entity.NotificationSeverity;
import com.stockpilot.entity.NotificationType;
import com.stockpilot.entity.Product;
import com.stockpilot.entity.RoleName;
import com.stockpilot.entity.StockTransfer;
import com.stockpilot.entity.Warehouse;
import com.stockpilot.exception.BadRequestException;
import com.stockpilot.exception.ConflictException;
import com.stockpilot.exception.InsufficientStockException;
import com.stockpilot.exception.NotFoundException;
import com.stockpilot.mapper.InventoryMapper;
import com.stockpilot.repository.InventoryBalanceRepository;
import com.stockpilot.repository.InventoryMovementRepository;
import com.stockpilot.repository.ProductRepository;
import com.stockpilot.repository.StockTransferRepository;
import com.stockpilot.repository.UserRepository;
import com.stockpilot.repository.WarehouseRepository;
import com.stockpilot.security.SecurityUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The only component that changes stock. Business rules:
 * <ul>
 *   <li>Every change to quantity on hand writes an {@link InventoryMovement} in the same transaction.</li>
 *   <li>Stock can never go negative, and reserved stock cannot be issued, transferred or adjusted away:
 *       outbound operations are limited to {@code onHand - reserved}.</li>
 *   <li>Each balance row is locked with SELECT ... FOR UPDATE before it is read and changed, so concurrent
 *       operations on the same product/warehouse are serialised. When several rows are involved they are
 *       locked in ascending (product, warehouse) order to avoid deadlocks.</li>
 *   <li>Crossing the reorder threshold (or reaching zero) notifies admins and inventory managers once.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    public static final String REF_MANUAL = "MANUAL";
    public static final String REF_TRANSFER = "STOCK_TRANSFER";
    public static final String REF_GOODS_RECEIPT = "GOODS_RECEIPT";
    public static final String REF_SALES_ORDER = "SALES_ORDER";

    private final InventoryBalanceRepository balanceRepository;
    private final InventoryMovementRepository movementRepository;
    private final StockTransferRepository transferRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final DocumentNumberService numberService;
    private final AuditService auditService;
    private final NamedParameterJdbcTemplate jdbc;

    // ------------------------------------------------------------------ building blocks (used by other services)

    /** Adds stock (receipts, purchase receipts, positive adjustments). */
    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryMovement increase(Product product, Warehouse warehouse, int quantity, MovementType type,
                                      String referenceType, Long referenceId, String reason) {
        requirePositive(quantity);
        InventoryBalance balance = lock(product, warehouse);
        return record(balance, product, warehouse, quantity, type, referenceType, referenceId, reason);
    }

    /** Removes unreserved stock (issues, negative adjustments, transfer out). */
    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryMovement decrease(Product product, Warehouse warehouse, int quantity, MovementType type,
                                      String referenceType, Long referenceId, String reason) {
        requirePositive(quantity);
        InventoryBalance balance = lock(product, warehouse);
        ensureAvailable(balance, product, warehouse, quantity);
        return record(balance, product, warehouse, -quantity, type, referenceType, referenceId, reason);
    }

    /** Reserves stock for a confirmed sales order; on-hand quantity is unchanged. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(Product product, Warehouse warehouse, int quantity) {
        requirePositive(quantity);
        InventoryBalance balance = lock(product, warehouse);
        ensureAvailable(balance, product, warehouse, quantity);
        balance.setQuantityReserved(balance.getQuantityReserved() + quantity);
    }

    /** Releases a reservation (order cancelled). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(Product product, Warehouse warehouse, int quantity) {
        InventoryBalance balance = lock(product, warehouse);
        if (balance.getQuantityReserved() < quantity) {
            throw new IllegalStateException("Reservation exceeds reserved stock for " + product.getSku());
        }
        balance.setQuantityReserved(balance.getQuantityReserved() - quantity);
    }

    /** Ships reserved stock: removes it from both reserved and on-hand and records the movement. */
    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryMovement consumeReservation(Product product, Warehouse warehouse, int quantity, Long salesOrderId) {
        InventoryBalance balance = lock(product, warehouse);
        if (balance.getQuantityReserved() < quantity || balance.getQuantityOnHand() < quantity) {
            throw new IllegalStateException("Reserved stock missing for " + product.getSku());
        }
        balance.setQuantityReserved(balance.getQuantityReserved() - quantity);
        return record(balance, product, warehouse, -quantity, MovementType.SALES_SHIPMENT, REF_SALES_ORDER,
                salesOrderId, null);
    }

    // ------------------------------------------------------------------ user-facing operations

    @Transactional
    public InventoryDtos.MovementResponse receive(InventoryDtos.ReceiptRequest request) {
        Product product = activeProduct(request.productId());
        Warehouse warehouse = activeWarehouse(request.warehouseId());
        InventoryMovement movement = increase(product, warehouse, request.quantity(), MovementType.RECEIPT,
                REF_MANUAL, null, CategoryService.blankToNull(request.reason()));
        auditService.record("STOCK_RECEIVED", "Product", product.getId(), stockDetails(product, warehouse,
                request.quantity(), request.reason()));
        return InventoryMapper.toResponse(movement);
    }

    @Transactional
    public InventoryDtos.MovementResponse issue(InventoryDtos.IssueRequest request) {
        Product product = findProduct(request.productId());
        Warehouse warehouse = findWarehouse(request.warehouseId());
        InventoryMovement movement = decrease(product, warehouse, request.quantity(), MovementType.ISSUE,
                REF_MANUAL, null, request.reason().trim());
        auditService.record("STOCK_ISSUED", "Product", product.getId(), stockDetails(product, warehouse,
                -request.quantity(), request.reason()));
        return InventoryMapper.toResponse(movement);
    }

    @Transactional
    public InventoryDtos.MovementResponse adjust(InventoryDtos.AdjustmentRequest request) {
        if (request.quantityChange() == 0) {
            throw new BadRequestException("ZERO_ADJUSTMENT", "Adjustment quantity cannot be zero");
        }
        Product product = findProduct(request.productId());
        Warehouse warehouse = findWarehouse(request.warehouseId());
        String reason = request.reason().trim();
        int change = request.quantityChange();
        InventoryMovement movement = change > 0
                ? increase(product, warehouse, change, MovementType.ADJUSTMENT, REF_MANUAL, null, reason)
                : decrease(product, warehouse, -change, MovementType.ADJUSTMENT, REF_MANUAL, null, reason);
        auditService.record("STOCK_ADJUSTED", "Product", product.getId(), stockDetails(product, warehouse, change, reason));
        return InventoryMapper.toResponse(movement);
    }

    @Transactional
    public InventoryDtos.TransferResponse transfer(InventoryDtos.TransferRequest request) {
        if (request.fromWarehouseId().equals(request.toWarehouseId())) {
            throw new BadRequestException("SAME_WAREHOUSE", "Source and destination warehouses must differ");
        }
        Product product = findProduct(request.productId());
        Warehouse from = findWarehouse(request.fromWarehouseId());
        Warehouse to = activeWarehouse(request.toWarehouseId());
        int quantity = request.quantity();

        // Lock both rows in a fixed order so two opposite transfers cannot deadlock.
        balanceRepository.ensureExists(product.getId(), to.getId());
        if (from.getId() < to.getId()) {
            lock(product, from);
            lock(product, to);
        } else {
            lock(product, to);
            lock(product, from);
        }

        StockTransfer transfer = new StockTransfer();
        transfer.setTransferNumber(numberService.next(DocumentNumberService.DocumentType.TRANSFER));
        transfer.setProduct(product);
        transfer.setFromWarehouse(from);
        transfer.setToWarehouse(to);
        transfer.setQuantity(quantity);
        transfer.setNotes(CategoryService.blankToNull(request.notes()));
        transfer.setCreatedBy(currentUserRef());
        transferRepository.save(transfer);

        String reason = "Transfer " + transfer.getTransferNumber() + " " + from.getCode() + " -> " + to.getCode();
        decrease(product, from, quantity, MovementType.TRANSFER_OUT, REF_TRANSFER, transfer.getId(), reason);
        increase(product, to, quantity, MovementType.TRANSFER_IN, REF_TRANSFER, transfer.getId(), reason);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("transferNumber", transfer.getTransferNumber());
        details.put("sku", product.getSku());
        details.put("from", from.getCode());
        details.put("to", to.getCode());
        details.put("quantity", quantity);
        auditService.record("STOCK_TRANSFERRED", "StockTransfer", transfer.getId(), details);
        return InventoryMapper.toResponse(transfer);
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public PageResponse<InventoryDtos.BalanceResponse> searchBalances(String search, Long warehouseId, Long productId,
                                                                      StockStatus status, Pageable pageable) {
        Specification<InventoryBalance> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            var product = root.join("product");
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(product.get("name")), like), cb.like(cb.lower(product.get("sku")), like)));
            }
            if (warehouseId != null) {
                p.add(cb.equal(root.get("warehouse").get("id"), warehouseId));
            }
            if (productId != null) {
                p.add(cb.equal(product.get("id"), productId));
            }
            if (status != null) {
                var onHand = root.<Integer>get("quantityOnHand");
                var threshold = product.<Integer>get("reorderThreshold");
                p.add(switch (status) {
                    case OUT_OF_STOCK -> cb.equal(onHand, 0);
                    case LOW_STOCK -> cb.and(cb.greaterThan(onHand, 0), cb.lessThanOrEqualTo(onHand, threshold));
                    case IN_STOCK -> cb.greaterThan(onHand, threshold);
                });
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(balanceRepository.findAll(spec, pageable), InventoryMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryDtos.MovementResponse> searchMovements(Long productId, Long warehouseId,
                                                                        MovementType type, String search,
                                                                        LocalDate from, LocalDate to,
                                                                        Pageable pageable) {
        Specification<InventoryMovement> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (productId != null) {
                p.add(cb.equal(root.get("product").get("id"), productId));
            }
            if (warehouseId != null) {
                p.add(cb.equal(root.get("warehouse").get("id"), warehouseId));
            }
            if (type != null) {
                p.add(cb.equal(root.get("movementType"), type));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                var product = root.join("product");
                p.add(cb.or(cb.like(cb.lower(product.get("name")), like), cb.like(cb.lower(product.get("sku")), like)));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            if (to != null) {
                p.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(movementRepository.findAll(spec, pageable), InventoryMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryDtos.TransferResponse> transfers(Pageable pageable) {
        return PageResponse.of(transferRepository.findAll(pageable), InventoryMapper::toResponse);
    }

    /** Active products whose total on-hand stock is at or below their reorder threshold. */
    @Transactional(readOnly = true)
    public List<InventoryDtos.StockAlert> alerts() {
        String sql = """
                SELECT p.id, p.sku, p.name, p.reorder_threshold, COALESCE(SUM(b.quantity_on_hand), 0) AS on_hand
                FROM products p
                LEFT JOIN inventory_balances b ON b.product_id = p.id
                WHERE p.status = 'ACTIVE'
                GROUP BY p.id
                HAVING COALESCE(SUM(b.quantity_on_hand), 0) <= p.reorder_threshold
                ORDER BY on_hand, p.name
                """;
        return jdbc.query(sql, new MapSqlParameterSource(), (rs, i) -> {
            long onHand = rs.getLong("on_hand");
            int threshold = rs.getInt("reorder_threshold");
            return new InventoryDtos.StockAlert(rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
                    onHand, threshold, StockStatus.of(onHand, threshold));
        });
    }

    // ------------------------------------------------------------------ internals

    private InventoryBalance lock(Product product, Warehouse warehouse) {
        balanceRepository.ensureExists(product.getId(), warehouse.getId());
        return balanceRepository.findForUpdate(product.getId(), warehouse.getId())
                .orElseThrow(() -> new IllegalStateException("Balance row missing after upsert"));
    }

    private static void ensureAvailable(InventoryBalance balance, Product product, Warehouse warehouse, int quantity) {
        if (balance.available() < quantity) {
            throw new InsufficientStockException(product.getSku(), warehouse.getCode(), quantity, balance.available());
        }
    }

    private InventoryMovement record(InventoryBalance balance, Product product, Warehouse warehouse, int delta,
                                     MovementType type, String referenceType, Long referenceId, String reason) {
        long totalBefore = balanceRepository.totalOnHand(product.getId());
        balance.setQuantityOnHand(balance.getQuantityOnHand() + delta);
        if (balance.getQuantityOnHand() < 0) {
            throw new IllegalStateException("Negative stock for " + product.getSku());
        }

        InventoryMovement movement = new InventoryMovement();
        movement.setProduct(product);
        movement.setWarehouse(warehouse);
        movement.setMovementType(type);
        movement.setQuantity(delta);
        movement.setBalanceAfter(balance.getQuantityOnHand());
        movement.setUnitCost(product.getCostPrice());
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setReason(reason);
        movement.setPerformedBy(currentUserRef());
        movementRepository.save(movement);
        log.debug("{} {} {} @{} -> {}", type, delta, product.getSku(), warehouse.getCode(), balance.getQuantityOnHand());

        notifyOnThresholdCrossing(product, totalBefore, totalBefore + delta);
        return movement;
    }

    private void notifyOnThresholdCrossing(Product product, long before, long after) {
        int threshold = product.getReorderThreshold();
        String link = "/products/" + product.getId();
        List<RoleName> recipients = List.of(RoleName.ADMIN, RoleName.INVENTORY_MANAGER);
        if (after <= 0 && before > 0) {
            notificationService.notifyRoles(recipients, NotificationType.OUT_OF_STOCK, NotificationSeverity.CRITICAL,
                    "Out of stock: " + product.getName(),
                    product.getSku() + " has no units left in any warehouse.", link);
        } else if (after > 0 && after <= threshold && before > threshold) {
            notificationService.notifyRoles(recipients, NotificationType.LOW_STOCK, NotificationSeverity.WARNING,
                    "Low stock: " + product.getName(),
                    product.getSku() + " has " + after + " units on hand (reorder threshold " + threshold + ").", link);
        }
    }

    private com.stockpilot.entity.User currentUserRef() {
        return SecurityUtils.currentUser().map(u -> userRepository.getReferenceById(u.id())).orElse(null);
    }

    Product findProduct(Long id) {
        return productRepository.findById(id).orElseThrow(() -> NotFoundException.of("Product", id));
    }

    Product activeProduct(Long id) {
        Product product = findProduct(id);
        if (!product.isActive()) {
            throw new ConflictException("PRODUCT_ARCHIVED", "Product " + product.getSku() + " is archived");
        }
        return product;
    }

    Warehouse findWarehouse(Long id) {
        return warehouseRepository.findById(id).orElseThrow(() -> NotFoundException.of("Warehouse", id));
    }

    Warehouse activeWarehouse(Long id) {
        Warehouse warehouse = findWarehouse(id);
        if (!warehouse.isActive()) {
            throw new ConflictException("WAREHOUSE_INACTIVE", "Warehouse " + warehouse.getCode() + " is inactive");
        }
        return warehouse;
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new BadRequestException("INVALID_QUANTITY", "Quantity must be greater than zero");
        }
    }

    private static Map<String, Object> stockDetails(Product product, Warehouse warehouse, int change, String reason) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("sku", product.getSku());
        details.put("warehouse", warehouse.getCode());
        details.put("change", change);
        if (reason != null && !reason.isBlank()) {
            details.put("reason", reason.trim());
        }
        return details;
    }
}
