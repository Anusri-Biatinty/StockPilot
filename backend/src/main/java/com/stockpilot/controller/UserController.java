package com.stockpilot.controller;

import com.stockpilot.dto.PageResponse;
import com.stockpilot.dto.UserDtos;
import com.stockpilot.entity.RoleName;
import com.stockpilot.security.Permissions;
import com.stockpilot.service.UserService;
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

import java.util.List;

@Tag(name = "Users & roles")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize(Permissions.ADMIN)
public class UserController {

    private final UserService userService;

    @GetMapping("/users")
    public PageResponse<UserDtos.Response> list(@RequestParam(required = false) String search,
                                                @RequestParam(required = false) RoleName role,
                                                @RequestParam(required = false) Boolean enabled,
                                                @PageableDefault(size = 20, sort = "fullName", direction = Sort.Direction.ASC) Pageable pageable) {
        return userService.search(search, role, enabled, pageable);
    }

    @GetMapping("/users/{id}")
    public UserDtos.Response get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDtos.Response create(@Valid @RequestBody UserDtos.CreateRequest request) {
        return userService.create(request);
    }

    @PutMapping("/users/{id}")
    public UserDtos.Response update(@PathVariable Long id, @Valid @RequestBody UserDtos.UpdateRequest request) {
        return userService.update(id, request);
    }

    @GetMapping("/roles")
    @PreAuthorize(Permissions.AUTHENTICATED)
    public List<UserDtos.RoleResponse> roles() {
        return userService.roles();
    }
}
