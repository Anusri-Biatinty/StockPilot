import { api } from './client';
import type {
  AppNotification, AuditLog, AuthResponse, Balance, Category, Customer, Dashboard, GoodsReceipt, Movement,
  Page, Product, ProductDetail, ProductRequest, PurchaseOrderDetail, PurchaseOrderSummary, ReportInfo,
  ReportResult, RoleInfo, SalesOrderDetail, SalesOrderSummary, Shipment, ShipmentStatus, StockAlert, Supplier,
  SupplierProduct, Transfer, User, Warehouse, Role,
} from './types';

type Params = Record<string, string | number | boolean | undefined | null>;

/** Drops empty values so they are not sent as "?search=" etc. */
function clean(params: Params = {}): Params {
  return Object.fromEntries(Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ''));
}

const get = <T>(url: string, params?: Params) => api.get<T>(url, { params: clean(params) }).then((r) => r.data);
const post = <T>(url: string, body?: unknown, headers?: Record<string, string>) =>
  api.post<T>(url, body, { headers }).then((r) => r.data);
const put = <T>(url: string, body?: unknown) => api.put<T>(url, body).then((r) => r.data);
const del = (url: string) => api.delete(url).then(() => undefined);

export const authApi = {
  login: (email: string, password: string) => post<AuthResponse>('/auth/login', { email, password }),
  register: (body: { email: string; password: string; fullName: string }) => post<AuthResponse>('/auth/register', body),
  logout: () => post<void>('/auth/logout'),
  me: () => get<User>('/auth/me'),
  updateProfile: (fullName: string) => put<User>('/auth/me', { fullName }),
  changePassword: (currentPassword: string, newPassword: string) =>
    post<AuthResponse>('/auth/me/password', { currentPassword, newPassword }),
};

export const usersApi = {
  list: (params: Params) => get<Page<User>>('/users', params),
  create: (body: { email: string; password: string; fullName: string; role: Role }) => post<User>('/users', body),
  update: (id: number, body: { fullName: string; role: Role; enabled: boolean }) => put<User>(`/users/${id}`, body),
  roles: () => get<RoleInfo[]>('/roles'),
};

export const categoriesApi = {
  list: () => get<Category[]>('/categories'),
  create: (body: { name: string; description?: string; active?: boolean }) => post<Category>('/categories', body),
  update: (id: number, body: { name: string; description?: string; active?: boolean }) => put<Category>(`/categories/${id}`, body),
  remove: (id: number) => del(`/categories/${id}`),
};

export const productsApi = {
  list: (params: Params) => get<Page<Product>>('/products', params),
  get: (id: number) => get<ProductDetail>(`/products/${id}`),
  movements: (id: number, params: Params) => get<Page<Movement>>(`/products/${id}/movements`, params),
  create: (body: ProductRequest) => post<Product>('/products', body),
  update: (id: number, body: ProductRequest) => put<Product>(`/products/${id}`, body),
  archive: (id: number) => post<Product>(`/products/${id}/archive`),
  restore: (id: number) => post<Product>(`/products/${id}/restore`),
};

export interface WarehouseRequest {
  code: string; name: string; address?: string; city?: string; country?: string; active?: boolean;
}

export const warehousesApi = {
  list: () => get<Warehouse[]>('/warehouses'),
  create: (body: WarehouseRequest) => post<Warehouse>('/warehouses', body),
  update: (id: number, body: WarehouseRequest) => put<Warehouse>(`/warehouses/${id}`, body),
};

export const inventoryApi = {
  balances: (params: Params) => get<Page<Balance>>('/inventory', params),
  movements: (params: Params) => get<Page<Movement>>('/inventory/movements', params),
  alerts: () => get<StockAlert[]>('/inventory/alerts'),
  transfers: (params: Params) => get<Page<Transfer>>('/inventory/transfers', params),
  receive: (body: { productId: number; warehouseId: number; quantity: number; reason?: string }) =>
    post<Movement>('/inventory/receipts', body),
  issue: (body: { productId: number; warehouseId: number; quantity: number; reason: string }) =>
    post<Movement>('/inventory/issues', body),
  transfer: (body: { productId: number; fromWarehouseId: number; toWarehouseId: number; quantity: number; notes?: string }) =>
    post<Transfer>('/inventory/transfers', body),
  adjust: (body: { productId: number; warehouseId: number; quantityChange: number; reason: string }) =>
    post<Movement>('/inventory/adjustments', body),
};

export interface SupplierRequest {
  code?: string; name: string; contactName?: string; email?: string; phone?: string; address?: string; active?: boolean;
}

export const suppliersApi = {
  list: (params: Params) => get<Page<Supplier>>('/suppliers', params),
  get: (id: number) => get<Supplier>(`/suppliers/${id}`),
  create: (body: SupplierRequest) => post<Supplier>('/suppliers', body),
  update: (id: number, body: SupplierRequest) => put<Supplier>(`/suppliers/${id}`, body),
  products: (id: number) => get<SupplierProduct[]>(`/suppliers/${id}/products`),
  saveProduct: (id: number, body: { productId: number; supplierSku?: string; unitCost: number; leadTimeDays: number }) =>
    put<SupplierProduct>(`/suppliers/${id}/products`, body),
  removeProduct: (id: number, productId: number) => del(`/suppliers/${id}/products/${productId}`),
};

