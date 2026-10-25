package com.stockpilot.controller;

import com.stockpilot.dto.CategoryDtos;
import com.stockpilot.security.Permissions;
import com.stockpilot.service.CategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Categories")
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @PreAuthorize(Permissions.AUTHENTICATED)
    public List<CategoryDtos.Response> list() {
        return categoryService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.INVENTORY_WRITE)
    public CategoryDtos.Response create(@Valid @RequestBody CategoryDtos.Request request) {
        return categoryService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permissions.INVENTORY_WRITE)
    public CategoryDtos.Response update(@PathVariable Long id, @Valid @RequestBody CategoryDtos.Request request) {
        return categoryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Permissions.INVENTORY_WRITE)
    public void delete(@PathVariable Long id) {
        categoryService.delete(id);
    }
}
