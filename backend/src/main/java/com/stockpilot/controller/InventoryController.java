package com.stockpilot.controller;

import com.stockpilot.dto.InventoryDtos;
import com.stockpilot.dto.PageResponse;
import com.stockpilot.dto.StockStatus;
import com.stockpilot.entity.MovementType;
import com.stockpilot.security.Permissions;
import com.stockpilot.service.InventoryService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Inventory")
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @Operation(summary = "Stock balances per product and warehouse")
    @GetMapping
    @PreAuthorize(Permissions.AUTHENTICATED)
    public PageResponse<InventoryDtos.BalanceResponse> balances(@RequestParam(required = false) String search,
                                                                @RequestParam(required = false) Long warehouseId,
                                                                @RequestParam(required = false) Long productId,
                                                                @RequestParam(required = false) StockStatus status,
                                                                @PageableDefault(size = 20, sort = "product.name", direction = Sort.Direction.ASC) Pageable pageable) {
        return inventoryService.searchBalances(search, warehouseId, productId, status, pageable);
    }

    @GetMapping("/movements")
    @PreAuthorize(Permissions.AUTHENTICATED)
    public PageResponse<InventoryDtos.MovementResponse> movements(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) MovementType type,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return inventoryService.searchMovements(productId, warehouseId, type, search, from, to, pageable);
    }

    @Operation(summary = "Products at or below their reorder threshold")
    @GetMapping("/alerts")
    @PreAuthorize(Permissions.AUTHENTICATED)
    public List<InventoryDtos.StockAlert> alerts() {
        return inventoryService.alerts();
    }

    @GetMapping("/transfers")
    @PreAuthorize(Permissions.AUTHENTICATED)
    public PageResponse<InventoryDtos.TransferResponse> transfers(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return inventoryService.transfers(pageable);
    }

    @Operation(summary = "Manual stock receipt (not linked to a purchase order)")
    @PostMapping("/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.INVENTORY_WRITE)
    public InventoryDtos.MovementResponse receive(@Valid @RequestBody InventoryDtos.ReceiptRequest request) {
        return inventoryService.receive(request);
    }

    @Operation(summary = "Manual stock issue; a reason is required")
    @PostMapping("/issues")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.INVENTORY_WRITE)
    public InventoryDtos.MovementResponse issue(@Valid @RequestBody InventoryDtos.IssueRequest request) {
        return inventoryService.issue(request);
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.INVENTORY_WRITE)
    public InventoryDtos.TransferResponse transfer(@Valid @RequestBody InventoryDtos.TransferRequest request) {
        return inventoryService.transfer(request);
    }

    @Operation(summary = "Stock adjustment (signed quantity); a reason is required")
    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.INVENTORY_WRITE)
    public InventoryDtos.MovementResponse adjust(@Valid @RequestBody InventoryDtos.AdjustmentRequest request) {
        return inventoryService.adjust(request);
    }
}
