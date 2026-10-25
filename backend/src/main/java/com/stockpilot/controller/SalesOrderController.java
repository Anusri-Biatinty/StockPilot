package com.stockpilot.controller;

import com.stockpilot.dto.PageResponse;
import com.stockpilot.dto.SalesOrderDtos;
import com.stockpilot.entity.SalesOrderStatus;
import com.stockpilot.entity.ShipmentStatus;
import com.stockpilot.security.Permissions;
import com.stockpilot.service.SalesOrderService;
import com.stockpilot.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Sales orders & shipments")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SalesOrderController {

    private final SalesOrderService salesOrderService;
    private final ShipmentService shipmentService;

    @GetMapping("/sales-orders")
    @PreAuthorize(Permissions.AUTHENTICATED)
    public PageResponse<SalesOrderDtos.Summary> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SalesOrderStatus status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return salesOrderService.search(search, status, customerId, from, to, pageable);
    }

    @GetMapping("/sales-orders/{id}")
    @PreAuthorize(Permissions.AUTHENTICATED)
    public SalesOrderDtos.Detail get(@PathVariable Long id) {
        return salesOrderService.get(id);
    }

    @Operation(summary = "Create a sales order; set confirm=true to reserve stock immediately")
    @PostMapping("/sales-orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.SALES_WRITE)
    public SalesOrderDtos.Detail create(@Valid @RequestBody SalesOrderDtos.Request request) {
        return salesOrderService.create(request);
    }

    @PutMapping("/sales-orders/{id}")
    @PreAuthorize(Permissions.SALES_WRITE)
    public SalesOrderDtos.Detail update(@PathVariable Long id, @Valid @RequestBody SalesOrderDtos.Request request) {
        return salesOrderService.update(id, request);
    }

    @Operation(summary = "Confirm a draft order, reserving stock for every line (all or nothing)")
    @PostMapping("/sales-orders/{id}/confirm")
    @PreAuthorize(Permissions.SALES_WRITE)
    public SalesOrderDtos.Detail confirm(@PathVariable Long id) {
        return salesOrderService.confirm(id);
    }

    @Operation(summary = "Ship a confirmed order: issues reserved stock and creates the shipment")
    @PostMapping("/sales-orders/{id}/ship")
    @PreAuthorize(Permissions.SALES_WRITE)
    public SalesOrderDtos.Detail ship(@PathVariable Long id, @Valid @RequestBody SalesOrderDtos.ShipRequest request) {
        return salesOrderService.ship(id, request);
    }

    @Operation(summary = "Cancel a draft or confirmed order, releasing reserved stock")
    @PostMapping("/sales-orders/{id}/cancel")
    @PreAuthorize(Permissions.SALES_WRITE)
    public SalesOrderDtos.Detail cancel(@PathVariable Long id) {
        return salesOrderService.cancel(id);
    }

    @GetMapping("/shipments")
    @PreAuthorize(Permissions.AUTHENTICATED)
    public PageResponse<SalesOrderDtos.ShipmentResponse> shipments(
            @RequestParam(required = false) ShipmentStatus status,
            @PageableDefault(size = 20, sort = "shippedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return shipmentService.list(status, pageable);
    }

    @PutMapping("/shipments/{id}/status")
    @PreAuthorize(Permissions.SALES_WRITE)
    public SalesOrderDtos.ShipmentResponse updateShipment(@PathVariable Long id,
                                                          @Valid @RequestBody SalesOrderDtos.ShipmentStatusRequest request) {
        return shipmentService.updateStatus(id, request.status());
    }
}