export interface OrderLine { productId: number; quantity: number; unitCost?: number }
export interface PurchaseOrderRequest {
  supplierId: number; warehouseId: number; expectedDate?: string; notes?: string; items: OrderLine[];
}

export const purchaseOrdersApi = {
  list: (params: Params) => get<Page<PurchaseOrderSummary>>('/purchase-orders', params),
  get: (id: number) => get<PurchaseOrderDetail>(`/purchase-orders/${id}`),
  create: (body: PurchaseOrderRequest) => post<PurchaseOrderDetail>('/purchase-orders', body),
  update: (id: number, body: PurchaseOrderRequest) => put<PurchaseOrderDetail>(`/purchase-orders/${id}`, body),
  submit: (id: number) => post<PurchaseOrderDetail>(`/purchase-orders/${id}/submit`),
  approve: (id: number) => post<PurchaseOrderDetail>(`/purchase-orders/${id}/approve`),
  reject: (id: number, reason: string) => post<PurchaseOrderDetail>(`/purchase-orders/${id}/reject`, { reason }),
  cancel: (id: number) => post<PurchaseOrderDetail>(`/purchase-orders/${id}/cancel`),
  receive: (id: number, items: { purchaseOrderItemId: number; quantity: number }[], notes: string, idempotencyKey: string) =>
    post<GoodsReceipt>(`/purchase-orders/${id}/receipts`, { items, notes }, { 'Idempotency-Key': idempotencyKey }),
  receipts: (params: Params) => get<Page<GoodsReceipt>>('/goods-receipts', params),
};

export interface CustomerRequest {
  code?: string; name: string; email?: string; phone?: string; address?: string; city?: string; country?: string; active?: boolean;
}

export const customersApi = {
  list: (params: Params) => get<Page<Customer>>('/customers', params),
  get: (id: number) => get<Customer>(`/customers/${id}`),
  create: (body: CustomerRequest) => post<Customer>('/customers', body),
  update: (id: number, body: CustomerRequest) => put<Customer>(`/customers/${id}`, body),
};

export interface SalesOrderRequest {
  customerId: number; warehouseId: number; shippingAddress?: string; notes?: string;
  items: { productId: number; quantity: number }[]; confirm: boolean;
}

export const salesOrdersApi = {
  list: (params: Params) => get<Page<SalesOrderSummary>>('/sales-orders', params),
  get: (id: number) => get<SalesOrderDetail>(`/sales-orders/${id}`),
  create: (body: SalesOrderRequest) => post<SalesOrderDetail>('/sales-orders', body),
  update: (id: number, body: SalesOrderRequest) => put<SalesOrderDetail>(`/sales-orders/${id}`, body),
  confirm: (id: number) => post<SalesOrderDetail>(`/sales-orders/${id}/confirm`),
  ship: (id: number, carrier: string, trackingNumber: string) =>
    post<SalesOrderDetail>(`/sales-orders/${id}/ship`, { carrier, trackingNumber }),
  cancel: (id: number) => post<SalesOrderDetail>(`/sales-orders/${id}/cancel`),
  shipments: (params: Params) => get<Page<Shipment>>('/shipments', params),
  updateShipment: (id: number, status: ShipmentStatus) => put<Shipment>(`/shipments/${id}/status`, { status }),
};

export const notificationsApi = {
  list: (params: Params) => get<Page<AppNotification>>('/notifications', params),
  unreadCount: () => get<{ count: number }>('/notifications/unread-count'),
  markRead: (id: number) => post<AppNotification>(`/notifications/${id}/read`),
  markUnread: (id: number) => post<AppNotification>(`/notifications/${id}/unread`),
  markAllRead: () => post<{ updated: number }>('/notifications/read-all'),
};

export const dashboardApi = {
  get: (params: Params) => get<Dashboard>('/dashboard', params),
};

export const reportsApi = {
  catalogue: () => get<ReportInfo[]>('/reports'),
  run: (key: string, params: Params) => get<ReportResult>(`/reports/${key}`, params),
  /** Downloads through axios so the Authorization header is sent, then saves the blob. */
  exportCsv: async (key: string, params: Params) => {
    const res = await api.get<Blob>(`/reports/${key}/export`, { params: clean(params), responseType: 'blob' });
    const disposition = String(res.headers['content-disposition'] ?? '');
    const name = /filename="?([^"]+)"?/.exec(disposition)?.[1] ?? `stockpilot-${key}.csv`;
    const url = URL.createObjectURL(res.data);
    const link = document.createElement('a');
    link.href = url;
    link.download = name;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  },
};

export const auditApi = {
  list: (params: Params) => get<Page<AuditLog>>('/audit-logs', params),
};
