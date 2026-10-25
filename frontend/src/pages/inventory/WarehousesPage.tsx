import { MapPin, Pencil, Plus } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { warehousesApi, type WarehouseRequest } from '../../api/endpoints';
import type { Warehouse } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { can } from '../../auth/permissions';
import { StatusBadge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { TextInput } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import { PageHeader } from '../../components/ui/PageHeader';
import { EmptyState, ErrorState, LoadingBlock } from '../../components/ui/States';
import { useToast } from '../../components/ui/Toast';
import { useApi } from '../../hooks/useApi';
import { errorMessage, fieldErrors } from '../../lib/errors';
import { formatMoney, formatNumber } from '../../lib/format';

const blank: WarehouseRequest = { code: '', name: '', address: '', city: '', country: '', active: true };

export function WarehousesPage() {
  const { user } = useAuth();
  const toast = useToast();
  const { data, error, loading, reload } = useApi(() => warehousesApi.list(), []);
  const [editing, setEditing] = useState<Warehouse | 'new' | null>(null);
  const [form, setForm] = useState<WarehouseRequest>(blank);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const canEdit = can.manageInventory(user?.role);

  const open = (w: Warehouse | 'new') => {
    setEditing(w);
    setErrors({});
    setForm(w === 'new' ? blank : { code: w.code, name: w.name, address: w.address ?? '', city: w.city ?? '', country: w.country ?? '', active: w.active });
  };

  const save = async (e: FormEvent) => {
    e.preventDefault();
    const v: Record<string, string> = {};
    if (!/^[A-Za-z0-9][A-Za-z0-9-]{1,19}$/.test(form.code)) v.code = '2-20 letters, digits or dashes';
    if (!form.name.trim()) v.name = 'Name is required';
    setErrors(v);
    if (Object.keys(v).length) return;
    setSaving(true);
    try {
      if (editing === 'new') await warehousesApi.create(form); else if (editing) await warehousesApi.update(editing.id, form);
      toast.success('Warehouse saved');
      setEditing(null);
      reload();
    } catch (err) {
      setErrors(fieldErrors(err));
      toast.error(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const set = (k: keyof WarehouseRequest) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value });

  return (
    <>
      <PageHeader title="Warehouses" description="Stock locations. Each product's stock is tracked separately per warehouse."
                  actions={canEdit && <Button icon={<Plus className="h-4 w-4" />} onClick={() => open('new')}>New warehouse</Button>} />
      {error ? <div className="card"><ErrorState message={error} onRetry={reload} /></div>
        : loading && !data ? <LoadingBlock />
        : !data?.length ? <div className="card"><EmptyState title="No warehouses yet" /></div>
        : (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {data.map((w) => (
              <article key={w.id} className="card p-5">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="font-mono text-xs font-semibold text-brand-600">{w.code}</p>
                    <h2 className="font-semibold">{w.name}</h2>
                    <p className="mt-1 flex items-center gap-1 text-sm text-slate-500"><MapPin className="h-3.5 w-3.5" />
                      {[w.address, w.city, w.country].filter(Boolean).join(', ') || 'No address'}</p>
                  </div>
                  <StatusBadge status={w.active ? 'ACTIVE' : 'INACTIVE'} />
                </div>
                <dl className="mt-4 grid grid-cols-3 gap-2 border-t border-slate-200 pt-4 text-center dark:border-slate-800">
                  <div><dt className="text-xs text-slate-500">SKUs</dt><dd className="font-semibold tabular-nums">{formatNumber(w.productCount)}</dd></div>
                  <div><dt className="text-xs text-slate-500">Units</dt><dd className="font-semibold tabular-nums">{formatNumber(w.totalUnits)}</dd></div>
                  <div><dt className="text-xs text-slate-500">Value</dt><dd className="font-semibold tabular-nums">{formatMoney(w.stockValue)}</dd></div>
                </dl>
                <div className="mt-4 flex gap-2">
                  <Link to={`/inventory?warehouseId=${w.id}`} className="flex-1"><Button variant="secondary" size="sm" className="w-full">View stock</Button></Link>
                  {canEdit && <Button variant="ghost" size="sm" icon={<Pencil className="h-4 w-4" />} onClick={() => open(w)}>Edit</Button>}
                </div>
              </article>
            ))}
          </div>
        )}
      <Modal open={editing !== null} onClose={() => setEditing(null)} title={editing === 'new' ? 'New warehouse' : 'Edit warehouse'}
             footer={<><Button variant="secondary" onClick={() => setEditing(null)}>Cancel</Button><Button type="submit" form="wh-form" loading={saving}>Save</Button></>}>
        <form id="wh-form" onSubmit={save} noValidate className="grid gap-4 sm:grid-cols-2">
          <TextInput label="Code" value={form.code} onChange={set('code')} error={errors.code} required maxLength={20} />
          <TextInput label="Name" value={form.name} onChange={set('name')} error={errors.name} required maxLength={120} />
          <TextInput className="sm:col-span-2" label="Address" value={form.address} onChange={set('address')} maxLength={255} />
          <TextInput label="City" value={form.city} onChange={set('city')} maxLength={80} />
          <TextInput label="Country" value={form.country} onChange={set('country')} maxLength={80} />
          <label className="flex items-center gap-2 text-sm sm:col-span-2">
            <input type="checkbox" checked={!!form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} className="h-4 w-4 rounded" />
            Active (a warehouse must be empty before it can be deactivated)
          </label>
        </form>
      </Modal>
    </>
  );
}
