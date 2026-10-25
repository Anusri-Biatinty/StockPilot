import { Pencil, Plus, Trash2 } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { categoriesApi } from '../../api/endpoints';
import type { Category } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { can } from '../../auth/permissions';
import { StatusBadge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { TextArea, TextInput } from '../../components/ui/Field';
import { ConfirmDialog, Modal } from '../../components/ui/Modal';
import { PageHeader } from '../../components/ui/PageHeader';
import { useToast } from '../../components/ui/Toast';
import { useApi } from '../../hooks/useApi';
import { errorMessage } from '../../lib/errors';

export function CategoriesPage() {
  const { user } = useAuth();
  const toast = useToast();
  const { data, error, loading, reload } = useApi(() => categoriesApi.list(), []);
  const [editing, setEditing] = useState<Category | 'new' | null>(null);
  const [deleting, setDeleting] = useState<Category | null>(null);
  const [form, setForm] = useState({ name: '', description: '', active: true });
  const [nameError, setNameError] = useState<string>();
  const [saving, setSaving] = useState(false);
  const canEdit = can.manageInventory(user?.role);

  const open = (c: Category | 'new') => {
    setEditing(c);
    setNameError(undefined);
    setForm(c === 'new' ? { name: '', description: '', active: true } : { name: c.name, description: c.description ?? '', active: c.active });
  };

  const save = async (e: FormEvent) => {
    e.preventDefault();
    if (!form.name.trim()) { setNameError('Name is required'); return; }
    setSaving(true);
    try {
      const body = { name: form.name.trim(), description: form.description.trim() || undefined, active: form.active };
      if (editing === 'new') await categoriesApi.create(body); else if (editing) await categoriesApi.update(editing.id, body);
      toast.success('Category saved');
      setEditing(null);
      reload();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (!deleting) return;
    setSaving(true);
    try {
      await categoriesApi.remove(deleting.id);
      toast.success(`Deleted ${deleting.name}`);
      setDeleting(null);
      reload();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const columns: Column<Category>[] = [
    { key: 'name', header: 'Name', render: (c) => <span className="font-medium">{c.name}</span> },
    { key: 'description', header: 'Description', render: (c) => <span className="block max-w-md truncate text-slate-500">{c.description ?? '—'}</span> },
    { key: 'products', header: 'Active products', align: 'right', render: (c) => (
      <Link to={`/products?categoryId=${c.id}`} className="font-medium text-brand-600 hover:underline">{c.productCount}</Link>) },
    { key: 'status', header: 'Status', render: (c) => <StatusBadge status={c.active ? 'ACTIVE' : 'INACTIVE'} /> },
    ...(canEdit ? [{ key: 'actions', header: '', align: 'right' as const, render: (c: Category) => (
      <div className="flex justify-end gap-1">
        <Button size="sm" variant="ghost" aria-label={`Edit ${c.name}`} onClick={() => open(c)}><Pencil className="h-4 w-4" /></Button>
        <Button size="sm" variant="ghost" aria-label={`Delete ${c.name}`} onClick={() => setDeleting(c)}><Trash2 className="h-4 w-4 text-rose-500" /></Button>
      </div>) }] : []),
  ];

  return (
    <>
      <PageHeader title="Categories" description="Group products for reporting and SKU generation."
                  actions={canEdit && <Button icon={<Plus className="h-4 w-4" />} onClick={() => open('new')}>New category</Button>} />
      <DataTable columns={columns} rows={data} rowKey={(c) => c.id} loading={loading} error={error} onRetry={reload}
                 emptyTitle="No categories yet" />
      <Modal open={editing !== null} onClose={() => setEditing(null)} title={editing === 'new' ? 'New category' : 'Edit category'}
             footer={<><Button variant="secondary" onClick={() => setEditing(null)}>Cancel</Button>
               <Button type="submit" form="category-form" loading={saving}>Save</Button></>}>
        <form id="category-form" onSubmit={save} noValidate className="space-y-4">
          <TextInput label="Name" value={form.name} maxLength={80} onChange={(e) => setForm({ ...form, name: e.target.value })} error={nameError} required />
          <TextArea label="Description" value={form.description} maxLength={500} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} className="h-4 w-4 rounded border-slate-300" />
            Active (inactive categories cannot receive new products)
          </label>
        </form>
      </Modal>
      <ConfirmDialog open={!!deleting} danger loading={saving} title="Delete category?" confirmLabel="Delete"
                     message={`Delete "${deleting?.name}"? Categories that still contain products cannot be deleted; deactivate them instead.`}
                     onConfirm={remove} onCancel={() => setDeleting(null)} />
    </>
  );
}
