import { Plus } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { salesOrdersApi } from '../../api/endpoints';
import type { SalesOrderStatus, SalesOrderSummary } from '../../api/types';
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

const STATUSES: SalesOrderStatus[] = ['DRAFT', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'];

export function SalesOrdersPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const [search, setSearch] = useState('');
  const debounced = useDebounced(search);
  const get = (k: string) => params.get(k) ?? '';
  const table = useTableState({ key: 'createdAt', direction: 'desc' });
  const orders = useApi(() => salesOrdersApi.list({ search: debounced, status: get('status'), customerId: get('customerId'),
    from: get('from'), to: get('to'), page: table.page, size: table.size, sort: table.sortParam }),
    [debounced, params.toString(), table.page, table.sortParam]);

  const setFilter = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value); else next.delete(key);
    setParams(next, { replace: true });
    table.setPage(0);
  };

  const columns: Column<SalesOrderSummary>[] = [
    { key: 'number', header: 'Order', sortKey: 'orderNumber', render: (o) => <span className="font-mono text-xs font-semibold">{o.orderNumber}</span> },
    { key: 'customer', header: 'Customer', render: (o) => <span className="font-medium">{o.customerName}</span> },
    { key: 'date', header: 'Date', sortKey: 'orderDate', render: (o) => formatDate(o.orderDate) },
    { key: 'wh', header: 'Ships from', render: (o) => o.warehouseCode },
    { key: 'lines', header: 'Lines', align: 'right', render: (o) => o.itemCount },
    { key: 'total', header: 'Total', sortKey: 'totalAmount', align: 'right', render: (o) => formatMoney(o.totalAmount) },
    { key: 'status', header: 'Status', render: (o) => <StatusBadge status={o.status} /> },
  ];

  return (
    <>
      <PageHeader title="Sales orders" description="Draft → confirmed (stock reserved) → shipped → delivered."
                  actions={can.manageSales(user?.role) && <Link to="/sales-orders/new"><Button icon={<Plus className="h-4 w-4" />}>New sales order</Button></Link>} />
      <FilterBar>
        <SearchBox value={search} onChange={(v) => { setSearch(v); table.setPage(0); }} placeholder="Search order number or customer" className="sm:w-72" />
        <FilterSelect label="Status" value={get('status')} onChange={(v) => setFilter('status', v)}>
          <option value="">All statuses</option>
          {STATUSES.map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
        </FilterSelect>
        <input type="date" aria-label="From date" className="input w-auto" value={get('from')} onChange={(e) => setFilter('from', e.target.value)} />
        <input type="date" aria-label="To date" className="input w-auto" value={get('to')} onChange={(e) => setFilter('to', e.target.value)} />
        {get('customerId') && <Button variant="ghost" size="sm" onClick={() => setFilter('customerId', '')}>Clear customer filter</Button>}
      </FilterBar>
      <DataTable columns={columns} rows={orders.data?.content} rowKey={(o) => o.id} loading={orders.loading} error={orders.error}
                 onRetry={orders.reload} sort={table.sort} onSort={table.setSort} onRowClick={(o) => navigate(`/sales-orders/${o.id}`)}
                 emptyTitle="No sales orders match"
                 footer={orders.data && <Pagination page={orders.data.page} size={orders.data.size}
                   totalElements={orders.data.totalElements} totalPages={orders.data.totalPages} onPage={table.setPage} />} />
    </>
  );
}
