import { Plus } from 'lucide-react';
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { categoriesApi, productsApi } from '../../api/endpoints';
import { useAuth } from '../../auth/AuthContext';
import { can } from '../../auth/permissions';
import { StatusBadge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { FilterSelect, SearchBox } from '../../components/ui/Field';
import { FilterBar, PageHeader } from '../../components/ui/PageHeader';
import { Pagination } from '../../components/ui/Pagination';
import { useApi, useDebounced } from '../../hooks/useApi';
import { useTableState } from '../../hooks/useTableState';
import { formatMoney, formatNumber } from '../../lib/format';
import type { Product } from '../../api/types';
import { ProductFormModal } from './ProductFormModal';

export function ProductsPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const [search, setSearch] = useState(params.get('search') ?? '');
  const debounced = useDebounced(search);
  const categoryId = params.get('categoryId') ?? '';
  const status = params.get('status') ?? 'ACTIVE';
  const stockStatus = params.get('stockStatus') ?? '';
  const table = useTableState({ key: 'name', direction: 'asc' });
  const [creating, setCreating] = useState(false);

  const categories = useApi(() => categoriesApi.list(), []);
  const products = useApi(
    () => productsApi.list({ search: debounced, categoryId, status: status === 'ALL' ? undefined : status, stockStatus,
      page: table.page, size: table.size, sort: table.sortParam }),
    [debounced, categoryId, status, stockStatus, table.page, table.sortParam],
  );

  const setFilter = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value); else next.delete(key);
    setParams(next, { replace: true });
    table.setPage(0);
  };

  const columns: Column<Product>[] = [
    { key: 'sku', header: 'SKU', sortKey: 'sku', render: (p) => <span className="font-mono text-xs font-medium">{p.sku}</span> },
    { key: 'name', header: 'Product', sortKey: 'name', render: (p) => (
      <div className="flex items-center gap-3">
        {p.imageUrl
          ? <img src={p.imageUrl} alt="" className="h-9 w-9 rounded-md object-cover" loading="lazy" referrerPolicy="no-referrer" />
          : <div className="h-9 w-9 rounded-md bg-slate-100 dark:bg-slate-800" aria-hidden="true" />}
        <div className="min-w-0"><p className="max-w-xs truncate font-medium">{p.name}</p><p className="text-xs text-slate-500">{p.categoryName}</p></div>
      </div>
    ) },
    { key: 'cost', header: 'Cost', sortKey: 'costPrice', align: 'right', render: (p) => formatMoney(p.costPrice) },
    { key: 'price', header: 'Price', sortKey: 'unitPrice', align: 'right', render: (p) => formatMoney(p.unitPrice) },
    { key: 'onHand', header: 'On hand', align: 'right', render: (p) => formatNumber(p.quantityOnHand) },
    { key: 'available', header: 'Available', align: 'right', render: (p) => formatNumber(p.quantityAvailable) },
    { key: 'threshold', header: 'Reorder at', sortKey: 'reorderThreshold', align: 'right', render: (p) => formatNumber(p.reorderThreshold) },
    { key: 'stock', header: 'Stock', render: (p) => <StatusBadge status={p.stockStatus} /> },
    { key: 'status', header: 'Status', render: (p) => <StatusBadge status={p.status} /> },
  ];

  return (
    <>
      <PageHeader title="Products" description="Your catalog with live stock across all warehouses."
                  actions={can.manageInventory(user?.role) &&
                    <Button icon={<Plus className="h-4 w-4" />} onClick={() => setCreating(true)}>New product</Button>} />
      <FilterBar>
        <SearchBox value={search} onChange={(v) => { setSearch(v); table.setPage(0); }} placeholder="Search name or SKU" className="sm:w-72" />
        <FilterSelect label="Category" value={categoryId} onChange={(v) => setFilter('categoryId', v)}>
          <option value="">All categories</option>
          {categories.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </FilterSelect>
        <FilterSelect label="Stock level" value={stockStatus} onChange={(v) => setFilter('stockStatus', v)}>
          <option value="">Any stock level</option>
          <option value="IN_STOCK">In stock</option>
          <option value="LOW_STOCK">Low stock</option>
          <option value="OUT_OF_STOCK">Out of stock</option>
        </FilterSelect>
        <FilterSelect label="Status" value={status} onChange={(v) => setFilter('status', v === 'ACTIVE' ? '' : v)}>
          <option value="ACTIVE">Active</option>
          <option value="ARCHIVED">Archived</option>
          <option value="ALL">All</option>
        </FilterSelect>
      </FilterBar>
      <DataTable columns={columns} rows={products.data?.content} rowKey={(p) => p.id} loading={products.loading}
                 error={products.error} onRetry={products.reload} sort={table.sort} onSort={table.setSort}
                 onRowClick={(p) => navigate(`/products/${p.id}`)}
                 emptyTitle="No products match" emptyMessage="Try a different search or filter."
                 footer={products.data && <Pagination page={products.data.page} size={products.data.size}
                   totalElements={products.data.totalElements} totalPages={products.data.totalPages} onPage={table.setPage} />} />
      <ProductFormModal open={creating} categories={categories.data ?? []} onClose={() => setCreating(false)}
                        onSaved={(p) => { setCreating(false); navigate(`/products/${p.id}`); }} />
    </>
  );
}
