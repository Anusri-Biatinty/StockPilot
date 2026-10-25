package com.stockpilot.service;

import com.stockpilot.dto.PageResponse;
import com.stockpilot.dto.SalesOrderDtos;
import com.stockpilot.entity.SalesOrder;
import com.stockpilot.entity.SalesOrderStatus;
import com.stockpilot.entity.Shipment;
import com.stockpilot.entity.ShipmentStatus;
import com.stockpilot.exception.InvalidStatusTransitionException;
import com.stockpilot.exception.NotFoundException;
import com.stockpilot.mapper.SalesMapper;
import com.stockpilot.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;

/**
 * Shipment tracking moves forward only (IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED). Delivery also
 * completes the sales order.
 */
@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<SalesOrderDtos.ShipmentResponse> list(ShipmentStatus status, Pageable pageable) {
        var statuses = status == null ? EnumSet.allOf(ShipmentStatus.class) : EnumSet.of(status);
        return PageResponse.of(shipmentRepository.findByStatusIn(statuses, pageable), SalesMapper::toResponse);
    }

    @Transactional
    public SalesOrderDtos.ShipmentResponse updateStatus(Long id, ShipmentStatus target) {
        Shipment shipment = shipmentRepository.findById(id).orElseThrow(() -> NotFoundException.of("Shipment", id));
        if (!shipment.getStatus().canTransitionTo(target)) {
            throw new InvalidStatusTransitionException("Shipment", shipment.getStatus(), target);
        }
        ShipmentStatus previous = shipment.getStatus();
        shipment.setStatus(target);
        SalesOrder so = shipment.getSalesOrder();
        if (target == ShipmentStatus.DELIVERED) {
            Instant now = Instant.now();
            shipment.setDeliveredAt(now);
            if (!so.getStatus().canTransitionTo(SalesOrderStatus.DELIVERED)) {
                throw new InvalidStatusTransitionException("Sales order", so.getStatus(), SalesOrderStatus.DELIVERED);
            }
            so.setStatus(SalesOrderStatus.DELIVERED);
            so.setDeliveredAt(now);
            auditService.record("SO_DELIVERED", "SalesOrder", so.getId(), Map.of("orderNumber", so.getOrderNumber()));
        }
        auditService.record("SHIPMENT_STATUS_CHANGED", "Shipment", id, Map.of("shipment", shipment.getShipmentNumber(),
                "status", previous + " -> " + target));
        return SalesMapper.toResponse(shipment);
    }
}
