import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { inventoryApi, warehousesApi } from '../../api/endpoints';
import type { Movement, MovementType } from '../../api/types';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { FilterSelect, SearchBox } from '../../components/ui/Field';
import { FilterBar, PageHeader } from '../../components/ui/PageHeader';
import { Pagination } from '../../components/ui/Pagination';
import { useApi, useDebounced } from '../../hooks/useApi';
import { useTableState } from '../../hooks/useTableState';
import { humanize } from '../../lib/format';
import { movementColumns } from '../catalog/ProductDetailPage';

const TYPES: MovementType[] = ['RECEIPT', 'ISSUE', 'TRANSFER_IN', 'TRANSFER_OUT', 'ADJUSTMENT', 'PURCHASE_RECEIPT', 'SALES_SHIPMENT'];

export function MovementsPage() {
  const [params, setParams] = useSearchParams();
  const [search, setSearch] = useState('');
  const debounced = useDebounced(search);
  const get = (k: string) => params.get(k) ?? '';
  const table = useTableState({ key: 'createdAt', direction: 'desc' }, 25);

  const warehouses = useApi(() => warehousesApi.list(), []);
  const movements = useApi(() => inventoryApi.movements({
    search: debounced, warehouseId: get('warehouseId'), type: get('type'), from: get('from'), to: get('to'),
    page: table.page, size: table.size, sort: table.sortParam,
  }), [debounced, params.toString(), table.page, table.sortParam]);

  const setFilter = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value); else next.delete(key);
    setParams(next, { replace: true });
    table.setPage(0);
  };

  const columns: Column<Movement>[] = [
    { ...movementColumns[0], sortKey: 'createdAt' },
    { key: 'product', header: 'Product', render: (m) => (
      <Link to={`/products/${m.productId}`} className="hover:text-brand-600"><span className="font-medium">{m.productName}</span>
        <span className="ml-1 font-mono text-xs text-slate-500">{m.sku}</span></Link>) },
    ...movementColumns.slice(1),
  ];

  return (
    <>
      <PageHeader title="Stock movement history" description="Every change to stock on hand, with who made it and why. Entries cannot be edited or deleted." />
      <FilterBar>
        <SearchBox value={search} onChange={(v) => { setSearch(v); table.setPage(0); }} placeholder="Search product or SKU" className="sm:w-64" />
        <FilterSelect label="Movement type" value={get('type')} onChange={(v) => setFilter('type', v)}>
          <option value="">All types</option>
          {TYPES.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}
        </FilterSelect>
        <FilterSelect label="Warehouse" value={get('warehouseId')} onChange={(v) => setFilter('warehouseId', v)}>
          <option value="">All warehouses</option>
          {warehouses.data?.map((w) => <option key={w.id} value={w.id}>{w.code}</option>)}
        </FilterSelect>
        <input type="date" aria-label="From date" className="input w-auto" value={get('from')} onChange={(e) => setFilter('from', e.target.value)} />
        <input type="date" aria-label="To date" className="input w-auto" value={get('to')} onChange={(e) => setFilter('to', e.target.value)} />
      </FilterBar>
      <DataTable columns={columns} rows={movements.data?.content} rowKey={(m) => m.id} loading={movements.loading}
                 error={movements.error} onRetry={movements.reload} sort={table.sort} onSort={table.setSort}
                 emptyTitle="No movements match these filters"
                 footer={movements.data && <Pagination page={movements.data.page} size={movements.data.size}
                   totalElements={movements.data.totalElements} totalPages={movements.data.totalPages} onPage={table.setPage} />} />
    </>
  );
}
