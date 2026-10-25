import type { Role } from '../api/types';

/**
 * Mirrors com.stockpilot.security.Permissions. The UI uses this only to hide actions a user cannot perform;
 * the backend enforces every rule independently.
 */
const has = (role: Role | undefined, ...allowed: Role[]) => !!role && allowed.includes(role);

export const can = {
  manageInventory: (r?: Role) => has(r, 'ADMIN', 'INVENTORY_MANAGER'),
  manageProcurement: (r?: Role) => has(r, 'ADMIN', 'INVENTORY_MANAGER'),
  approvePurchases: (r?: Role) => has(r, 'ADMIN', 'INVENTORY_MANAGER'),
  manageSales: (r?: Role) => has(r, 'ADMIN', 'SALES_MANAGER'),
  administer: (r?: Role) => has(r, 'ADMIN'),
};

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Admin',
  INVENTORY_MANAGER: 'Inventory manager',
  SALES_MANAGER: 'Sales manager',
  VIEWER: 'Viewer',
};
