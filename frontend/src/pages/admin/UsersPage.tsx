import { Plus } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { usersApi } from '../../api/endpoints';
import type { Role, User } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { ROLE_LABELS } from '../../auth/permissions';
import { Badge, StatusBadge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { FilterSelect, SearchBox, SelectInput, TextInput } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import { FilterBar, PageHeader } from '../../components/ui/PageHeader';
import { Pagination } from '../../components/ui/Pagination';
import { useToast } from '../../components/ui/Toast';
import { useApi, useDebounced } from '../../hooks/useApi';
import { useTableState } from '../../hooks/useTableState';
import { errorMessage, fieldErrors } from '../../lib/errors';
import { formatDateTime } from '../../lib/format';
import { validatePassword } from '../auth/RegisterPage';

const ROLES: Role[] = ['ADMIN', 'INVENTORY_MANAGER', 'SALES_MANAGER', 'VIEWER'];

export function UsersPage() {
  const { user: me } = useAuth();
  const toast = useToast();
  const [search, setSearch] = useState('');
  const [role, setRole] = useState('');
  const debounced = useDebounced(search);
  const table = useTableState({ key: 'fullName', direction: 'asc' });
  const users = useApi(() => usersApi.list({ search: debounced, role, page: table.page, size: table.size, sort: table.sortParam }),
    [debounced, role, table.page, table.sortParam]);
  const roles = useApi(() => usersApi.roles(), []);
  const [editing, setEditing] = useState<User | 'new' | null>(null);
  const [form, setForm] = useState({ email: '', fullName: '', password: '', role: 'VIEWER' as Role, enabled: true });
  const [errors, setErrors] = useState<Record<string, string | undefined>>({});
  const [saving, setSaving] = useState(false);

  const open = (u: User | 'new') => {
    setEditing(u);
    setErrors({});
    setForm(u === 'new' ? { email: '', fullName: '', password: '', role: 'VIEWER', enabled: true }
      : { email: u.email, fullName: u.fullName, password: '', role: u.role, enabled: u.enabled });
  };

  const save = async (e: FormEvent) => {
    e.preventDefault();
    const v: Record<string, string | undefined> = {};
    if (!form.fullName.trim()) v.fullName = 'Name is required';
    if (editing === 'new') {
      if (!/^\S+@\S+\.\S+$/.test(form.email)) v.email = 'Enter a valid email';
      v.password = validatePassword(form.password);
    }
    setErrors(v);
    if (Object.values(v).some(Boolean)) return;
    setSaving(true);
    try {
      if (editing === 'new') await usersApi.create({ email: form.email.trim(), fullName: form.fullName.trim(), password: form.password, role: form.role });
      else if (editing) await usersApi.update(editing.id, { fullName: form.fullName.trim(), role: form.role, enabled: form.enabled });
      toast.success('User saved');
      setEditing(null);
      users.reload();
    } catch (err) {
      setErrors(fieldErrors(err));
      toast.error(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const columns: Column<User>[] = [
    { key: 'name', header: 'Name', sortKey: 'fullName', render: (u) => <span className="font-medium">{u.fullName}{u.id === me?.id && <span className="ml-2 text-xs text-slate-500">(you)</span>}</span> },
    { key: 'email', header: 'Email', sortKey: 'email', render: (u) => u.email },
    { key: 'role', header: 'Role', render: (u) => <Badge tone={u.role === 'ADMIN' ? 'violet' : u.role === 'VIEWER' ? 'gray' : 'indigo'}>{ROLE_LABELS[u.role]}</Badge> },
    { key: 'status', header: 'Status', render: (u) => <StatusBadge status={u.enabled ? 'ACTIVE' : 'INACTIVE'} /> },
    { key: 'login', header: 'Last sign-in', sortKey: 'lastLoginAt', render: (u) => formatDateTime(u.lastLoginAt) },
  ];

  const isSelf = editing !== 'new' && editing?.id === me?.id;
  return (
    <>
      <PageHeader title="Users & roles" description="Assign roles to control what each person can do. Changes apply immediately."
                  actions={<Button icon={<Plus className="h-4 w-4" />} onClick={() => open('new')}>New user</Button>} />
      <div className="mb-6 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {roles.data?.map((r) => (
          <div key={r.name} className="card p-4">
            <p className="font-semibold">{ROLE_LABELS[r.name]}</p>
            <p className="mb-2 text-xs text-slate-500">{r.description}</p>
            <ul className="space-y-1 text-xs text-slate-600 dark:text-slate-400">{r.permissions.map((p) => <li key={p}>• {p}</li>)}</ul>
          </div>
        ))}
      </div>
      <FilterBar>
        <SearchBox value={search} onChange={(v) => { setSearch(v); table.setPage(0); }} placeholder="Search name or email" className="sm:w-72" />
        <FilterSelect label="Role" value={role} onChange={(v) => { setRole(v); table.setPage(0); }}>
          <option value="">All roles</option>
          {ROLES.map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
        </FilterSelect>
      </FilterBar>
      <DataTable columns={columns} rows={users.data?.content} rowKey={(u) => u.id} loading={users.loading} error={users.error}
                 onRetry={users.reload} sort={table.sort} onSort={table.setSort} onRowClick={open} emptyTitle="No users found"
                 footer={users.data && <Pagination page={users.data.page} size={users.data.size}
                   totalElements={users.data.totalElements} totalPages={users.data.totalPages} onPage={table.setPage} />} />
      <Modal open={editing !== null} onClose={() => setEditing(null)} title={editing === 'new' ? 'New user' : 'Edit user'}
             footer={<><Button variant="secondary" onClick={() => setEditing(null)}>Cancel</Button><Button type="submit" form="user-form" loading={saving}>Save</Button></>}>
        <form id="user-form" onSubmit={save} noValidate className="space-y-4">
          <TextInput label="Email" type="email" value={form.email} disabled={editing !== 'new'} error={errors.email}
                     onChange={(e) => setForm({ ...form, email: e.target.value })} required />
          <TextInput label="Full name" value={form.fullName} error={errors.fullName} maxLength={120}
                     onChange={(e) => setForm({ ...form, fullName: e.target.value })} required />
          {editing === 'new' && <TextInput label="Initial password" type="password" autoComplete="new-password" value={form.password}
                                           error={errors.password} hint="Share it securely; the user can change it under Profile."
                                           onChange={(e) => setForm({ ...form, password: e.target.value })} required />}
          <SelectInput label="Role" value={form.role} disabled={isSelf} onChange={(e) => setForm({ ...form, role: e.target.value as Role })}
                       hint={isSelf ? 'You cannot change your own role' : undefined}>
            {ROLES.map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
          </SelectInput>
          {editing !== 'new' && (
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={form.enabled} disabled={isSelf} onChange={(e) => setForm({ ...form, enabled: e.target.checked })} className="h-4 w-4 rounded" />
              Account enabled (disabling signs the user out everywhere)
            </label>
          )}
        </form>
      </Modal>
    </>
  );
}
