package com.stockpilot.security;

/**
 * Single source of truth for role-based access rules, used in {@code @PreAuthorize}.
 *
 * <pre>
 * Capability                         ADMIN  INVENTORY_MGR  SALES_MGR  VIEWER
 * Read catalog, stock, orders, reports  x        x             x         x
 * Manage products/categories/warehouses x        x
 * Stock receipts/issues/transfers/adj.  x        x
 * Suppliers & purchase orders (write)   x        x
 * Approve purchase orders               x        x (not own POs)
 * Customers, sales orders, shipments    x                      x
 * Users & roles, audit logs             x
 * </pre>
 */
public final class Permissions {

    public static final String AUTHENTICATED = "isAuthenticated()";
    public static final String ADMIN = "hasRole('ADMIN')";
    public static final String INVENTORY_WRITE = "hasAnyRole('ADMIN','INVENTORY_MANAGER')";
    public static final String PROCUREMENT_WRITE = "hasAnyRole('ADMIN','INVENTORY_MANAGER')";
    public static final String PROCUREMENT_APPROVE = "hasAnyRole('ADMIN','INVENTORY_MANAGER')";
    public static final String SALES_WRITE = "hasAnyRole('ADMIN','SALES_MANAGER')";

    private Permissions() {
    }
}
