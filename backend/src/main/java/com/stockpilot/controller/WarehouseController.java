package com.stockpilot.controller;

import com.stockpilot.dto.WarehouseDtos;
import com.stockpilot.security.Permissions;
import com.stockpilot.service.WarehouseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Warehouses")
@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping
    @PreAuthorize(Permissions.AUTHENTICATED)
    public List<WarehouseDtos.Response> list() {
        return warehouseService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permissions.AUTHENTICATED)
    public WarehouseDtos.Response get(@PathVariable Long id) {
        return warehouseService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.INVENTORY_WRITE)
    public WarehouseDtos.Response create(@Valid @RequestBody WarehouseDtos.Request request) {
        return warehouseService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permissions.INVENTORY_WRITE)
    public WarehouseDtos.Response update(@PathVariable Long id, @Valid @RequestBody WarehouseDtos.Request request) {
        return warehouseService.update(id, request);
    }
}
