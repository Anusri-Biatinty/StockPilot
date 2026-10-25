package com.stockpilot.controller;

import com.stockpilot.dto.InventoryDtos;
import com.stockpilot.dto.PageResponse;
import com.stockpilot.dto.ProductDtos;
import com.stockpilot.dto.StockStatus;
import com.stockpilot.entity.ProductStatus;
import com.stockpilot.security.Permissions;
import com.stockpilot.service.InventoryService;
import com.stockpilot.service.ProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

@Tag(name = "Products")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final InventoryService inventoryService;

    @GetMapping
    @PreAuthorize(Permissions.AUTHENTICATED)
    public PageResponse<ProductDtos.Response> list(@RequestParam(required = false) String search,
                                                   @RequestParam(required = false) Long categoryId,
                                                   @RequestParam(required = false) ProductStatus status,
                                                   @RequestParam(required = false) StockStatus stockStatus,
                                                   @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return productService.search(search, categoryId, status, stockStatus, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permissions.AUTHENTICATED)
    public ProductDtos.Detail get(@PathVariable Long id) {
        return productService.get(id);
    }

    @GetMapping("/{id}/movements")
    @PreAuthorize(Permissions.AUTHENTICATED)
    public PageResponse<InventoryDtos.MovementResponse> movements(@PathVariable Long id,
                                                                  @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return inventoryService.searchMovements(id, null, null, null, null, null, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.INVENTORY_WRITE)
    public ProductDtos.Response create(@Valid @RequestBody ProductDtos.Request request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permissions.INVENTORY_WRITE)
    public ProductDtos.Response update(@PathVariable Long id, @Valid @RequestBody ProductDtos.Request request) {
        return productService.update(id, request);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize(Permissions.INVENTORY_WRITE)
    public ProductDtos.Response archive(@PathVariable Long id) {
        return productService.setStatus(id, ProductStatus.ARCHIVED);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize(Permissions.INVENTORY_WRITE)
    public ProductDtos.Response restore(@PathVariable Long id) {
        return productService.setStatus(id, ProductStatus.ACTIVE);
    }
}
