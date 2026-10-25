import { lazy } from 'react';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { AppLayout } from './components/layout/AppLayout';
import { ToastProvider } from './components/ui/Toast';
import { NotFoundPage, UnauthorizedPage } from './pages/ErrorPages';
import { LoginPage } from './pages/auth/LoginPage';
import { RegisterPage } from './pages/auth/RegisterPage';

// Pages are code-split per route; each loads on first visit.
const DashboardPage = lazy(() => import('./pages/DashboardPage').then((m) => ({ default: m.DashboardPage })));
const NotificationsPage = lazy(() => import('./pages/NotificationsPage').then((m) => ({ default: m.NotificationsPage })));
const ProfilePage = lazy(() => import('./pages/ProfilePage').then((m) => ({ default: m.ProfilePage })));
const ReportsPage = lazy(() => import('./pages/ReportsPage').then((m) => ({ default: m.ReportsPage })));
const AuditLogsPage = lazy(() => import('./pages/admin/AuditLogsPage').then((m) => ({ default: m.AuditLogsPage })));
const UsersPage = lazy(() => import('./pages/admin/UsersPage').then((m) => ({ default: m.UsersPage })));
const CategoriesPage = lazy(() => import('./pages/catalog/CategoriesPage').then((m) => ({ default: m.CategoriesPage })));
const ProductDetailPage = lazy(() => import('./pages/catalog/ProductDetailPage').then((m) => ({ default: m.ProductDetailPage })));
const ProductsPage = lazy(() => import('./pages/catalog/ProductsPage').then((m) => ({ default: m.ProductsPage })));
const InventoryPage = lazy(() => import('./pages/inventory/InventoryPage').then((m) => ({ default: m.InventoryPage })));
const MovementsPage = lazy(() => import('./pages/inventory/MovementsPage').then((m) => ({ default: m.MovementsPage })));
const StockOperationsPage = lazy(() => import('./pages/inventory/StockOperationsPage').then((m) => ({ default: m.StockOperationsPage })));
const WarehousesPage = lazy(() => import('./pages/inventory/WarehousesPage').then((m) => ({ default: m.WarehousesPage })));
const GoodsReceiptsPage = lazy(() => import('./pages/purchasing/GoodsReceiptsPage').then((m) => ({ default: m.GoodsReceiptsPage })));
const PurchaseOrderDetailPage = lazy(() => import('./pages/purchasing/PurchaseOrderDetailPage').then((m) => ({ default: m.PurchaseOrderDetailPage })));
const PurchaseOrderFormPage = lazy(() => import('./pages/purchasing/PurchaseOrderFormPage').then((m) => ({ default: m.PurchaseOrderFormPage })));
const PurchaseOrdersPage = lazy(() => import('./pages/purchasing/PurchaseOrdersPage').then((m) => ({ default: m.PurchaseOrdersPage })));
const SupplierDetailPage = lazy(() => import('./pages/purchasing/SupplierDetailPage').then((m) => ({ default: m.SupplierDetailPage })));
const SuppliersPage = lazy(() => import('./pages/purchasing/SuppliersPage').then((m) => ({ default: m.SuppliersPage })));
const CustomersPage = lazy(() => import('./pages/sales/CustomersPage').then((m) => ({ default: m.CustomersPage })));
const SalesOrderDetailPage = lazy(() => import('./pages/sales/SalesOrderDetailPage').then((m) => ({ default: m.SalesOrderDetailPage })));
const SalesOrderFormPage = lazy(() => import('./pages/sales/SalesOrderFormPage').then((m) => ({ default: m.SalesOrderFormPage })));
const SalesOrdersPage = lazy(() => import('./pages/sales/SalesOrdersPage').then((m) => ({ default: m.SalesOrdersPage })));
const ShipmentsPage = lazy(() => import('./pages/sales/ShipmentsPage').then((m) => ({ default: m.ShipmentsPage })));

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route element={<ProtectedRoute><AppLayout /></ProtectedRoute>}>
        <Route index element={<DashboardPage />} />
        <Route path="products" element={<ProductsPage />} />
        <Route path="products/:id" element={<ProductDetailPage />} />
        <Route path="categories" element={<CategoriesPage />} />
        <Route path="warehouses" element={<WarehousesPage />} />
        <Route path="inventory" element={<InventoryPage />} />
        <Route path="inventory/movements" element={<MovementsPage />} />
        <Route path="inventory/operations" element={<StockOperationsPage />} />
        <Route path="suppliers" element={<SuppliersPage />} />
        <Route path="suppliers/:id" element={<SupplierDetailPage />} />
        <Route path="purchase-orders" element={<PurchaseOrdersPage />} />
        <Route path="purchase-orders/new" element={<ProtectedRoute roles={['ADMIN', 'INVENTORY_MANAGER']}><PurchaseOrderFormPage /></ProtectedRoute>} />
        <Route path="purchase-orders/:id" element={<PurchaseOrderDetailPage />} />
        <Route path="purchase-orders/:id/edit" element={<ProtectedRoute roles={['ADMIN', 'INVENTORY_MANAGER']}><PurchaseOrderFormPage /></ProtectedRoute>} />
        <Route path="goods-receipts" element={<GoodsReceiptsPage />} />
        <Route path="customers" element={<CustomersPage />} />
        <Route path="sales-orders" element={<SalesOrdersPage />} />
        <Route path="sales-orders/new" element={<ProtectedRoute roles={['ADMIN', 'SALES_MANAGER']}><SalesOrderFormPage /></ProtectedRoute>} />
        <Route path="sales-orders/:id" element={<SalesOrderDetailPage />} />
        <Route path="sales-orders/:id/edit" element={<ProtectedRoute roles={['ADMIN', 'SALES_MANAGER']}><SalesOrderFormPage /></ProtectedRoute>} />
        <Route path="shipments" element={<ShipmentsPage />} />
        <Route path="notifications" element={<NotificationsPage />} />
        <Route path="reports" element={<ReportsPage />} />
        <Route path="audit-logs" element={<ProtectedRoute roles={['ADMIN']}><AuditLogsPage /></ProtectedRoute>} />
        <Route path="users" element={<ProtectedRoute roles={['ADMIN']}><UsersPage /></ProtectedRoute>} />
        <Route path="profile" element={<ProfilePage />} />
        <Route path="unauthorized" element={<UnauthorizedPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <AppRoutes />
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  );
}
