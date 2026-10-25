import { CheckCircle2, Pencil, Truck, XCircle } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { salesOrdersApi } from '../../api/endpoints';
import type { ShipmentStatus } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { can } from '../../auth/permissions';
import { StatusBadge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { TextInput } from '../../components/ui/Field';
import { ConfirmDialog, Modal } from '../../components/ui/Modal';
import { PageHeader } from '../../components/ui/PageHeader';
import { ErrorState, LoadingBlock } from '../../components/ui/States';
import { useToast } from '../../components/ui/Toast';
import { useApi } from '../../hooks/useApi';
import { errorMessage } from '../../lib/errors';
import { formatDate, formatDateTime, formatMoney, formatNumber, humanize } from '../../lib/format';

const TRACKING: ShipmentStatus[] = ['IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED'];

export function SalesOrderDetailPage() {
  const id = Number(useParams().id);
  const { user } = useAuth();
  const toast = useToast();
  const { data: so, error, reload } = useApi(() => salesOrdersApi.get(id), [id]);
  const [action, setAction] = useState<'confirm' | 'cancel' | null>(null);
  const [shipping, setShipping] = useState(false);
  const [carrier, setCarrier] = useState('');
  const [tracking, setTracking] = useState('');
  const [busy, setBusy] = useState(false);

  if (error) return <div className="card"><ErrorState message={error} onRetry={reload} /></div>;
  if (!so) return <LoadingBlock />;
  const canWrite = can.manageSales(user?.role);

  const run = async (fn: () => Promise<unknown>, message: string) => {
    setBusy(true);
    try {
      await fn();
      toast.success(message);
      setAction(null);
      setShipping(false);
      reload();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const ship = (e: FormEvent) => {
    e.preventDefault();
    if (!carrier.trim()) { toast.error('Carrier is required'); return; }
    void run(() => salesOrdersApi.ship(so.id, carrier.trim(), tracking.trim()), 'Order shipped and stock issued');
  };

  const shortages = so.status === 'DRAFT' ? so.items.filter((i) => i.quantity > i.availableInWarehouse) : [];
  const timeline: [string, string | null][] = [
    ['Created', so.createdAt], ['Confirmed', so.confirmedAt], ['Shipped', so.shippedAt], ['Delivered', so.deliveredAt],
    ...(so.cancelledAt ? [['Cancelled', so.cancelledAt] as [string, string]] : []),
  ];

  return (
    <>
      <PageHeader title={so.orderNumber} back={{ to: '/sales-orders', label: 'Sales orders' }}
                  description={<span className="flex flex-wrap items-center gap-2">{so.customerName} · ships from {so.warehouseCode}<StatusBadge status={so.status} /></span>}
                  actions={canWrite && <>
                    {so.status === 'DRAFT' && <>
                      <Link to={`/sales-orders/${so.id}/edit`}><Button variant="secondary" icon={<Pencil className="h-4 w-4" />}>Edit</Button></Link>
                      <Button icon={<CheckCircle2 className="h-4 w-4" />} onClick={() => setAction('confirm')}>Confirm & reserve stock</Button>
                    </>}
                    {so.status === 'CONFIRMED' && <Button icon={<Truck className="h-4 w-4" />} onClick={() => { setCarrier(''); setTracking(''); setShipping(true); }}>Ship order</Button>}
                    {(so.status === 'DRAFT' || so.status === 'CONFIRMED') &&
                      <Button variant="ghost" icon={<XCircle className="h-4 w-4" />} onClick={() => setAction('cancel')}>Cancel order</Button>}
                  </>} />

      {shortages.length > 0 && (
        <p role="alert" className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:border-amber-900 dark:bg-amber-500/10 dark:text-amber-300">
          Not enough stock in {so.warehouseCode} to confirm: {shortages.map((s) => `${s.sku} (${s.availableInWarehouse} of ${s.quantity})`).join(', ')}.
        </p>
      )}

      <div className="grid gap-6 lg:grid-cols-3">
        <section className="card overflow-hidden lg:col-span-2">
          <h2 className="border-b border-slate-200 px-5 py-3 text-sm font-semibold dark:border-slate-800">Items</h2>
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead className="table-head"><tr>
                <th className="px-4 py-2">Product</th><th className="px-4 py-2 text-right">Qty</th><th className="px-4 py-2 text-right">Unit price</th>
                <th className="px-4 py-2 text-right">Total</th>{so.status === 'DRAFT' && <th className="px-4 py-2 text-right">Available</th>}
              </tr></thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {so.items.map((i) => (
                  <tr key={i.id}>
                    <td className="px-4 py-2.5"><Link to={`/products/${i.productId}`} className="font-medium hover:text-brand-600">{i.productName}</Link>
                      <span className="block font-mono text-xs text-slate-500">{i.sku}</span></td>
                    <td className="px-4 py-2.5 text-right tabular-nums">{formatNumber(i.quantity)}</td>
                    <td className="px-4 py-2.5 text-right tabular-nums">{formatMoney(i.unitPrice)}</td>
                    <td className="px-4 py-2.5 text-right font-medium tabular-nums">{formatMoney(i.lineTotal)}</td>
                    {so.status === 'DRAFT' && <td className={`px-4 py-2.5 text-right tabular-nums ${i.availableInWarehouse < i.quantity ? 'text-rose-600' : ''}`}>{formatNumber(i.availableInWarehouse)}</td>}
                  </tr>
                ))}
              </tbody>
              <tfoot><tr className="border-t border-slate-200 dark:border-slate-800">
                <td colSpan={3} className="px-4 py-3 text-right font-medium">Order total</td>
                <td className="px-4 py-3 text-right text-base font-semibold tabular-nums">{formatMoney(so.totalAmount)}</td>
                {so.status === 'DRAFT' && <td />}</tr></tfoot>
            </table>
          </div>
        </section>

        <div className="space-y-6">
          <section className="card p-5 text-sm">
            <h2 className="mb-3 font-semibold">Order details</h2>
            <dl className="space-y-2">
              <div className="flex justify-between gap-4"><dt className="text-slate-500">Order date</dt><dd>{formatDate(so.orderDate)}</dd></div>
              <div className="flex justify-between gap-4"><dt className="text-slate-500">Created by</dt><dd>{so.createdBy ?? '—'}</dd></div>
              <div className="flex justify-between gap-4"><dt className="text-slate-500">Warehouse</dt><dd>{so.warehouseName}</dd></div>
              <div><dt className="text-slate-500">Ship to</dt><dd className="mt-0.5">{so.shippingAddress ?? '—'}</dd></div>
            </dl>
            {so.notes && <p className="mt-3 border-t border-slate-200 pt-3 text-slate-600 dark:border-slate-800 dark:text-slate-400">{so.notes}</p>}
            <ol className="mt-4 space-y-2 border-t border-slate-200 pt-4 dark:border-slate-800">
              {timeline.map(([label, at]) => (
                <li key={label} className="flex items-center gap-3">
                  <span className={`h-2.5 w-2.5 rounded-full ${at ? (label === 'Cancelled' ? 'bg-rose-500' : 'bg-brand-600') : 'bg-slate-300 dark:bg-slate-700'}`} />
                  <span className={at ? 'font-medium' : 'text-slate-400'}>{label}</span>
                  <span className="ml-auto text-xs text-slate-500">{at ? formatDateTime(at) : ''}</span>
                </li>
              ))}
            </ol>
          </section>

          <section className="card p-5 text-sm">
            <h2 className="mb-3 font-semibold">Shipment tracking</h2>
            {!so.shipment ? <p className="text-slate-500">Not shipped yet.</p> : (
              <>
                <p><span className="font-mono font-semibold">{so.shipment.shipmentNumber}</span> · {so.shipment.carrier}</p>
                <p className="text-slate-500">Tracking: {so.shipment.trackingNumber ?? '—'}</p>
                <ol className="mt-3 flex gap-2" aria-label="Shipment progress">
                  {TRACKING.map((s) => {
                    const reached = TRACKING.indexOf(s) <= TRACKING.indexOf(so.shipment!.status);
                    return <li key={s} className={`flex-1 rounded-md px-2 py-1.5 text-center text-xs font-medium ${reached ? 'bg-violet-600 text-white' : 'bg-slate-100 text-slate-500 dark:bg-slate-800'}`}>{humanize(s)}</li>;
                  })}
                </ol>
                {canWrite && so.shipment.status !== 'DELIVERED' && (
                  <div className="mt-3 flex flex-wrap gap-2">
                    {TRACKING.filter((s) => TRACKING.indexOf(s) > TRACKING.indexOf(so.shipment!.status)).map((s) => (
                      <Button key={s} size="sm" variant={s === 'DELIVERED' ? 'primary' : 'secondary'} loading={busy}
                              onClick={() => run(() => salesOrdersApi.updateShipment(so.shipment!.id, s), `Shipment marked ${humanize(s).toLowerCase()}`)}>
                        Mark {humanize(s).toLowerCase()}
                      </Button>
                    ))}
                  </div>
                )}
              </>
            )}
          </section>
        </div>
      </div>

      <ConfirmDialog open={action === 'confirm'} loading={busy} title="Confirm order?" confirmLabel="Confirm & reserve"
                     message={`Stock for every line is reserved in ${so.warehouseCode}. If any line is short, nothing is reserved and you'll see which product is missing.`}
                     onCancel={() => setAction(null)} onConfirm={() => run(() => salesOrdersApi.confirm(so.id), 'Order confirmed and stock reserved')} />
      <ConfirmDialog open={action === 'cancel'} loading={busy} danger title={`Cancel ${so.orderNumber}?`} confirmLabel="Cancel order"
                     message={so.status === 'CONFIRMED' ? 'Reserved stock will be released back to available inventory.' : 'The draft will be cancelled.'}
                     onCancel={() => setAction(null)} onConfirm={() => run(() => salesOrdersApi.cancel(so.id), 'Order cancelled')} />
      <Modal open={shipping} onClose={() => setShipping(false)} title={`Ship ${so.orderNumber}`}
             footer={<><Button variant="secondary" onClick={() => setShipping(false)}>Cancel</Button><Button type="submit" form="ship-form" loading={busy}>Ship order</Button></>}>
        <form id="ship-form" onSubmit={ship} className="space-y-4">
          <p className="text-sm text-slate-500">Reserved stock is issued from {so.warehouseCode} and a shipment is created.</p>
          <TextInput label="Carrier" value={carrier} onChange={(e) => setCarrier(e.target.value)} required maxLength={80} list="carriers" />
          <datalist id="carriers"><option value="UPS" /><option value="FedEx" /><option value="DHL" /><option value="USPS" /></datalist>
          <TextInput label="Tracking number" value={tracking} onChange={(e) => setTracking(e.target.value)} maxLength={80} />
        </form>
      </Modal>
    </>
  );
}
