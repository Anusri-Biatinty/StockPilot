import { Plus } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { suppliersApi } from '../../api/endpoints';
import type { Supplier } from '../../api/types';
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
import { SupplierFormModal } from './SupplierFormModal';

export function SuppliersPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [active, setActive] = useState('');
  const debounced = useDebounced(search);
  const table = useTableState({ key: 'name', direction: 'asc' });
  const [creating, setCreating] = useState(false);
  const suppliers = useApi(() => suppliersApi.list({ search: debounced, active, page: table.page, size: table.size, sort: table.sortParam }),
    [debounced, active, table.page, table.sortParam]);

  const columns: Column<Supplier>[] = [
    { key: 'code', header: 'Code', sortKey: 'code', render: (s) => <span className="font-mono text-xs font-medium">{s.code}</span> },
    { key: 'name', header: 'Supplier', sortKey: 'name', render: (s) => <span className="font-medium">{s.name}</span> },
    { key: 'contact', header: 'Contact', render: (s) => (
      <span className="text-slate-500">{s.contactName ?? '—'}{s.email && <span className="block text-xs">{s.email}</span>}</span>) },
    { key: 'products', header: 'Products', align: 'right', render: (s) => formatNumber(s.productCount) },
    { key: 'open', header: 'Open POs', align: 'right', render: (s) => formatNumber(s.openOrders) },
    { key: 'spend', header: 'Received value', align: 'right', render: (s) => formatMoney(s.totalSpend) },
    { key: 'status', header: 'Status', render: (s) => <StatusBadge status={s.active ? 'ACTIVE' : 'INACTIVE'} /> },
  ];

  return (
    <>
      <PageHeader title="Suppliers" description="Vendors, their catalogues and purchase history."
                  actions={can.manageProcurement(user?.role) && <Button icon={<Plus className="h-4 w-4" />} onClick={() => setCreating(true)}>New supplier</Button>} />
      <FilterBar>
        <SearchBox value={search} onChange={(v) => { setSearch(v); table.setPage(0); }} placeholder="Search name, code or contact" className="sm:w-72" />
        <FilterSelect label="Status" value={active} onChange={(v) => { setActive(v); table.setPage(0); }}>
          <option value="">All</option><option value="true">Active</option><option value="false">Inactive</option>
        </FilterSelect>
      </FilterBar>
      <DataTable columns={columns} rows={suppliers.data?.content} rowKey={(s) => s.id} loading={suppliers.loading}
                 error={suppliers.error} onRetry={suppliers.reload} sort={table.sort} onSort={table.setSort}
                 onRowClick={(s) => navigate(`/suppliers/${s.id}`)} emptyTitle="No suppliers found"
                 footer={suppliers.data && <Pagination page={suppliers.data.page} size={suppliers.data.size}
                   totalElements={suppliers.data.totalElements} totalPages={suppliers.data.totalPages} onPage={table.setPage} />} />
      <SupplierFormModal open={creating} onClose={() => setCreating(false)} onSaved={(s) => { setCreating(false); navigate(`/suppliers/${s.id}`); }} />
    </>
  );
}
