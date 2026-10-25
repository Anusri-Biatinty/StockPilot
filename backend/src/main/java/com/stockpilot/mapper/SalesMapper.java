package com.stockpilot.mapper;

import com.stockpilot.dto.CustomerDtos;
import com.stockpilot.dto.SalesOrderDtos;
import com.stockpilot.entity.Customer;
import com.stockpilot.entity.SalesOrder;
import com.stockpilot.entity.Shipment;

import java.math.BigDecimal;
import java.util.Map;

public final class SalesMapper {

    private SalesMapper() {
    }

    public static CustomerDtos.Response toResponse(Customer c, long orderCount, BigDecimal revenue) {
        return new CustomerDtos.Response(c.getId(), c.getCode(), c.getName(), c.getEmail(), c.getPhone(),
                c.getAddress(), c.getCity(), c.getCountry(), c.isActive(), orderCount, revenue);
    }

    public static SalesOrderDtos.Summary toSummary(SalesOrder so) {
        return new SalesOrderDtos.Summary(so.getId(), so.getOrderNumber(), so.getCustomer().getId(),
                so.getCustomer().getName(), so.getWarehouse().getCode(), so.getStatus(), so.getOrderDate(),
                so.getTotalAmount(), so.getItems().size(), so.getCreatedAt());
    }

    /** @param availableByProduct live available quantity per product id in the order's warehouse */
    public static SalesOrderDtos.Detail toDetail(SalesOrder so, Shipment shipment, Map<Long, Integer> availableByProduct) {
        var items = so.getItems().stream()
                .map(i -> new SalesOrderDtos.Item(i.getId(), i.getProduct().getId(), i.getProduct().getSku(),
                        i.getProduct().getName(), i.getQuantity(), i.getUnitPrice(), i.getLineTotal(),
                        availableByProduct.getOrDefault(i.getProduct().getId(), 0)))
                .toList();
        return new SalesOrderDtos.Detail(so.getId(), so.getOrderNumber(), so.getCustomer().getId(),
                so.getCustomer().getName(), so.getWarehouse().getId(), so.getWarehouse().getCode(),
                so.getWarehouse().getName(), so.getStatus(), so.getOrderDate(), so.getShippingAddress(), so.getNotes(),
                so.getTotalAmount(), UserMapper.displayName(so.getCreatedBy()), so.getCreatedAt(), so.getConfirmedAt(),
                so.getShippedAt(), so.getDeliveredAt(), so.getCancelledAt(), items,
                shipment == null ? null : toResponse(shipment));
    }

    public static SalesOrderDtos.ShipmentResponse toResponse(Shipment s) {
        SalesOrder so = s.getSalesOrder();
        return new SalesOrderDtos.ShipmentResponse(s.getId(), s.getShipmentNumber(), so.getId(), so.getOrderNumber(),
                so.getCustomer().getName(), s.getCarrier(), s.getTrackingNumber(), s.getStatus(), s.getShippedAt(),
                s.getDeliveredAt());
    }
}
