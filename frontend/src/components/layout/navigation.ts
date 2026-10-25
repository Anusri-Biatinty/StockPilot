import {
  ArrowLeftRight, Bell, Boxes, ClipboardList, FileBarChart, History, LayoutDashboard, Package, PackageCheck,
  ScrollText, ShoppingCart, Tags, Truck, Users, UserCog, Warehouse, Factory,
} from 'lucide-react';
import type { ComponentType } from 'react';
import type { Role } from '../../api/types';

export interface NavItem {
  to: string;
  label: string;
  icon: ComponentType<{ className?: string }>;
  roles?: Role[];
}

export interface NavSection {
  title: string;
  items: NavItem[];
}

export const NAVIGATION: NavSection[] = [
  { title: 'Overview', items: [
    { to: '/', label: 'Dashboard', icon: LayoutDashboard },
    { to: '/notifications', label: 'Notifications', icon: Bell },
  ] },
  { title: 'Catalog', items: [
    { to: '/products', label: 'Products', icon: Package },
    { to: '/categories', label: 'Categories', icon: Tags },
  ] },
  { title: 'Inventory', items: [
    { to: '/inventory', label: 'Stock overview', icon: Boxes },
    { to: '/inventory/movements', label: 'Movement history', icon: History },
    { to: '/inventory/operations', label: 'Transfers & adjustments', icon: ArrowLeftRight },
    { to: '/warehouses', label: 'Warehouses', icon: Warehouse },
  ] },
  { title: 'Purchasing', items: [
    { to: '/suppliers', label: 'Suppliers', icon: Factory },
    { to: '/purchase-orders', label: 'Purchase orders', icon: ClipboardList },
    { to: '/goods-receipts', label: 'Goods receipts', icon: PackageCheck },
  ] },
  { title: 'Sales', items: [
    { to: '/customers', label: 'Customers', icon: Users },
    { to: '/sales-orders', label: 'Sales orders', icon: ShoppingCart },
    { to: '/shipments', label: 'Shipments', icon: Truck },
  ] },
  { title: 'Insights', items: [
    { to: '/reports', label: 'Reports & exports', icon: FileBarChart },
  ] },
  { title: 'Administration', items: [
    { to: '/users', label: 'Users & roles', icon: UserCog, roles: ['ADMIN'] },
    { to: '/audit-logs', label: 'Audit logs', icon: ScrollText, roles: ['ADMIN'] },
  ] },
];
