import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { purchaseOrdersApi, suppliersApi, warehousesApi } from '../../api/endpoints';
import type { Product } from '../../api/types';
import { Button } from '../../components/ui/Button';
import { SelectInput, TextArea, TextInput } from '../../components/ui/Field';
import { LineItemsEditor, newLine, validateLines, type Line } from '../../components/ui/LineItemsEditor';
import { PageHeader } from '../../components/ui/PageHeader';
import { ErrorState, LoadingBlock } from '../../components/ui/States';
import { useToast } from '../../components/ui/Toast';
import { useApi } from '../../hooks/useApi';
import { errorMessage } from '../../lib/errors';

export function PurchaseOrderFormPage() {
  const params = useParams();
  const editingId = params.id ? Number(params.id) : null;
  const [query] = useSearchParams();
  const navigate = useNavigate();
  const toast = useToast();
  const suppliers = useApi(() => suppliersApi.list({ active: true, size: 100, sort: 'name,asc' }), []);
  const warehouses = useApi(() => warehousesApi.list(), []);
  const [form, setForm] = useState({ supplierId: query.get('supplierId') ?? '', warehouseId: '', expectedDate: '', notes: '' });
  const [lines, setLines] = useState<Line[]>([newLine()]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [lineErrors, setLineErrors] = useState<Record<number, string>>({});
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loaded, setLoaded] = useState(!editingId);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!editingId) return;
    purchaseOrdersApi.get(editingId).then((po) => {
      if (po.status !== 'DRAFT') { navigate(`/purchase-orders/${po.id}`, { replace: true }); return; }
      setForm({ supplierId: String(po.supplierId), warehouseId: String(po.warehouseId), expectedDate: po.expectedDate ?? '', notes: po.notes ?? '' });
      setLines(po.items.map((i) => newLine({ id: i.productId, sku: i.sku, name: i.productName, costPrice: i.unitCost } as Product,
        String(i.quantityOrdered), String(i.unitCost))));
      setLoaded(true);
    }).catch((e) => setLoadError(errorMessage(e)));
  }, [editingId, navigate]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const v: Record<string, string> = {};
    if (!form.supplierId) v.supplierId = 'Choose a supplier';
    if (!form.warehouseId) v.warehouseId = 'Choose a destination warehouse';
    const { lineErrors: le, message } = validateLines(lines);
    setErrors(v);
    setLineErrors(le);
    if (Object.keys(v).length || Object.keys(le).length) return;
    if (message) { toast.error(message); return; }

    const body = {
      supplierId: Number(form.supplierId), warehouseId: Number(form.warehouseId),
      expectedDate: form.expectedDate || undefined, notes: form.notes || undefined,
      items: lines.map((l) => ({ productId: l.product!.id, quantity: Number(l.quantity), unitCost: l.unitCost === '' ? undefined : Number(l.unitCost) })),
    };
    setSaving(true);
    try {
      const po = editingId ? await purchaseOrdersApi.update(editingId, body) : await purchaseOrdersApi.create(body);
      toast.success(`${po.poNumber} saved as draft`);
      navigate(`/purchase-orders/${po.id}`);
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  if (loadError) return <div className="card"><ErrorState message={loadError} /></div>;
  if (!loaded) return <LoadingBlock />;
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value });

  return (
    <>
      <PageHeader title={editingId ? 'Edit purchase order' : 'New purchase order'} back={{ to: '/purchase-orders', label: 'Purchase orders' }}
                  description="Orders are saved as drafts. Submit them for approval from the order page." />
      <form onSubmit={submit} noValidate className="space-y-6">
        <section className="card grid gap-4 p-5 md:grid-cols-3">
          <SelectInput label="Supplier" value={form.supplierId} onChange={set('supplierId')} error={errors.supplierId} required>
            <option value="">Select…</option>
            {suppliers.data?.content.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </SelectInput>
          <SelectInput label="Deliver to warehouse" value={form.warehouseId} onChange={set('warehouseId')} error={errors.warehouseId} required>
            <option value="">Select…</option>
            {warehouses.data?.filter((w) => w.active).map((w) => <option key={w.id} value={w.id}>{w.code} — {w.name}</option>)}
          </SelectInput>
          <TextInput label="Expected delivery" type="date" value={form.expectedDate} onChange={set('expectedDate')} />
          <TextArea className="md:col-span-3" label="Notes" value={form.notes} onChange={set('notes')} maxLength={1000} rows={2} />
        </section>
        <section className="card p-5">
          <h2 className="mb-1 text-sm font-semibold">Line items</h2>
          <p className="mb-4 text-xs text-slate-500">Leave unit cost empty to use the supplier's catalogue cost (or the product cost).</p>
          <LineItemsEditor lines={lines} onChange={setLines} mode="purchase" errors={lineErrors} />
        </section>
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={() => navigate(-1)}>Cancel</Button>
          <Button type="submit" loading={saving}>Save draft</Button>
        </div>
      </form>
    </>
  );
}
