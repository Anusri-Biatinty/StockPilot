import clsx from 'clsx';
import { ArrowDownToLine, ArrowLeftRight, ArrowUpFromLine, SlidersHorizontal } from 'lucide-react';
import { useEffect, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { inventoryApi, productsApi, warehousesApi } from '../../api/endpoints';
import type { Product, Transfer } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { can } from '../../auth/permissions';
import { Button } from '../../components/ui/Button';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { SelectInput, TextArea, TextInput } from '../../components/ui/Field';
import { PageHeader } from '../../components/ui/PageHeader';
import { Pagination } from '../../components/ui/Pagination';
import { ProductPicker } from '../../components/ui/ProductPicker';
import { useToast } from '../../components/ui/Toast';
import { useApi } from '../../hooks/useApi';
import { errorMessage } from '../../lib/errors';
import { formatDateTime, formatNumber } from '../../lib/format';

type Mode = 'transfer' | 'adjust' | 'receive' | 'issue';

const MODES: { key: Mode; label: string; icon: typeof ArrowLeftRight; help: string }[] = [
  { key: 'transfer', label: 'Transfer', icon: ArrowLeftRight, help: 'Move unreserved stock between warehouses. Creates a paired out/in movement.' },
  { key: 'adjust', label: 'Adjust', icon: SlidersHorizontal, help: 'Correct stock after a count. Use a negative number to remove units. A reason is mandatory.' },
  { key: 'receive', label: 'Receive', icon: ArrowDownToLine, help: 'Book goods in that did not come through a purchase order (returns, found stock).' },
  { key: 'issue', label: 'Issue', icon: ArrowUpFromLine, help: 'Book goods out for internal use, samples or write-offs. A reason is mandatory.' },
];

export function StockOperationsPage() {
  const { user } = useAuth();
  const toast = useToast();
  const [params] = useSearchParams();
  const [mode, setMode] = useState<Mode>('transfer');
  const [product, setProduct] = useState<Product | null>(null);
  const [form, setForm] = useState({ warehouseId: '', toWarehouseId: '', quantity: '', reason: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [transferPage, setTransferPage] = useState(0);
  const allowed = can.manageInventory(user?.role);

  const warehouses = useApi(() => warehousesApi.list(), []);
  const transfers = useApi(() => inventoryApi.transfers({ page: transferPage, size: 10 }), [transferPage]);
  const [stockInfo, setStockInfo] = useState<Record<number, { onHand: number; available: number }>>({});

  useEffect(() => {
    const id = Number(params.get('productId'));
    if (id) productsApi.get(id).then((d) => setProduct(d.product)).catch(() => undefined);
  }, [params]);

  useEffect(() => {
    if (!product) { setStockInfo({}); return; }
    productsApi.get(product.id).then((d) => setStockInfo(Object.fromEntries(
      d.stock.map((s) => [s.warehouseId, { onHand: s.onHand, available: s.available }])))).catch(() => undefined);
  }, [product, transfers.data]);

  const active = warehouses.data?.filter((w) => w.active) ?? [];
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [k]: e.target.value }));
  const label = (id: number, code: string) => {
    const s = stockInfo[id];
    return product ? `${code} (${formatNumber(s?.available ?? 0)} available)` : code;
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const v: Record<string, string> = {};
    const qty = Number(form.quantity);
    if (!product) v.product = 'Choose a product';
    if (!form.warehouseId) v.warehouseId = 'Choose a warehouse';
    if (mode === 'transfer' && !form.toWarehouseId) v.toWarehouseId = 'Choose a destination';
    if (mode === 'transfer' && form.toWarehouseId && form.toWarehouseId === form.warehouseId) v.toWarehouseId = 'Must differ from source';
    if (!Number.isInteger(qty) || qty === 0 || (mode !== 'adjust' && qty < 0)) {
      v.quantity = mode === 'adjust' ? 'Enter a non-zero whole number' : 'Enter a whole number above zero';
    }
    if ((mode === 'adjust' || mode === 'issue') && !form.reason.trim()) v.reason = 'A reason is required';
    setErrors(v);
    if (Object.keys(v).length || !product) return;

    setSaving(true);
    try {
      const productId = product.id;
      const warehouseId = Number(form.warehouseId);
      if (mode === 'transfer') {
        const t = await inventoryApi.transfer({ productId, fromWarehouseId: warehouseId, toWarehouseId: Number(form.toWarehouseId), quantity: qty, notes: form.reason || undefined });
        toast.success(`Transfer ${t.transferNumber} completed`);
      } else if (mode === 'adjust') {
        await inventoryApi.adjust({ productId, warehouseId, quantityChange: qty, reason: form.reason.trim() });
        toast.success('Stock adjusted');
      } else if (mode === 'receive') {
        await inventoryApi.receive({ productId, warehouseId, quantity: qty, reason: form.reason || undefined });
        toast.success('Stock received');
      } else {
        await inventoryApi.issue({ productId, warehouseId, quantity: qty, reason: form.reason.trim() });
        toast.success('Stock issued');
      }
      setForm({ warehouseId: form.warehouseId, toWarehouseId: '', quantity: '', reason: '' });
      transfers.reload();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const transferColumns: Column<Transfer>[] = [
    { key: 'number', header: 'Transfer', render: (t) => <span className="font-mono text-xs font-medium">{t.transferNumber}</span> },
    { key: 'date', header: 'Date', render: (t) => formatDateTime(t.createdAt) },
    { key: 'product', header: 'Product', render: (t) => <span><span className="font-medium">{t.productName}</span> <span className="text-xs text-slate-500">{t.sku}</span></span> },
    { key: 'route', header: 'Route', render: (t) => `${t.fromWarehouseCode} → ${t.toWarehouseCode}` },
    { key: 'qty', header: 'Qty', align: 'right', render: (t) => formatNumber(t.quantity) },
    { key: 'by', header: 'By', render: (t) => t.createdBy ?? '—' },
  ];

  const current = MODES.find((m) => m.key === mode)!;
  return (
    <>
      <PageHeader title="Transfers & adjustments" description="Every operation is validated against available stock and recorded in the movement history." />
      {allowed ? (
        <section className="card mb-8 p-5">
          <div role="tablist" aria-label="Operation" className="mb-5 flex flex-wrap gap-2">
            {MODES.map((m) => (
              <button key={m.key} role="tab" aria-selected={mode === m.key} onClick={() => { setMode(m.key); setErrors({}); }}
                      className={clsx('inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium',
                        mode === m.key ? 'bg-brand-600 text-white' : 'bg-slate-100 text-slate-700 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300')}>
                <m.icon className="h-4 w-4" /> {m.label}
              </button>
            ))}
          </div>
          <p className="mb-4 text-sm text-slate-500">{current.help}</p>
          <form onSubmit={submit} noValidate className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            <div className="lg:col-span-2"><ProductPicker label="Product" value={product} onChange={setProduct} error={errors.product} required /></div>
            <SelectInput label={mode === 'transfer' ? 'From warehouse' : 'Warehouse'} value={form.warehouseId} onChange={set('warehouseId')} error={errors.warehouseId} required>
              <option value="">Select…</option>
              {active.map((w) => <option key={w.id} value={w.id}>{label(w.id, w.code)}</option>)}
            </SelectInput>
            {mode === 'transfer' ? (
              <SelectInput label="To warehouse" value={form.toWarehouseId} onChange={set('toWarehouseId')} error={errors.toWarehouseId} required>
                <option value="">Select…</option>
                {active.filter((w) => String(w.id) !== form.warehouseId).map((w) => <option key={w.id} value={w.id}>{w.code}</option>)}
              </SelectInput>
            ) : <div className="hidden lg:block" />}
            <TextInput label={mode === 'adjust' ? 'Quantity change (+/-)' : 'Quantity'} type="number" step="1" value={form.quantity}
                       onChange={set('quantity')} error={errors.quantity} required />
            <TextArea className="md:col-span-1 lg:col-span-2" label={mode === 'transfer' ? 'Notes' : 'Reason'} value={form.reason}
                      onChange={set('reason')} error={errors.reason} required={mode === 'adjust' || mode === 'issue'} maxLength={500} rows={2} />
            <div className="flex items-end"><Button type="submit" loading={saving} className="w-full">{current.label} stock</Button></div>
          </form>
        </section>
      ) : (
        <p className="card mb-8 p-5 text-sm text-slate-500">Your role can view transfer history but cannot change stock.</p>
      )}

      <h2 className="mb-3 text-lg font-semibold">Recent transfers</h2>
      <DataTable columns={transferColumns} rows={transfers.data?.content} rowKey={(t) => t.id} loading={transfers.loading}
                 error={transfers.error} onRetry={transfers.reload} emptyTitle="No transfers yet"
                 footer={transfers.data && <Pagination page={transfers.data.page} size={transfers.data.size}
                   totalElements={transfers.data.totalElements} totalPages={transfers.data.totalPages} onPage={setTransferPage} />} />
    </>
  );
}
