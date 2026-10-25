import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { inventoryApi, warehousesApi } from '../../api/endpoints';
import type { Balance } from '../../api/types';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { FilterSelect, SearchBox } from '../../components/ui/Field';
import { FilterBar, PageHeader } from '../../components/ui/PageHeader';
import { Pagination } from '../../components/ui/Pagination';
import { StatCard } from '../../components/ui/StatCard';
import { StatusBadge } from '../../components/ui/Badge';
import { useApi, useDebounced } from '../../hooks/useApi';
import { useTableState } from '../../hooks/useTableState';
import { formatMoney, formatNumber } from '../../lib/format';
import { Boxes, DollarSign, Warehouse } from 'lucide-react';

function rowStatus(b: Balance) {
  if (b.onHand === 0) return 'OUT_OF_STOCK';
  return b.onHand <= b.reorderThreshold ? 'LOW_STOCK' : 'IN_STOCK';
}

export function InventoryPage() {
  const [params, setParams] = useSearchParams();
  const [search, setSearch] = useState('');
  const debounced = useDebounced(search);
  const warehouseId = params.get('warehouseId') ?? '';
  const status = params.get('status') ?? '';
  const table = useTableState({ key: 'product.name', direction: 'asc' }, 25);

  const warehouses = useApi(() => warehousesApi.list(), []);
  const balances = useApi(() => inventoryApi.balances({ search: debounced, warehouseId, status, page: table.page,
    size: table.size, sort: table.sortParam }), [debounced, warehouseId, status, table.page, table.sortParam]);

  const setFilter = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value); else next.delete(key);
    setParams(next, { replace: true });
    table.setPage(0);
  };

  const selected = warehouses.data?.filter((w) => !warehouseId || String(w.id) === warehouseId) ?? [];
  const units = selected.reduce((s, w) => s + w.totalUnits, 0);
  const value = selected.reduce((s, w) => s + Number(w.stockValue), 0);

  const columns: Column<Balance>[] = [
    { key: 'product', header: 'Product', sortKey: 'product.name', render: (b) => (
      <Link to={`/products/${b.productId}`} className="hover:text-brand-600" onClick={(e) => e.stopPropagation()}>
        <span className="block font-medium">{b.productName}</span><span className="font-mono text-xs text-slate-500">{b.sku}</span>
      </Link>) },
    { key: 'category', header: 'Category', render: (b) => b.categoryName },
    { key: 'warehouse', header: 'Warehouse', sortKey: 'warehouse.code', render: (b) => <span title={b.warehouseName}>{b.warehouseCode}</span> },
    { key: 'onHand', header: 'On hand', sortKey: 'quantityOnHand', align: 'right', render: (b) => formatNumber(b.onHand) },
    { key: 'reserved', header: 'Reserved', sortKey: 'quantityReserved', align: 'right', render: (b) => formatNumber(b.reserved) },
    { key: 'available', header: 'Available', align: 'right', render: (b) => <span className="font-medium">{formatNumber(b.available)}</span> },
    { key: 'value', header: 'Value', align: 'right', render: (b) => formatMoney(b.stockValue) },
    { key: 'status', header: 'Level', render: (b) => <StatusBadge status={rowStatus(b)} /> },
  ];

  return (
    <>
      <PageHeader title="Stock overview" description="Current quantity on hand, reserved for confirmed orders, and available, per warehouse." />
      <div className="mb-6 grid gap-4 sm:grid-cols-3">
        <StatCard label="Units on hand" value={formatNumber(units)} icon={<Boxes className="h-5 w-5" />} />
        <StatCard label="Stock value (cost)" value={formatMoney(value)} icon={<DollarSign className="h-5 w-5" />} tone="green" />
        <StatCard label="Warehouses" value={formatNumber(selected.length)} icon={<Warehouse className="h-5 w-5" />} tone="blue" to="/warehouses" />
      </div>
      <FilterBar>
        <SearchBox value={search} onChange={(v) => { setSearch(v); table.setPage(0); }} placeholder="Search product or SKU" className="sm:w-72" />
        <FilterSelect label="Warehouse" value={warehouseId} onChange={(v) => setFilter('warehouseId', v)}>
          <option value="">All warehouses</option>
          {warehouses.data?.map((w) => <option key={w.id} value={w.id}>{w.code} — {w.name}</option>)}
        </FilterSelect>
        <FilterSelect label="Stock level" value={status} onChange={(v) => setFilter('status', v)}>
          <option value="">Any level</option>
          <option value="IN_STOCK">Above threshold</option>
          <option value="LOW_STOCK">At or below threshold</option>
          <option value="OUT_OF_STOCK">Zero on hand</option>
        </FilterSelect>
      </FilterBar>
      <DataTable columns={columns} rows={balances.data?.content} rowKey={(b) => b.id} loading={balances.loading}
                 error={balances.error} onRetry={balances.reload} sort={table.sort} onSort={table.setSort}
                 emptyTitle="No stock records" emptyMessage="Receive stock or adjust filters."
                 footer={balances.data && <Pagination page={balances.data.page} size={balances.data.size}
                   totalElements={balances.data.totalElements} totalPages={balances.data.totalPages} onPage={table.setPage} />} />
    </>
  );
}
