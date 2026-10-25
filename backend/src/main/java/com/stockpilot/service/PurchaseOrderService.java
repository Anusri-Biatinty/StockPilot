package com.stockpilot.service;

import com.stockpilot.dto.GoodsReceiptDtos;
import com.stockpilot.dto.PageResponse;
import com.stockpilot.dto.PurchaseOrderDtos;
import com.stockpilot.entity.GoodsReceipt;
import com.stockpilot.entity.GoodsReceiptItem;
import com.stockpilot.entity.MovementType;
import com.stockpilot.entity.NotificationSeverity;
import com.stockpilot.entity.NotificationType;
import com.stockpilot.entity.Product;
import com.stockpilot.entity.PurchaseOrder;
import com.stockpilot.entity.PurchaseOrderItem;
import com.stockpilot.entity.PurchaseOrderStatus;
import com.stockpilot.entity.RoleName;
import com.stockpilot.entity.Supplier;
import com.stockpilot.entity.User;
import com.stockpilot.exception.ApiException;
import com.stockpilot.exception.BadRequestException;
import com.stockpilot.exception.ConflictException;
import com.stockpilot.exception.InvalidStatusTransitionException;
import com.stockpilot.exception.NotFoundException;
import com.stockpilot.mapper.ProcurementMapper;
import com.stockpilot.repository.GoodsReceiptRepository;
import com.stockpilot.repository.PurchaseOrderRepository;
import com.stockpilot.repository.SupplierProductRepository;
import com.stockpilot.repository.UserRepository;
import com.stockpilot.security.AuthenticatedUser;
import com.stockpilot.security.SecurityUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Purchase order workflow: DRAFT -> SUBMITTED -> APPROVED -> PARTIALLY_RECEIVED -> RECEIVED.
 *
 * <p>Rules enforced here:
 * <ul>
 *   <li>Only drafts can be edited; totals are always computed server-side.</li>
 *   <li>Non-admin users cannot approve a purchase order they created (segregation of duties).</li>
 *   <li>Cancellation is allowed until the first goods receipt.</li>
 *   <li>Receipts lock the order row, can never exceed the outstanding quantity, and an optional
 *       Idempotency-Key makes retried requests return the original receipt instead of adding stock twice.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private static final List<RoleName> PROCUREMENT_ROLES = List.of(RoleName.ADMIN, RoleName.INVENTORY_MANAGER);

    private final PurchaseOrderRepository orderRepository;
    private final GoodsReceiptRepository receiptRepository;
    private final SupplierProductRepository supplierProductRepository;
    private final UserRepository userRepository;
    private final SupplierService supplierService;
    private final InventoryService inventoryService;
    private final NotificationService notificationService;
    private final DocumentNumberService numberService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderDtos.Summary> search(String search, PurchaseOrderStatus status, Long supplierId,
                                                          LocalDate from, LocalDate to, Pageable pageable) {
        Specification<PurchaseOrder> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("poNumber")), like),
                        cb.like(cb.lower(root.get("supplier").get("name")), like)));
            }
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (supplierId != null) {
                p.add(cb.equal(root.get("supplier").get("id"), supplierId));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("orderDate"), from));
            }
            if (to != null) {
                p.add(cb.lessThanOrEqualTo(root.get("orderDate"), to));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(orderRepository.findAll(spec, pageable), ProcurementMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderDtos.Detail get(Long id) {
        return toDetail(findDetailed(id));
    }

    @Transactional
    public PurchaseOrderDtos.Detail create(PurchaseOrderDtos.Request request) {
        Supplier supplier = activeSupplier(request.supplierId());
        PurchaseOrder po = new PurchaseOrder();
        po.setPoNumber(numberService.next(DocumentNumberService.DocumentType.PURCHASE_ORDER));
        po.setSupplier(supplier);
        po.setWarehouse(inventoryService.activeWarehouse(request.warehouseId()));
        po.setOrderDate(LocalDate.now(ZoneOffset.UTC));
        po.setExpectedDate(request.expectedDate());
        po.setNotes(CategoryService.blankToNull(request.notes()));
        po.setCreatedBy(currentUser());
        applyLines(po, request.items());
        orderRepository.save(po);
        auditService.record("PO_CREATED", "PurchaseOrder", po.getId(), Map.of("poNumber", po.getPoNumber(),
                "supplier", supplier.getCode(), "total", po.getTotalAmount()));
        return toDetail(po);
    }

    @Transactional
    public PurchaseOrderDtos.Detail update(Long id, PurchaseOrderDtos.Request request) {
        PurchaseOrder po = lock(id);
        if (po.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new ConflictException("PO_NOT_EDITABLE", "Only draft purchase orders can be edited");
        }
        if (!po.getSupplier().getId().equals(request.supplierId())) {
            po.setSupplier(activeSupplier(request.supplierId()));
        }
        if (!po.getWarehouse().getId().equals(request.warehouseId())) {
            po.setWarehouse(inventoryService.activeWarehouse(request.warehouseId()));
        }
        po.setExpectedDate(request.expectedDate());
        po.setNotes(CategoryService.blankToNull(request.notes()));
        applyLines(po, request.items());
        auditService.record("PO_UPDATED", "PurchaseOrder", id, Map.of("total", po.getTotalAmount()));
        return toDetail(po);
    }

    @Transactional
    public PurchaseOrderDtos.Detail submit(Long id) {
        PurchaseOrder po = lock(id);
        transition(po, PurchaseOrderStatus.SUBMITTED);
        po.setSubmittedAt(Instant.now());
        po.setRejectionReason(null);
        AuthenticatedUser actor = SecurityUtils.requireCurrentUser();
        List<User> approvers = userRepository.findByRole_NameInAndEnabledTrue(PROCUREMENT_ROLES).stream()
                .filter(u -> !u.getId().equals(actor.id()) || u.roleName() == RoleName.ADMIN)
                .toList();
        notificationService.notifyUsers(approvers, NotificationType.PO_PENDING_APPROVAL, NotificationSeverity.INFO,
                "Purchase order awaiting approval: " + po.getPoNumber(),
                actor.fullName() + " submitted " + po.getPoNumber() + " (" + po.getSupplier().getName() + ") for "
                        + po.getTotalAmount() + ".", link(po));
        auditService.record("PO_SUBMITTED", "PurchaseOrder", id, Map.of("poNumber", po.getPoNumber()));
        return toDetail(po);
    }

    @Transactional
    public PurchaseOrderDtos.Detail approve(Long id) {
        PurchaseOrder po = lock(id);
        if (!po.getStatus().canTransitionTo(PurchaseOrderStatus.APPROVED)) {
            throw new InvalidStatusTransitionException("Purchase order", po.getStatus(), PurchaseOrderStatus.APPROVED);
        }
        AuthenticatedUser actor = SecurityUtils.requireCurrentUser();
        if (!actor.isAdmin() && po.getCreatedBy() != null && po.getCreatedBy().getId().equals(actor.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SELF_APPROVAL_NOT_ALLOWED",
                    "You cannot approve a purchase order you created. Ask another manager or an admin.");
        }
        transition(po, PurchaseOrderStatus.APPROVED);
        po.setApprovedBy(userRepository.getReferenceById(actor.id()));
        po.setApprovedAt(Instant.now());
        notifyCreator(po, NotificationType.PO_APPROVED, "Purchase order approved: " + po.getPoNumber(),
                actor.fullName() + " approved " + po.getPoNumber() + ". It can now be received.");
        auditService.record("PO_APPROVED", "PurchaseOrder", id, Map.of("poNumber", po.getPoNumber(),
                "total", po.getTotalAmount()));
        return toDetail(po);
    }

    /** Rejection sends the order back to DRAFT with the reason, so it can be fixed and resubmitted. */
    @Transactional
    public PurchaseOrderDtos.Detail reject(Long id, String reason) {
        PurchaseOrder po = lock(id);
        if (po.getStatus() != PurchaseOrderStatus.SUBMITTED) {
            throw new InvalidStatusTransitionException("Purchase order", po.getStatus(), PurchaseOrderStatus.DRAFT);
        }
        po.setStatus(PurchaseOrderStatus.DRAFT);
        po.setRejectionReason(reason.trim());
        po.setSubmittedAt(null);
        notifyCreator(po, NotificationType.PO_REJECTED, "Purchase order returned: " + po.getPoNumber(),
                "Reason: " + reason.trim());
        auditService.record("PO_REJECTED", "PurchaseOrder", id, Map.of("poNumber", po.getPoNumber(), "reason", reason.trim()));
        return toDetail(po);
    }

    @Transactional
    public PurchaseOrderDtos.Detail cancel(Long id) {
        PurchaseOrder po = lock(id);
        if (po.hasReceivedAnything()) {
            throw new ConflictException("PO_HAS_RECEIPTS", "Goods have already been received against this order");
        }
        transition(po, PurchaseOrderStatus.CANCELLED);
        po.setCancelledAt(Instant.now());
        auditService.record("PO_CANCELLED", "PurchaseOrder", id, Map.of("poNumber", po.getPoNumber()));
        return toDetail(po);
    }

    @Transactional
    public GoodsReceiptDtos.Response receive(Long id, PurchaseOrderDtos.ReceiveRequest request, String idempotencyKey) {
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        if (key != null && key.length() > 80) {
            throw new BadRequestException("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be at most 80 characters");
        }

        // The row lock makes concurrent receipts for the same order run one after another.
        PurchaseOrder po = lock(id);

        if (key != null) {
            var previous = receiptRepository.findByIdempotencyKey(key);
            if (previous.isPresent()) {
                if (!previous.get().getPurchaseOrder().getId().equals(id)) {
                    throw new ConflictException("IDEMPOTENCY_KEY_REUSED", "This Idempotency-Key was used for another order");
                }
                return ProcurementMapper.toResponse(previous.get());
            }
        }
        if (!po.getStatus().isReceivable()) {
            throw new ConflictException("PO_NOT_RECEIVABLE",
                    "Only approved or partially received orders can be received (current status: " + po.getStatus() + ")");
        }

        Map<Long, PurchaseOrderItem> itemsById = po.getItems().stream()
                .collect(Collectors.toMap(PurchaseOrderItem::getId, Function.identity()));
        Set<Long> seen = new HashSet<>();
        for (var line : request.items()) {
            if (!seen.add(line.purchaseOrderItemId())) {
                throw new BadRequestException("DUPLICATE_LINE", "Each order line can appear only once per receipt");
            }
            PurchaseOrderItem item = itemsById.get(line.purchaseOrderItemId());
            if (item == null) {
                throw new BadRequestException("UNKNOWN_LINE", "Line " + line.purchaseOrderItemId() + " does not belong to " + po.getPoNumber());
            }
            if (line.quantity() > item.remainingQuantity()) {
                throw new ConflictException("OVER_RECEIPT", "Cannot receive " + line.quantity() + " of "
                        + item.getProduct().getSku() + ": only " + item.remainingQuantity() + " outstanding");
            }
        }

        GoodsReceipt receipt = new GoodsReceipt();
        receipt.setReceiptNumber(numberService.next(DocumentNumberService.DocumentType.GOODS_RECEIPT));
        receipt.setPurchaseOrder(po);
        receipt.setWarehouse(po.getWarehouse());
        receipt.setReceivedBy(currentUser());
        receipt.setNotes(CategoryService.blankToNull(request.notes()));
        receipt.setIdempotencyKey(key);
        receiptRepository.save(receipt);

        var lines = request.items().stream()
                .sorted(Comparator.comparing(l -> itemsById.get(l.purchaseOrderItemId()).getProduct().getId()))
                .toList();
        int units = 0;
        for (var line : lines) {
            PurchaseOrderItem item = itemsById.get(line.purchaseOrderItemId());
            item.setQuantityReceived(item.getQuantityReceived() + line.quantity());

            GoodsReceiptItem gri = new GoodsReceiptItem();
            gri.setPurchaseOrderItem(item);
            gri.setProduct(item.getProduct());
            gri.setQuantityReceived(line.quantity());
            receipt.addItem(gri);

            inventoryService.increase(item.getProduct(), po.getWarehouse(), line.quantity(),
                    MovementType.PURCHASE_RECEIPT, InventoryService.REF_GOODS_RECEIPT, receipt.getId(), null);
            units += line.quantity();
        }

        PurchaseOrderStatus next = po.isFullyReceived() ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED;
        transition(po, next);

        notificationService.notifyRoles(PROCUREMENT_ROLES, NotificationType.PO_RECEIVED, NotificationSeverity.INFO,
                (next == PurchaseOrderStatus.RECEIVED ? "Purchase order received: " : "Partial receipt: ") + po.getPoNumber(),
                units + " units received into " + po.getWarehouse().getCode() + " (" + receipt.getReceiptNumber() + ").",
                link(po));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("receiptNumber", receipt.getReceiptNumber());
        details.put("poNumber", po.getPoNumber());
        details.put("units", units);
        details.put("status", next);
        auditService.record("GOODS_RECEIVED", "PurchaseOrder", id, details);
        return ProcurementMapper.toResponse(receipt);
    }

    // ------------------------------------------------------------------ goods receipt queries

    @Transactional(readOnly = true)
    public PageResponse<GoodsReceiptDtos.Response> searchReceipts(String search, Long purchaseOrderId, Long supplierId,
                                                                   LocalDate from, LocalDate to, Pageable pageable) {
        Specification<GoodsReceipt> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            var po = root.join("purchaseOrder");
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("receiptNumber")), like), cb.like(cb.lower(po.get("poNumber")), like)));
            }
            if (purchaseOrderId != null) {
                p.add(cb.equal(po.get("id"), purchaseOrderId));
            }
            if (supplierId != null) {
                p.add(cb.equal(po.get("supplier").get("id"), supplierId));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("receivedAt"), from.atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            if (to != null) {
                p.add(cb.lessThan(root.get("receivedAt"), to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(receiptRepository.findAll(spec, pageable), ProcurementMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public GoodsReceiptDtos.Response getReceipt(Long id) {
        return receiptRepository.findDetailedById(id).map(ProcurementMapper::toResponse)
                .orElseThrow(() -> NotFoundException.of("Goods receipt", id));
    }

    // ------------------------------------------------------------------ internals

    /**
     * Updates lines in place (matched by product) rather than delete-and-reinsert, which would trip the
     * (order, product) unique constraint because Hibernate flushes inserts before deletes.
     */
    private void applyLines(PurchaseOrder po, List<PurchaseOrderDtos.Line> lines) {
        Set<Long> productIds = new HashSet<>();
        for (var line : lines) {
            if (!productIds.add(line.productId())) {
                throw new BadRequestException("DUPLICATE_LINE", "Each product can appear only once per order");
            }
        }
        Map<Long, PurchaseOrderItem> existing = new HashMap<>();
        po.getItems().forEach(i -> existing.put(i.getProduct().getId(), i));
        po.getItems().removeIf(i -> !productIds.contains(i.getProduct().getId()));

        for (var line : lines) {
            PurchaseOrderItem item = existing.get(line.productId());
            if (item == null) {
                Product product = inventoryService.activeProduct(line.productId());
                item = new PurchaseOrderItem();
                item.setProduct(product);
                po.addItem(item);
            }
            BigDecimal unitCost = line.unitCost() != null ? line.unitCost() : defaultCost(po.getSupplier(), item.getProduct());
            item.setQuantityOrdered(line.quantity());
            item.setUnitCost(unitCost.setScale(2, RoundingMode.HALF_UP));
            item.setLineTotal(item.getUnitCost().multiply(BigDecimal.valueOf(line.quantity())));
        }
        po.recalculateTotal();
    }

    private BigDecimal defaultCost(Supplier supplier, Product product) {
        return supplierProductRepository.findBySupplierIdAndProductId(supplier.getId(), product.getId())
                .map(sp -> sp.getUnitCost())
                .orElse(product.getCostPrice());
    }

    private static void transition(PurchaseOrder po, PurchaseOrderStatus target) {
        if (!po.getStatus().canTransitionTo(target)) {
            throw new InvalidStatusTransitionException("Purchase order", po.getStatus(), target);
        }
        po.setStatus(target);
    }

    private void notifyCreator(PurchaseOrder po, NotificationType type, String title, String message) {
        if (po.getCreatedBy() != null) {
            notificationService.notifyUsers(List.of(po.getCreatedBy()), type,
                    type == NotificationType.PO_REJECTED ? NotificationSeverity.WARNING : NotificationSeverity.INFO,
                    title, message, link(po));
        }
    }

    private Supplier activeSupplier(Long id) {
        Supplier supplier = supplierService.find(id);
        if (!supplier.isActive()) {
            throw new ConflictException("SUPPLIER_INACTIVE", "Supplier " + supplier.getName() + " is inactive");
        }
        return supplier;
    }

    private PurchaseOrder lock(Long id) {
        return orderRepository.findForUpdate(id).orElseThrow(() -> NotFoundException.of("Purchase order", id));
    }

    private PurchaseOrder findDetailed(Long id) {
        return orderRepository.findDetailedById(id).orElseThrow(() -> NotFoundException.of("Purchase order", id));
    }

    private PurchaseOrderDtos.Detail toDetail(PurchaseOrder po) {
        List<GoodsReceipt> receipts = po.getId() == null ? List.of()
                : receiptRepository.findByPurchaseOrderIdOrderByReceivedAtAsc(po.getId());
        return ProcurementMapper.toDetail(po, receipts);
    }

    private User currentUser() {
        return SecurityUtils.currentUser().map(u -> userRepository.getReferenceById(u.id())).orElse(null);
    }

    private static String link(PurchaseOrder po) {
        return "/purchase-orders/" + Objects.requireNonNull(po.getId());
    }
}
