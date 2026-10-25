// TypeScript mirrors of the backend DTOs (com.stockpilot.dto).

export type Role = 'ADMIN' | 'INVENTORY_MANAGER' | 'SALES_MANAGER' | 'VIEWER';
export type StockStatus = 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK';
export type ProductStatus = 'ACTIVE' | 'ARCHIVED';
export type MovementType =
  | 'RECEIPT' | 'ISSUE' | 'TRANSFER_IN' | 'TRANSFER_OUT' | 'ADJUSTMENT' | 'PURCHASE_RECEIPT' | 'SALES_SHIPMENT';
export type PurchaseOrderStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'PARTIALLY_RECEIVED' | 'RECEIVED' | 'CANCELLED';
export type SalesOrderStatus = 'DRAFT' | 'CONFIRMED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';
export type ShipmentStatus = 'IN_TRANSIT' | 'OUT_FOR_DELIVERY' | 'DELIVERED';
export type NotificationSeverity = 'INFO' | 'WARNING' | 'CRITICAL';
export type NotificationType =
  | 'LOW_STOCK' | 'OUT_OF_STOCK' | 'PO_PENDING_APPROVAL' | 'PO_APPROVED' | 'PO_REJECTED' | 'PO_RECEIVED'
  | 'SALES_ORDER_SHIPPED' | 'SYSTEM';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiErrorBody {
  status: number;
  code: string;
  message: string;
  fieldErrors?: { field: string; message: string }[];
}

