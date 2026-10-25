package com.stockpilot.integration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Permissions are enforced by the backend, independent of what the UI shows.
 */
class RoleAccessIntegrationTest extends IntegrationTestBase {

    @Test
    void everyRoleCanReadOperationalData() throws Exception {
        for (String token : new String[]{admin, inventory, sales, viewer}) {
            doGet("/api/v1/products", token).andExpect(status().isOk());
            doGet("/api/v1/inventory", token).andExpect(status().isOk());
            doGet("/api/v1/sales-orders", token).andExpect(status().isOk());
            doGet("/api/v1/dashboard", token).andExpect(status().isOk());
            doGet("/api/v1/reports/inventory", token).andExpect(status().isOk());
        }
    }

    @Test
    void viewerCannotModifyAnything() throws Exception {
        doPost("/api/v1/categories", viewer, Map.of("name", "Nope")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        doPost("/api/v1/inventory/receipts", viewer, Map.of("productId", 1, "warehouseId", 1, "quantity", 1))
                .andExpect(status().isForbidden());
        doPost("/api/v1/customers", viewer, Map.of("name", "Nope")).andExpect(status().isForbidden());
    }

    @Test
    void salesManagerCannotChangeStockOrPurchasing() throws Exception {
        doPost("/api/v1/inventory/adjustments", sales,
                Map.of("productId", 1, "warehouseId", 1, "quantityChange", 5, "reason", "x"))
                .andExpect(status().isForbidden());
        doPost("/api/v1/suppliers", sales, Map.of("name", "Supplier")).andExpect(status().isForbidden());
        doPost("/api/v1/customers", sales, Map.of("name", "Allowed")).andExpect(status().isCreated());
    }

    @Test
    void inventoryManagerCannotManageSales() throws Exception {
        doPost("/api/v1/customers", inventory, Map.of("name", "Nope")).andExpect(status().isForbidden());
        doPost("/api/v1/categories", inventory, Map.of("name", "Allowed")).andExpect(status().isCreated());
    }

    @Test
    void onlyAdminManagesUsersAndReadsAuditLogs() throws Exception {
        for (String token : new String[]{inventory, sales, viewer}) {
            doGet("/api/v1/users", token).andExpect(status().isForbidden());
            doGet("/api/v1/audit-logs", token).andExpect(status().isForbidden());
        }
        doGet("/api/v1/users", admin).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4));
        doGet("/api/v1/audit-logs", admin).andExpect(status().isOk());
    }

    @Test
    void adminCanChangeRolesButNotLockThemselvesOut() throws Exception {
        long viewerId = userRepository.findByEmailIgnoreCase("viewer@test.local").orElseThrow().getId();
        doPut("/api/v1/users/" + viewerId, admin, Map.of("fullName", "Promoted", "role", "SALES_MANAGER", "enabled", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SALES_MANAGER"));
        // The change applies immediately to the existing token because the user is reloaded on each request.
        doPost("/api/v1/customers", viewer, Map.of("name", "Now allowed")).andExpect(status().isCreated());

        long adminId = userRepository.findByEmailIgnoreCase("admin@test.local").orElseThrow().getId();
        doPut("/api/v1/users/" + adminId, admin, Map.of("fullName", "Admin", "role", "VIEWER", "enabled", true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_LOCKOUT"));
    }
}
