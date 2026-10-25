import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { customersApi, inventoryApi, salesOrdersApi, warehousesApi } from '../../api/endpoints';
import type { Product } from '../../api/types';
import { Button } from '../../components/ui/Button';
import { SelectInput, TextArea } from '../../components/ui/Field';
import { LineItemsEditor, newLine, validateLines, type Line } from '../../components/ui/LineItemsEditor';
import { PageHeader } from '../../components/ui/PageHeader';
import { ErrorState, LoadingBlock } from '../../components/ui/States';
import { useToast } from '../../components/ui/Toast';
import { useApi } from '../../hooks/useApi';
import { errorMessage } from '../../lib/errors';

export function SalesOrderFormPage() {
  const params = useParams();
  const editingId = params.id ? Number(params.id) : null;
  const navigate = useNavigate();
  const toast = useToast();
  const customers = useApi(() => customersApi.list({ active: true, size: 100, sort: 'name,asc' }), []);
  const warehouses = useApi(() => warehousesApi.list(), []);
  const [form, setForm] = useState({ customerId: '', warehouseId: '', shippingAddress: '', notes: '' });
  const [lines, setLines] = useState<Line[]>([newLine()]);
  const [availability, setAvailability] = useState<Record<number, number>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [lineErrors, setLineErrors] = useState<Record<number, string>>({});
  const [loaded, setLoaded] = useState(!editingId);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState<'draft' | 'confirm' | null>(null);

  useEffect(() => {
    if (!editingId) return;
    salesOrdersApi.get(editingId).then((so) => {
      if (so.status !== 'DRAFT') { navigate(`/sales-orders/${so.id}`, { replace: true }); return; }
      setForm({ customerId: String(so.customerId), warehouseId: String(so.warehouseId), shippingAddress: so.shippingAddress ?? '', notes: so.notes ?? '' });
      setLines(so.items.map((i) => newLine({ id: i.productId, sku: i.sku, name: i.productName, unitPrice: i.unitPrice } as Product, String(i.quantity))));
      setLoaded(true);
    }).catch((e) => setLoadError(errorMessage(e)));
  }, [editingId, navigate]);

  // Live availability for each chosen product in the chosen warehouse (display only; the server re-checks).
  const productKey = lines.map((l) => l.product?.id ?? 0).join(',');
  useEffect(() => {
    if (!form.warehouseId) { setAvailability({}); return; }
    const ids = lines.map((l) => l.product?.id).filter((id): id is number => !!id);
    let active = true;
    Promise.all(ids.map((productId) => inventoryApi.balances({ warehouseId: form.warehouseId, productId, size: 1 })
      .then((p) => [productId, p.content[0]?.available ?? 0] as const)))
      .then((pairs) => active && setAvailability(Object.fromEntries(pairs)))
      .catch(() => undefined);
    return () => { active = false; };
  }, [form.warehouseId, productKey]);

  const submit = async (confirm: boolean, e?: FormEvent) => {
    e?.preventDefault();
    const v: Record<string, string> = {};
    if (!form.customerId) v.customerId = 'Choose a customer';
    if (!form.warehouseId) v.warehouseId = 'Choose a warehouse';
    const { lineErrors: le, message } = validateLines(lines);
    setErrors(v);
    setLineErrors(le);
    if (Object.keys(v).length || Object.keys(le).length) return;
    if (message) { toast.error(message); return; }

    const body = {
      customerId: Number(form.customerId), warehouseId: Number(form.warehouseId),
      shippingAddress: form.shippingAddress || undefined, notes: form.notes || undefined,
      items: lines.map((l) => ({ productId: l.product!.id, quantity: Number(l.quantity) })), confirm,
    };
    setSaving(confirm ? 'confirm' : 'draft');
    try {
      const so = editingId ? await salesOrdersApi.update(editingId, body) : await salesOrdersApi.create(body);
      toast.success(confirm ? `${so.orderNumber} confirmed and stock reserved` : `${so.orderNumber} saved as draft`);
      navigate(`/sales-orders/${so.id}`);
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setSaving(null);
    }
  };

  if (loadError) return <div className="card"><ErrorState message={loadError} /></div>;
  if (!loaded) return <LoadingBlock />;
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value });

  return (
    <>
      <PageHeader title={editingId ? 'Edit sales order' : 'New sales order'} back={{ to: '/sales-orders', label: 'Sales orders' }}
                  description="Prices come from the product catalogue and totals are calculated by the server." />
      <form onSubmit={(e) => submit(false, e)} noValidate className="space-y-6">
        <section className="card grid gap-4 p-5 md:grid-cols-2">
          <SelectInput label="Customer" value={form.customerId} onChange={set('customerId')} error={errors.customerId} required>
            <option value="">Select…</option>
            {customers.data?.content.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </SelectInput>
          <SelectInput label="Ship from warehouse" value={form.warehouseId} onChange={set('warehouseId')} error={errors.warehouseId} required>
            <option value="">Select…</option>
            {warehouses.data?.filter((w) => w.active).map((w) => <option key={w.id} value={w.id}>{w.code} — {w.name}</option>)}
          </SelectInput>
          <TextArea label="Shipping address" value={form.shippingAddress} onChange={set('shippingAddress')} maxLength={500} rows={2}
                    hint="Leave blank to use the customer's address" />
          <TextArea label="Notes" value={form.notes} onChange={set('notes')} maxLength={1000} rows={2} />
        </section>
        <section className="card p-5">
          <h2 className="mb-4 text-sm font-semibold">Line items</h2>
          <LineItemsEditor lines={lines} onChange={setLines} mode="sales" errors={lineErrors}
                           availability={form.warehouseId ? availability : undefined} />
        </section>
        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="secondary" onClick={() => navigate(-1)}>Cancel</Button>
          <Button type="submit" variant="secondary" loading={saving === 'draft'} disabled={!!saving}>Save draft</Button>
          <Button onClick={() => submit(true)} loading={saving === 'confirm'} disabled={!!saving}>Save &amp; confirm</Button>
        </div>
      </form>
    </>
  );
}
