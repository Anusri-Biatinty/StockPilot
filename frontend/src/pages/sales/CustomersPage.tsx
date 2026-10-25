import { Plus } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { customersApi, type CustomerRequest } from '../../api/endpoints';
import type { Customer } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { can } from '../../auth/permissions';
import { StatusBadge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { FilterSelect, SearchBox, TextInput } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import { FilterBar, PageHeader } from '../../components/ui/PageHeader';
import { Pagination } from '../../components/ui/Pagination';
import { useToast } from '../../components/ui/Toast';
import { useApi, useDebounced } from '../../hooks/useApi';
import { useTableState } from '../../hooks/useTableState';
import { errorMessage, fieldErrors } from '../../lib/errors';
import { formatMoney, formatNumber } from '../../lib/format';

const blank: CustomerRequest = { code: '', name: '', email: '', phone: '', address: '', city: '', country: '', active: true };

export function CustomersPage() {
  const { user } = useAuth();
  const toast = useToast();
  const [search, setSearch] = useState('');
  const [active, setActive] = useState('');
  const debounced = useDebounced(search);
  const table = useTableState({ key: 'name', direction: 'asc' });
  const customers = useApi(() => customersApi.list({ search: debounced, active, page: table.page, size: table.size, sort: table.sortParam }),
    [debounced, active, table.page, table.sortParam]);
  const [editing, setEditing] = useState<Customer | 'new' | null>(null);
  const [form, setForm] = useState<CustomerRequest>(blank);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const canEdit = can.manageSales(user?.role);

  const open = (c: Customer | 'new') => {
    setEditing(c);
    setErrors({});
    setForm(c === 'new' ? blank : { code: c.code, name: c.name, email: c.email ?? '', phone: c.phone ?? '', address: c.address ?? '',
      city: c.city ?? '', country: c.country ?? '', active: c.active });
  };

  const save = async (e: FormEvent) => {
    e.preventDefault();
    const v: Record<string, string> = {};
    if (!form.name.trim()) v.name = 'Name is required';
    if (form.email && !/^\S+@\S+\.\S+$/.test(form.email)) v.email = 'Enter a valid email';
    setErrors(v);
    if (Object.keys(v).length) return;
    setSaving(true);
    try {
      const body = { ...form, code: form.code || undefined };
      if (editing === 'new') await customersApi.create(body); else if (editing) await customersApi.update(editing.id, body);
      toast.success('Customer saved');
      setEditing(null);
      customers.reload();
    } catch (err) {
      setErrors(fieldErrors(err));
      toast.error(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const set = (k: keyof CustomerRequest) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value });

  const columns: Column<Customer>[] = [
    { key: 'code', header: 'Code', sortKey: 'code', render: (c) => <span className="font-mono text-xs font-medium">{c.code}</span> },
    { key: 'name', header: 'Customer', sortKey: 'name', render: (c) => <span className="font-medium">{c.name}</span> },
    { key: 'contact', header: 'Contact', render: (c) => <span className="text-slate-500">{c.email ?? '—'}{c.phone && <span className="block text-xs">{c.phone}</span>}</span> },
    { key: 'city', header: 'Location', sortKey: 'city', render: (c) => [c.city, c.country].filter(Boolean).join(', ') || '—' },
    { key: 'orders', header: 'Orders', align: 'right', render: (c) => (
      <Link to={`/sales-orders?customerId=${c.id}`} onClick={(e) => e.stopPropagation()} className="font-medium text-brand-600 hover:underline">{formatNumber(c.orderCount)}</Link>) },
    { key: 'revenue', header: 'Revenue', align: 'right', render: (c) => formatMoney(c.totalRevenue) },
    { key: 'status', header: 'Status', render: (c) => <StatusBadge status={c.active ? 'ACTIVE' : 'INACTIVE'} /> },
  ];

  return (
    <>
      <PageHeader title="Customers" description="Accounts you sell to, with booked order counts and revenue."
                  actions={canEdit && <Button icon={<Plus className="h-4 w-4" />} onClick={() => open('new')}>New customer</Button>} />
      <FilterBar>
        <SearchBox value={search} onChange={(v) => { setSearch(v); table.setPage(0); }} placeholder="Search name, code, email or city" className="sm:w-72" />
        <FilterSelect label="Status" value={active} onChange={(v) => { setActive(v); table.setPage(0); }}>
          <option value="">All</option><option value="true">Active</option><option value="false">Inactive</option>
        </FilterSelect>
      </FilterBar>
      <DataTable columns={columns} rows={customers.data?.content} rowKey={(c) => c.id} loading={customers.loading}
                 error={customers.error} onRetry={customers.reload} sort={table.sort} onSort={table.setSort}
                 onRowClick={canEdit ? open : undefined} emptyTitle="No customers found"
                 footer={customers.data && <Pagination page={customers.data.page} size={customers.data.size}
                   totalElements={customers.data.totalElements} totalPages={customers.data.totalPages} onPage={table.setPage} />} />
      <Modal open={editing !== null} onClose={() => setEditing(null)} title={editing === 'new' ? 'New customer' : 'Edit customer'} size="lg"
             footer={<><Button variant="secondary" onClick={() => setEditing(null)}>Cancel</Button><Button type="submit" form="customer-form" loading={saving}>Save</Button></>}>
        <form id="customer-form" onSubmit={save} noValidate className="grid gap-4 sm:grid-cols-2">
          <TextInput label="Code" value={form.code} onChange={set('code')} disabled={editing !== 'new'} error={errors.code}
                     hint={editing === 'new' ? 'Leave blank to generate' : 'Codes cannot be changed'} />
          <TextInput label="Name" value={form.name} onChange={set('name')} error={errors.name} required maxLength={160} />
          <TextInput label="Email" type="email" value={form.email} onChange={set('email')} error={errors.email} maxLength={160} />
          <TextInput label="Phone" value={form.phone} onChange={set('phone')} maxLength={40} />
          <TextInput className="sm:col-span-2" label="Address" value={form.address} onChange={set('address')} maxLength={255} />
          <TextInput label="City" value={form.city} onChange={set('city')} maxLength={80} />
          <TextInput label="Country" value={form.country} onChange={set('country')} maxLength={80} />
          <label className="flex items-center gap-2 text-sm sm:col-span-2">
            <input type="checkbox" checked={!!form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} className="h-4 w-4 rounded" /> Active
          </label>
        </form>
      </Modal>
    </>
  );
}