export interface User {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  enabled: boolean;
  lastLoginAt: string | null;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

export interface RoleInfo {
  name: Role;
  description: string;
  permissions: string[];
}

export interface Category {
  id: number;
  name: string;
  description: string | null;
  active: boolean;
  productCount: number;
}

export interface Product {
  id: number;
  sku: string;
  name: string;
  description: string | null;
  categoryId: number;
  categoryName: string;
  unitPrice: number;
  costPrice: number;
  reorderThreshold: number;
  imageUrl: string | null;
  status: ProductStatus;
  quantityOnHand: number;
  quantityReserved: number;
  quantityAvailable: number;
  stockStatus: StockStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProductDetail {
  product: Product;
  stock: { warehouseId: number; warehouseCode: string; warehouseName: string; onHand: number; reserved: number; available: number }[];
  suppliers: { supplierId: number; supplierName: string; supplierSku: string | null; unitCost: number; leadTimeDays: number }[];
  stockValue: number;
}

export interface ProductRequest {
  sku?: string;
  name: string;
  description?: string;
  categoryId: number;
  unitPrice: number;
  costPrice: number;
  reorderThreshold: number;
  imageUrl?: string;
  version?: number;
}

export interface Warehouse {
  id: number;
  code: string;
  name: string;
  address: string | null;
  city: string | null;
  country: string | null;
  active: boolean;
  productCount: number;
  totalUnits: number;
  stockValue: number;
}

export interface Balance {
  id: number;
  productId: number;
  sku: string;
  productName: string;
  categoryName: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  onHand: number;
  reserved: number;
  available: number;
  reorderThreshold: number;
  unitCost: number;
  stockValue: number;
}

export interface Movement {
  id: number;
  productId: number;
  sku: string;
  productName: string;
  warehouseId: number;
  warehouseCode: string;
  type: MovementType;
  quantity: number;
  balanceAfter: number;
  unitCost: number;
  referenceType: string | null;
  referenceId: number | null;
  reason: string | null;
  performedBy: string | null;
  createdAt: string;
}

export interface Transfer {
  id: number;
  transferNumber: string;
  productId: number;
  sku: string;
  productName: string;
  fromWarehouseCode: string;
  toWarehouseCode: string;
  quantity: number;
  notes: string | null;
  createdBy: string | null;
  createdAt: string;
}

export interface StockAlert {
  productId: number;
  sku: string;
  name: string;
  onHand: number;
  reorderThreshold: number;
  status: StockStatus;
}

export interface Supplier {
  id: number;
  code: string;
  name: string;
  contactName: string | null;
  email: string | null;
  phone: string | null;
  address: string | null;
  active: boolean;
  productCount: number;
  openOrders: number;
  totalSpend: number;
}

export interface SupplierProduct {
  id: number;
  productId: number;
  sku: string;
  productName: string;
  supplierSku: string | null;
  unitCost: number;
  leadTimeDays: number;
}

export interface PurchaseOrderSummary {
  id: number;
  poNumber: string;
  supplierId: number;
  supplierName: string;
  warehouseId: number;
  warehouseCode: string;
  status: PurchaseOrderStatus;
  orderDate: string;
  expectedDate: string | null;
  totalAmount: number;
  itemCount: number;
  createdBy: string | null;
  createdAt: string;
}

export interface PurchaseOrderItem {
  id: number;
  productId: number;
  sku: string;
  productName: string;
  quantityOrdered: number;
  quantityReceived: number;
  quantityRemaining: number;
  unitCost: number;
  lineTotal: number;
}

export interface GoodsReceipt {
  id: number;
  receiptNumber: string;
  purchaseOrderId: number;
  poNumber: string;
  supplierName: string;
  warehouseCode: string;
  receivedBy: string | null;
  receivedAt: string;
  notes: string | null;
  items: { productId: number; sku: string; productName: string; quantityReceived: number }[];
  totalUnits: number;
}

export interface PurchaseOrderDetail {
  id: number;
  poNumber: string;
  supplierId: number;
  supplierName: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  status: PurchaseOrderStatus;
  orderDate: string;
  expectedDate: string | null;
  notes: string | null;
  totalAmount: number;
  rejectionReason: string | null;
  createdBy: string | null;
  createdById: number | null;
  submittedAt: string | null;
  approvedBy: string | null;
  approvedAt: string | null;
  cancelledAt: string | null;
  createdAt: string;
  items: PurchaseOrderItem[];
  receipts: GoodsReceipt[];
}

export interface Customer {
  id: number;
  code: string;
  name: string;
  email: string | null;
  phone: string | null;
  address: string | null;
  city: string | null;
  country: string | null;
  active: boolean;
  orderCount: number;
  totalRevenue: number;
}

export interface SalesOrderSummary {
  id: number;
  orderNumber: string;
  customerId: number;
  customerName: string;
  warehouseCode: string;
  status: SalesOrderStatus;
  orderDate: string;
  totalAmount: number;
  itemCount: number;
  createdAt: string;
}

export interface Shipment {
  id: number;
  shipmentNumber: string;
  salesOrderId: number;
  orderNumber: string;
  customerName: string;
  carrier: string;
  trackingNumber: string | null;
  status: ShipmentStatus;
  shippedAt: string;
  deliveredAt: string | null;
}

export interface SalesOrderDetail {
  id: number;
  orderNumber: string;
  customerId: number;
  customerName: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  status: SalesOrderStatus;
  orderDate: string;
  shippingAddress: string | null;
  notes: string | null;
  totalAmount: number;
  createdBy: string | null;
  createdAt: string;
  confirmedAt: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
  cancelledAt: string | null;
  items: {
    id: number; productId: number; sku: string; productName: string; quantity: number;
    unitPrice: number; lineTotal: number; availableInWarehouse: number;
  }[];
  shipment: Shipment | null;
}

export interface AppNotification {
  id: number;
  type: NotificationType;
  severity: NotificationSeverity;
  title: string;
  message: string;
  link: string | null;
  read: boolean;
  createdAt: string;
}

export interface Dashboard {
  from: string;
  to: string;
  kpis: {
    totalProducts: number; totalCategories: number; totalWarehouses: number; inventoryValue: number;
    totalUnits: number; lowStockCount: number; outOfStockCount: number; pendingPurchaseOrders: number;
    pendingSalesOrders: number; salesRevenue: number; purchaseSpend: number; salesOrderCount: number;
  };
  trends: { month: string; sales: number; purchases: number; orders: number }[];
  topProducts: { productId: number; sku: string; name: string; quantitySold: number; revenue: number }[];
  stockByCategory: { categoryId: number; name: string; units: number; value: number }[];
  recentMovements: Movement[];
  alerts: StockAlert[];
}

export interface ReportInfo {
  key: string;
  title: string;
  description: string;
  dateRange: boolean;
  warehouseFilter: boolean;
}

export type ColumnType = 'text' | 'number' | 'money' | 'date' | 'datetime' | 'percent';

export interface ReportResult {
  key: string;
  title: string;
  columns: { key: string; label: string; type: ColumnType }[];
  rows: Record<string, string | number | null>[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AuditLog {
  id: number;
  actorId: number | null;
  actorEmail: string | null;
  action: string;
  entityType: string;
  entityId: string | null;
  details: string | null;
  ipAddress: string | null;
  createdAt: string;
}
