import { Plus } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { purchaseOrdersApi, suppliersApi } from '../../api/endpoints';
import type { PurchaseOrderStatus, PurchaseOrderSummary } from '../../api/types';
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
import { formatDate, formatMoney, humanize } from '../../lib/format';

const STATUSES: PurchaseOrderStatus[] = ['DRAFT', 'SUBMITTED', 'APPROVED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED'];

export function PurchaseOrdersPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const [search, setSearch] = useState('');
  const debounced = useDebounced(search);
  const get = (k: string) => params.get(k) ?? '';
  const table = useTableState({ key: 'createdAt', direction: 'desc' });
  const suppliers = useApi(() => suppliersApi.list({ size: 100, sort: 'name,asc' }), []);
  const orders = useApi(() => purchaseOrdersApi.list({ search: debounced, status: get('status'), supplierId: get('supplierId'),
    from: get('from'), to: get('to'), page: table.page, size: table.size, sort: table.sortParam }),
    [debounced, params.toString(), table.page, table.sortParam]);

  const setFilter = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value); else next.delete(key);
    setParams(next, { replace: true });
    table.setPage(0);
  };

  const columns: Column<PurchaseOrderSummary>[] = [
    { key: 'po', header: 'PO number', sortKey: 'poNumber', render: (o) => <span className="font-mono text-xs font-semibold">{o.poNumber}</span> },
    { key: 'supplier', header: 'Supplier', render: (o) => <span className="font-medium">{o.supplierName}</span> },
    { key: 'wh', header: 'Deliver to', render: (o) => o.warehouseCode },
    { key: 'date', header: 'Ordered', sortKey: 'orderDate', render: (o) => formatDate(o.orderDate) },
    { key: 'expected', header: 'Expected', sortKey: 'expectedDate', render: (o) => formatDate(o.expectedDate) },
    { key: 'lines', header: 'Lines', align: 'right', render: (o) => o.itemCount },
    { key: 'total', header: 'Total', sortKey: 'totalAmount', align: 'right', render: (o) => formatMoney(o.totalAmount) },
    { key: 'status', header: 'Status', render: (o) => <StatusBadge status={o.status} /> },
  ];

  return (
    <>
      <PageHeader title="Purchase orders" description="Draft → submitted → approved → received. Receipts update stock automatically."
                  actions={can.manageProcurement(user?.role) && <Link to="/purchase-orders/new"><Button icon={<Plus className="h-4 w-4" />}>New purchase order</Button></Link>} />
      <FilterBar>
        <SearchBox value={search} onChange={(v) => { setSearch(v); table.setPage(0); }} placeholder="Search PO number or supplier" className="sm:w-64" />
        <FilterSelect label="Status" value={get('status')} onChange={(v) => setFilter('status', v)}>
          <option value="">All statuses</option>
          {STATUSES.map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
        </FilterSelect>
        <FilterSelect label="Supplier" value={get('supplierId')} onChange={(v) => setFilter('supplierId', v)}>
          <option value="">All suppliers</option>
          {suppliers.data?.content.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
        </FilterSelect>
        <input type="date" aria-label="Ordered from" className="input w-auto" value={get('from')} onChange={(e) => setFilter('from', e.target.value)} />
        <input type="date" aria-label="Ordered to" className="input w-auto" value={get('to')} onChange={(e) => setFilter('to', e.target.value)} />
      </FilterBar>
      <DataTable columns={columns} rows={orders.data?.content} rowKey={(o) => o.id} loading={orders.loading} error={orders.error}
                 onRetry={orders.reload} sort={table.sort} onSort={table.setSort} onRowClick={(o) => navigate(`/purchase-orders/${o.id}`)}
                 emptyTitle="No purchase orders match"
                 footer={orders.data && <Pagination page={orders.data.page} size={orders.data.size}
                   totalElements={orders.data.totalElements} totalPages={orders.data.totalPages} onPage={table.setPage} />} />
    </>
  );
}
