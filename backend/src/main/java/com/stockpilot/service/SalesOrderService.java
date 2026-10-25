package com.stockpilot.service;

import com.stockpilot.dto.PageResponse;
import com.stockpilot.dto.SalesOrderDtos;
import com.stockpilot.entity.Customer;
import com.stockpilot.entity.InventoryBalance;
import com.stockpilot.entity.NotificationSeverity;
import com.stockpilot.entity.NotificationType;
import com.stockpilot.entity.Product;
import com.stockpilot.entity.ReservationStatus;
import com.stockpilot.entity.SalesOrder;
import com.stockpilot.entity.SalesOrderItem;
import com.stockpilot.entity.SalesOrderStatus;
import com.stockpilot.entity.Shipment;
import com.stockpilot.entity.StockReservation;
import com.stockpilot.exception.BadRequestException;
import com.stockpilot.exception.ConflictException;
import com.stockpilot.exception.InvalidStatusTransitionException;
import com.stockpilot.exception.NotFoundException;
import com.stockpilot.mapper.SalesMapper;
import com.stockpilot.repository.InventoryBalanceRepository;
import com.stockpilot.repository.SalesOrderRepository;
import com.stockpilot.repository.ShipmentRepository;
import com.stockpilot.repository.StockReservationRepository;
import com.stockpilot.repository.UserRepository;
import com.stockpilot.security.SecurityUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
import java.util.Set;

