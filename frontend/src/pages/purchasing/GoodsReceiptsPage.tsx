import { useState } from 'react';
import { Link } from 'react-router-dom';
import { purchaseOrdersApi } from '../../api/endpoints';
import type { GoodsReceipt } from '../../api/types';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { SearchBox } from '../../components/ui/Field';
import { FilterBar, PageHeader } from '../../components/ui/PageHeader';
import { Pagination } from '../../components/ui/Pagination';
import { useApi, useDebounced } from '../../hooks/useApi';
import { useTableState } from '../../hooks/useTableState';
import { formatDateTime, formatNumber } from '../../lib/format';

export function GoodsReceiptsPage() {
  const [search, setSearch] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const debounced = useDebounced(search);
  const table = useTableState({ key: 'receivedAt', direction: 'desc' });
  const receipts = useApi(() => purchaseOrdersApi.receipts({ search: debounced, from, to, page: table.page, size: table.size, sort: table.sortParam }),
    [debounced, from, to, table.page, table.sortParam]);

  const columns: Column<GoodsReceipt>[] = [
    { key: 'number', header: 'Receipt', sortKey: 'receiptNumber', render: (r) => <span className="font-mono text-xs font-semibold">{r.receiptNumber}</span> },
    { key: 'date', header: 'Received', sortKey: 'receivedAt', render: (r) => formatDateTime(r.receivedAt) },
    { key: 'po', header: 'Purchase order', render: (r) => <Link to={`/purchase-orders/${r.purchaseOrderId}`} className="font-mono text-xs font-medium text-brand-600 hover:underline">{r.poNumber}</Link> },
    { key: 'supplier', header: 'Supplier', render: (r) => r.supplierName },
    { key: 'wh', header: 'Warehouse', render: (r) => r.warehouseCode },
    { key: 'items', header: 'Items', render: (r) => <span className="block max-w-xs truncate text-slate-500" title={r.items.map((i) => `${i.sku} × ${i.quantityReceived}`).join(', ')}>
      {r.items.map((i) => `${i.sku} × ${i.quantityReceived}`).join(', ')}</span> },
    { key: 'units', header: 'Units', align: 'right', render: (r) => formatNumber(r.totalUnits) },
    { key: 'by', header: 'Received by', render: (r) => r.receivedBy ?? '—' },
  ];

  return (
    <>
      <PageHeader title="Goods receipts" description="Deliveries booked against purchase orders. Each receipt created PURCHASE_RECEIPT stock movements." />
      <FilterBar>
        <SearchBox value={search} onChange={(v) => { setSearch(v); table.setPage(0); }} placeholder="Search receipt or PO number" className="sm:w-72" />
        <input type="date" aria-label="Received from" className="input w-auto" value={from} onChange={(e) => { setFrom(e.target.value); table.setPage(0); }} />
        <input type="date" aria-label="Received to" className="input w-auto" value={to} onChange={(e) => { setTo(e.target.value); table.setPage(0); }} />
      </FilterBar>
      <DataTable columns={columns} rows={receipts.data?.content} rowKey={(r) => r.id} loading={receipts.loading}
                 error={receipts.error} onRetry={receipts.reload} sort={table.sort} onSort={table.setSort} emptyTitle="No goods receipts found"
                 footer={receipts.data && <Pagination page={receipts.data.page} size={receipts.data.size}
                   totalElements={receipts.data.totalElements} totalPages={receipts.data.totalPages} onPage={table.setPage} />} />
    </>
  );
}
