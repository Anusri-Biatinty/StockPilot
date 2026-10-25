package com.stockpilot.controller;

import com.stockpilot.dto.GoodsReceiptDtos;
import com.stockpilot.dto.PageResponse;
import com.stockpilot.dto.PurchaseOrderDtos;
import com.stockpilot.entity.PurchaseOrderStatus;
import com.stockpilot.security.Permissions;
import com.stockpilot.service.PurchaseOrderService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Purchase orders & goods receipts")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService service;

    @GetMapping("/purchase-orders")
    @PreAuthorize(Permissions.AUTHENTICATED)
    public PageResponse<PurchaseOrderDtos.Summary> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(search, status, supplierId, from, to, pageable);
    }

    @GetMapping("/purchase-orders/{id}")
    @PreAuthorize(Permissions.AUTHENTICATED)
    public PurchaseOrderDtos.Detail get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/purchase-orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.PROCUREMENT_WRITE)
    public PurchaseOrderDtos.Detail create(@Valid @RequestBody PurchaseOrderDtos.Request request) {
        return service.create(request);
    }

    @PutMapping("/purchase-orders/{id}")
    @PreAuthorize(Permissions.PROCUREMENT_WRITE)
    public PurchaseOrderDtos.Detail update(@PathVariable Long id, @Valid @RequestBody PurchaseOrderDtos.Request request) {
        return service.update(id, request);
    }

    @PostMapping("/purchase-orders/{id}/submit")
    @PreAuthorize(Permissions.PROCUREMENT_WRITE)
    public PurchaseOrderDtos.Detail submit(@PathVariable Long id) {
        return service.submit(id);
    }

    @Operation(summary = "Approve a submitted order (cannot approve your own unless ADMIN)")
    @PostMapping("/purchase-orders/{id}/approve")
    @PreAuthorize(Permissions.PROCUREMENT_APPROVE)
    public PurchaseOrderDtos.Detail approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @Operation(summary = "Return a submitted order to DRAFT with a reason")
    @PostMapping("/purchase-orders/{id}/reject")
    @PreAuthorize(Permissions.PROCUREMENT_APPROVE)
    public PurchaseOrderDtos.Detail reject(@PathVariable Long id, @Valid @RequestBody PurchaseOrderDtos.RejectRequest request) {
        return service.reject(id, request.reason());
    }

    @PostMapping("/purchase-orders/{id}/cancel")
    @PreAuthorize(Permissions.PROCUREMENT_WRITE)
    public PurchaseOrderDtos.Detail cancel(@PathVariable Long id) {
        return service.cancel(id);
    }

    @Operation(summary = "Receive goods (full or partial). Send an Idempotency-Key header to make retries safe.")
    @PostMapping("/purchase-orders/{id}/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.PROCUREMENT_WRITE)
    public GoodsReceiptDtos.Response receive(@PathVariable Long id,
                                             @Valid @RequestBody PurchaseOrderDtos.ReceiveRequest request,
                                             @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return service.receive(id, request, idempotencyKey);
    }

    @GetMapping("/goods-receipts")
    @PreAuthorize(Permissions.AUTHENTICATED)
    public PageResponse<GoodsReceiptDtos.Response> receipts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long purchaseOrderId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.searchReceipts(search, purchaseOrderId, supplierId, from, to, pageable);
    }

    @GetMapping("/goods-receipts/{id}")
    @PreAuthorize(Permissions.AUTHENTICATED)
    public GoodsReceiptDtos.Response receipt(@PathVariable Long id) {
        return service.getReceipt(id);
    }
}
