package com.stockpilot.controller;

import com.stockpilot.dto.PageResponse;
import com.stockpilot.dto.SupplierDtos;
import com.stockpilot.security.Permissions;
import com.stockpilot.service.SupplierService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Suppliers")
@RestController
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    @PreAuthorize(Permissions.AUTHENTICATED)
    public PageResponse<SupplierDtos.Response> list(@RequestParam(required = false) String search,
                                                    @RequestParam(required = false) Boolean active,
                                                    @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return supplierService.search(search, active, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permissions.AUTHENTICATED)
    public SupplierDtos.Response get(@PathVariable Long id) {
        return supplierService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.PROCUREMENT_WRITE)
    public SupplierDtos.Response create(@Valid @RequestBody SupplierDtos.Request request) {
        return supplierService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permissions.PROCUREMENT_WRITE)
    public SupplierDtos.Response update(@PathVariable Long id, @Valid @RequestBody SupplierDtos.Request request) {
        return supplierService.update(id, request);
    }

    @GetMapping("/{id}/products")
    @PreAuthorize(Permissions.AUTHENTICATED)
    public List<SupplierDtos.ProductResponse> products(@PathVariable Long id) {
        return supplierService.products(id);
    }

    @PutMapping("/{id}/products")
    @PreAuthorize(Permissions.PROCUREMENT_WRITE)
    public SupplierDtos.ProductResponse upsertProduct(@PathVariable Long id,
                                                      @Valid @RequestBody SupplierDtos.ProductRequest request) {
        return supplierService.upsertProduct(id, request);
    }

    @DeleteMapping("/{id}/products/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Permissions.PROCUREMENT_WRITE)
    public void removeProduct(@PathVariable Long id, @PathVariable Long productId) {
        supplierService.removeProduct(id, productId);
    }
}
