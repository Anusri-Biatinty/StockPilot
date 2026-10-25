package com.stockpilot.controller;

import com.stockpilot.dto.CustomerDtos;
import com.stockpilot.dto.PageResponse;
import com.stockpilot.security.Permissions;
import com.stockpilot.service.CustomerService;
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

@Tag(name = "Customers")
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    @PreAuthorize(Permissions.AUTHENTICATED)
    public PageResponse<CustomerDtos.Response> list(@RequestParam(required = false) String search,
                                                    @RequestParam(required = false) Boolean active,
                                                    @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return customerService.search(search, active, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permissions.AUTHENTICATED)
    public CustomerDtos.Response get(@PathVariable Long id) {
        return customerService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.SALES_WRITE)
    public CustomerDtos.Response create(@Valid @RequestBody CustomerDtos.Request request) {
        return customerService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permissions.SALES_WRITE)
    public CustomerDtos.Response update(@PathVariable Long id, @Valid @RequestBody CustomerDtos.Request request) {
        return customerService.update(id, request);
    }
}