/**
 * Sales order lifecycle: DRAFT -> CONFIRMED -> SHIPPED -> DELIVERED, with cancellation from DRAFT or CONFIRMED.
 *
 * <ul>
 *   <li>Prices come from the product catalogue at the time the line is created; totals are computed here.</li>
 *   <li>Confirming reserves stock for every line in one transaction (all or nothing). Reservations take
 *       balance row locks in product order, so concurrent confirmations can never oversell.</li>
 *   <li>Shipping consumes the reservations and issues the stock; cancelling releases them.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SalesOrderService {

    private final SalesOrderRepository orderRepository;
    private final StockReservationRepository reservationRepository;
    private final ShipmentRepository shipmentRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final UserRepository userRepository;
    private final CustomerService customerService;
    private final InventoryService inventoryService;
    private final NotificationService notificationService;
    private final DocumentNumberService numberService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<SalesOrderDtos.Summary> search(String search, SalesOrderStatus status, Long customerId,
                                                       LocalDate from, LocalDate to, Pageable pageable) {
        Specification<SalesOrder> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("orderNumber")), like),
                        cb.like(cb.lower(root.get("customer").get("name")), like)));
            }
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (customerId != null) {
                p.add(cb.equal(root.get("customer").get("id"), customerId));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("orderDate"), from));
            }
            if (to != null) {
                p.add(cb.lessThanOrEqualTo(root.get("orderDate"), to));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(orderRepository.findAll(spec, pageable), SalesMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public SalesOrderDtos.Detail get(Long id) {
        SalesOrder so = orderRepository.findDetailedById(id).orElseThrow(() -> NotFoundException.of("Sales order", id));
        return toDetail(so);
    }

    @Transactional
    public SalesOrderDtos.Detail create(SalesOrderDtos.Request request) {
        Customer customer = customerService.find(request.customerId());
        if (!customer.isActive()) {
            throw new ConflictException("CUSTOMER_INACTIVE", "Customer " + customer.getName() + " is inactive");
        }
        SalesOrder so = new SalesOrder();
        so.setOrderNumber(numberService.next(DocumentNumberService.DocumentType.SALES_ORDER));
        so.setCustomer(customer);
        so.setWarehouse(inventoryService.activeWarehouse(request.warehouseId()));
        so.setOrderDate(LocalDate.now(ZoneOffset.UTC));
        so.setShippingAddress(shippingAddress(request.shippingAddress(), customer));
        so.setNotes(CategoryService.blankToNull(request.notes()));
        so.setCreatedBy(SecurityUtils.currentUser().map(u -> userRepository.getReferenceById(u.id())).orElse(null));
        applyLines(so, request.items());
        orderRepository.save(so);
        auditService.record("SO_CREATED", "SalesOrder", so.getId(), Map.of("orderNumber", so.getOrderNumber(),
                "customer", customer.getCode(), "total", so.getTotalAmount()));
        if (request.confirm()) {
            confirm(so);
        }
        return toDetail(so);
    }

    @Transactional
    public SalesOrderDtos.Detail update(Long id, SalesOrderDtos.Request request) {
        SalesOrder so = lock(id);
        if (so.getStatus() != SalesOrderStatus.DRAFT) {
            throw new ConflictException("SO_NOT_EDITABLE", "Only draft sales orders can be edited");
        }
        if (!so.getCustomer().getId().equals(request.customerId())) {
            so.setCustomer(customerService.find(request.customerId()));
        }
        if (!so.getWarehouse().getId().equals(request.warehouseId())) {
            so.setWarehouse(inventoryService.activeWarehouse(request.warehouseId()));
        }
        so.setShippingAddress(shippingAddress(request.shippingAddress(), so.getCustomer()));
        so.setNotes(CategoryService.blankToNull(request.notes()));
        applyLines(so, request.items());
        auditService.record("SO_UPDATED", "SalesOrder", id, Map.of("total", so.getTotalAmount()));
        if (request.confirm()) {
            confirm(so);
        }
        return toDetail(so);
    }

    @Transactional
    public SalesOrderDtos.Detail confirm(Long id) {
        SalesOrder so = lock(id);
        confirm(so);
        return toDetail(so);
    }

    @Transactional
    public SalesOrderDtos.Detail ship(Long id, SalesOrderDtos.ShipRequest request) {
        SalesOrder so = lock(id);
        transition(so, SalesOrderStatus.SHIPPED);
        for (StockReservation r : reservationRepository.findByOrderAndStatus(id, ReservationStatus.ACTIVE)) {
            inventoryService.consumeReservation(r.getProduct(), r.getWarehouse(), r.getQuantity(), so.getId());
            r.setStatus(ReservationStatus.CONSUMED);
        }
        Shipment shipment = new Shipment();
        shipment.setShipmentNumber(numberService.next(DocumentNumberService.DocumentType.SHIPMENT));
        shipment.setSalesOrder(so);
        shipment.setCarrier(request.carrier().trim());
        shipment.setTrackingNumber(CategoryService.blankToNull(request.trackingNumber()));
        shipment.setCreatedBy(SecurityUtils.currentUser().map(u -> userRepository.getReferenceById(u.id())).orElse(null));
        shipmentRepository.save(shipment);
        so.setShippedAt(Instant.now());

        if (so.getCreatedBy() != null) {
            notificationService.notifyUsers(List.of(so.getCreatedBy()), NotificationType.SALES_ORDER_SHIPPED,
                    NotificationSeverity.INFO, "Order shipped: " + so.getOrderNumber(),
                    so.getOrderNumber() + " for " + so.getCustomer().getName() + " shipped via " + shipment.getCarrier() + ".",
                    "/sales-orders/" + so.getId());
        }
        auditService.record("SO_SHIPPED", "SalesOrder", id, Map.of("orderNumber", so.getOrderNumber(),
                "shipment", shipment.getShipmentNumber(), "carrier", shipment.getCarrier()));
        return toDetail(so, shipment);
    }

    @Transactional
    public SalesOrderDtos.Detail cancel(Long id) {
        SalesOrder so = lock(id);
        SalesOrderStatus previous = so.getStatus();
        transition(so, SalesOrderStatus.CANCELLED);
        int released = 0;
        for (StockReservation r : reservationRepository.findByOrderAndStatus(id, ReservationStatus.ACTIVE)) {
            inventoryService.release(r.getProduct(), r.getWarehouse(), r.getQuantity());
            r.setStatus(ReservationStatus.RELEASED);
            released += r.getQuantity();
        }
        so.setCancelledAt(Instant.now());
        auditService.record("SO_CANCELLED", "SalesOrder", id, Map.of("orderNumber", so.getOrderNumber(),
                "previousStatus", previous, "unitsReleased", released));
        return toDetail(so);
    }

    // ------------------------------------------------------------------ internals

    private void confirm(SalesOrder so) {
        transition(so, SalesOrderStatus.CONFIRMED);
        List<SalesOrderItem> items = so.getItems().stream()
                .sorted(Comparator.comparing(i -> i.getProduct().getId()))
                .toList();
        for (SalesOrderItem item : items) {
            inventoryService.reserve(item.getProduct(), so.getWarehouse(), item.getQuantity());
            StockReservation reservation = new StockReservation();
            reservation.setSalesOrderItem(item);
            reservation.setProduct(item.getProduct());
            reservation.setWarehouse(so.getWarehouse());
            reservation.setQuantity(item.getQuantity());
            reservationRepository.save(reservation);
        }
        so.setConfirmedAt(Instant.now());
        auditService.record("SO_CONFIRMED", "SalesOrder", so.getId(), Map.of("orderNumber", so.getOrderNumber(),
                "total", so.getTotalAmount()));
    }

    /** In-place line update (see PurchaseOrderService#applyLines for why). */
    private void applyLines(SalesOrder so, List<SalesOrderDtos.Line> lines) {
        Set<Long> productIds = new HashSet<>();
        for (var line : lines) {
            if (!productIds.add(line.productId())) {
                throw new BadRequestException("DUPLICATE_LINE", "Each product can appear only once per order");
            }
        }
        Map<Long, SalesOrderItem> existing = new HashMap<>();
        so.getItems().forEach(i -> existing.put(i.getProduct().getId(), i));
        so.getItems().removeIf(i -> !productIds.contains(i.getProduct().getId()));

        for (var line : lines) {
            SalesOrderItem item = existing.get(line.productId());
            if (item == null) {
                Product product = inventoryService.activeProduct(line.productId());
                item = new SalesOrderItem();
                item.setProduct(product);
                so.addItem(item);
            }
            item.setUnitPrice(item.getProduct().getUnitPrice());
            item.setQuantity(line.quantity());
            item.setLineTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }
        so.recalculateTotal();
    }

    private static void transition(SalesOrder so, SalesOrderStatus target) {
        if (!so.getStatus().canTransitionTo(target)) {
            throw new InvalidStatusTransitionException("Sales order", so.getStatus(), target);
        }
        so.setStatus(target);
    }

    private SalesOrder lock(Long id) {
        return orderRepository.findForUpdate(id).orElseThrow(() -> NotFoundException.of("Sales order", id));
    }

    private static String shippingAddress(String requested, Customer customer) {
        String explicit = CategoryService.blankToNull(requested);
        if (explicit != null) {
            return explicit;
        }
        List<String> parts = new ArrayList<>();
        for (String part : new String[]{customer.getAddress(), customer.getCity(), customer.getCountry()}) {
            if (part != null && !part.isBlank()) {
                parts.add(part);
            }
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private SalesOrderDtos.Detail toDetail(SalesOrder so) {
        Shipment shipment = so.getId() == null ? null : shipmentRepository.findBySalesOrderId(so.getId()).orElse(null);
        return toDetail(so, shipment);
    }

    private SalesOrderDtos.Detail toDetail(SalesOrder so, Shipment shipment) {
        Map<Long, Integer> available = new LinkedHashMap<>();
        for (SalesOrderItem item : so.getItems()) {
            int qty = balanceRepository.findByProductIdAndWarehouseId(item.getProduct().getId(), so.getWarehouse().getId())
                    .map(InventoryBalance::available).orElse(0);
            available.put(item.getProduct().getId(), qty);
        }
        return SalesMapper.toDetail(so, shipment, available);
    }
}
