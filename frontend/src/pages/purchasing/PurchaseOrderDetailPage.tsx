import { CheckCircle2, Pencil, PackageCheck, Send, XCircle } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { purchaseOrdersApi } from '../../api/endpoints';
import { useAuth } from '../../auth/AuthContext';
import { can } from '../../auth/permissions';
import { StatusBadge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { TextArea } from '../../components/ui/Field';
import { ConfirmDialog, Modal } from '../../components/ui/Modal';
import { PageHeader } from '../../components/ui/PageHeader';
import { ErrorState, LoadingBlock } from '../../components/ui/States';
import { useToast } from '../../components/ui/Toast';
import { useApi } from '../../hooks/useApi';
import { errorMessage } from '../../lib/errors';
import { formatDate, formatDateTime, formatMoney, formatNumber } from '../../lib/format';

type Action = 'submit' | 'approve' | 'cancel' | null;

const STEPS = ['DRAFT', 'SUBMITTED', 'APPROVED', 'PARTIALLY_RECEIVED', 'RECEIVED'] as const;

export function PurchaseOrderDetailPage() {
  const id = Number(useParams().id);
  const { user } = useAuth();
  const toast = useToast();
  const { data: po, error, reload } = useApi(() => purchaseOrdersApi.get(id), [id]);
  const [action, setAction] = useState<Action>(null);
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState('');
  const [receiving, setReceiving] = useState(false);
  const [receipt, setReceipt] = useState<Record<number, string>>({});
  const [receiptNotes, setReceiptNotes] = useState('');
  const [idempotencyKey, setIdempotencyKey] = useState('');
  const [busy, setBusy] = useState(false);

  if (error) return <div className="card"><ErrorState message={error} onRetry={reload} /></div>;
  if (!po) return <LoadingBlock />;

  const canWrite = can.manageProcurement(user?.role);
  const canApprove = can.approvePurchases(user?.role) && (user?.role === 'ADMIN' || po.createdById !== user?.id);

  const run = async (fn: () => Promise<unknown>, message: string) => {
    setBusy(true);
    try {
      await fn();
      toast.success(message);
      setAction(null);
      setRejecting(false);
      setReceiving(false);
      reload();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const openReceive = () => {
    setReceipt(Object.fromEntries(po.items.map((i) => [i.id, String(i.quantityRemaining)])));
    setReceiptNotes('');
    // A fresh key per receipt attempt: retries of the same submission are safe, a new receipt is not blocked.
    setIdempotencyKey(crypto.randomUUID());
    setReceiving(true);
  };

  const submitReceipt = (e: FormEvent) => {
    e.preventDefault();
    const items = po.items
      .map((i) => ({ purchaseOrderItemId: i.id, quantity: Number(receipt[i.id] || 0), max: i.quantityRemaining }))
      .filter((i) => i.quantity > 0);
    if (items.length === 0) { toast.error('Enter a quantity for at least one line'); return; }
    if (items.some((i) => !Number.isInteger(i.quantity) || i.quantity > i.max)) { toast.error('Quantities must be whole numbers within the outstanding amount'); return; }
    void run(() => purchaseOrdersApi.receive(po.id, items.map(({ purchaseOrderItemId, quantity }) => ({ purchaseOrderItemId, quantity })),
      receiptNotes, idempotencyKey), 'Goods received and stock updated');
  };

  const stepIndex = po.status === 'CANCELLED' ? -1 : STEPS.indexOf(po.status);
  const receivable = po.status === 'APPROVED' || po.status === 'PARTIALLY_RECEIVED';

  return (
    <>
      <PageHeader title={po.poNumber} back={{ to: '/purchase-orders', label: 'Purchase orders' }}
                  description={<span className="flex flex-wrap items-center gap-2">
                    <Link to={`/suppliers/${po.supplierId}`} className="hover:text-brand-600">{po.supplierName}</Link> · to {po.warehouseCode}
                    <StatusBadge status={po.status} /></span>}
                  actions={canWrite && <>
                    {po.status === 'DRAFT' && <>
                      <Link to={`/purchase-orders/${po.id}/edit`}><Button variant="secondary" icon={<Pencil className="h-4 w-4" />}>Edit</Button></Link>
                      <Button icon={<Send className="h-4 w-4" />} onClick={() => setAction('submit')}>Submit for approval</Button>
                    </>}
                    {po.status === 'SUBMITTED' && canApprove && <>
                      <Button variant="secondary" onClick={() => { setReason(''); setRejecting(true); }}>Return to draft</Button>
                      <Button icon={<CheckCircle2 className="h-4 w-4" />} onClick={() => setAction('approve')}>Approve</Button>
                    </>}
                    {receivable && <Button icon={<PackageCheck className="h-4 w-4" />} onClick={openReceive}>Receive goods</Button>}
                    {['DRAFT', 'SUBMITTED', 'APPROVED'].includes(po.status) &&
                      <Button variant="ghost" icon={<XCircle className="h-4 w-4" />} onClick={() => setAction('cancel')}>Cancel order</Button>}
                  </>} />

      {po.status === 'SUBMITTED' && can.approvePurchases(user?.role) && !canApprove && (
        <p className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:border-amber-900 dark:bg-amber-500/10 dark:text-amber-300">
          You created this order, so another manager or an admin must approve it.
        </p>
      )}
      {po.rejectionReason && po.status === 'DRAFT' && (
        <p className="mb-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900 dark:bg-rose-500/10 dark:text-rose-300">
          Returned for changes: {po.rejectionReason}
        </p>
      )}

      <ol className="card mb-6 grid grid-cols-2 gap-3 p-4 sm:grid-cols-5" aria-label="Order progress">
        {STEPS.map((s, i) => (
          <li key={s} className={`rounded-lg px-3 py-2 text-xs font-medium ${po.status === 'CANCELLED' ? 'bg-slate-100 text-slate-400 dark:bg-slate-800'
            : i <= stepIndex ? 'bg-brand-600 text-white' : 'bg-slate-100 text-slate-500 dark:bg-slate-800'}`}
              aria-current={i === stepIndex ? 'step' : undefined}>
            {i + 1}. {s.replace('_', ' ').toLowerCase()}
          </li>
        ))}
      </ol>

      <div className="grid gap-6 lg:grid-cols-3">
        <section className="card overflow-hidden lg:col-span-2">
          <h2 className="border-b border-slate-200 px-5 py-3 text-sm font-semibold dark:border-slate-800">Line items</h2>
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead className="table-head"><tr>
                <th className="px-4 py-2">Product</th><th className="px-4 py-2 text-right">Ordered</th><th className="px-4 py-2 text-right">Received</th>
                <th className="px-4 py-2 text-right">Outstanding</th><th className="px-4 py-2 text-right">Unit cost</th><th className="px-4 py-2 text-right">Total</th>
              </tr></thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {po.items.map((i) => (
                  <tr key={i.id}>
                    <td className="px-4 py-2.5"><Link to={`/products/${i.productId}`} className="font-medium hover:text-brand-600">{i.productName}</Link>
                      <span className="block font-mono text-xs text-slate-500">{i.sku}</span></td>
                    <td className="px-4 py-2.5 text-right tabular-nums">{formatNumber(i.quantityOrdered)}</td>
                    <td className="px-4 py-2.5 text-right tabular-nums">{formatNumber(i.quantityReceived)}</td>
                    <td className="px-4 py-2.5 text-right tabular-nums">{formatNumber(i.quantityRemaining)}</td>
                    <td className="px-4 py-2.5 text-right tabular-nums">{formatMoney(i.unitCost)}</td>
                    <td className="px-4 py-2.5 text-right font-medium tabular-nums">{formatMoney(i.lineTotal)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot><tr className="border-t border-slate-200 dark:border-slate-800">
                <td colSpan={5} className="px-4 py-3 text-right font-medium">Order total</td>
                <td className="px-4 py-3 text-right text-base font-semibold tabular-nums">{formatMoney(po.totalAmount)}</td></tr></tfoot>
            </table>
          </div>
        </section>
        <section className="card p-5 text-sm">
          <h2 className="mb-3 font-semibold">Details</h2>
          <dl className="space-y-2">
            {[
              ['Ordered', formatDate(po.orderDate)], ['Expected', formatDate(po.expectedDate)],
              ['Created by', po.createdBy ?? '—'], ['Submitted', formatDateTime(po.submittedAt)],
              ['Approved by', po.approvedBy ? `${po.approvedBy}, ${formatDateTime(po.approvedAt)}` : '—'],
              ['Destination', `${po.warehouseCode} — ${po.warehouseName}`],
              ...(po.cancelledAt ? [['Cancelled', formatDateTime(po.cancelledAt)]] : []),
            ].map(([k, v]) => <div key={k} className="flex justify-between gap-4"><dt className="text-slate-500">{k}</dt><dd className="text-right">{v}</dd></div>)}
          </dl>
          {po.notes && <p className="mt-4 border-t border-slate-200 pt-3 text-slate-600 dark:border-slate-800 dark:text-slate-400">{po.notes}</p>}
        </section>
      </div>

      <h2 className="mb-3 mt-8 text-lg font-semibold">Goods receipts</h2>
      {po.receipts.length === 0 ? <p className="card p-5 text-sm text-slate-500">Nothing received yet.</p> : (
        <div className="space-y-3">
          {po.receipts.map((r) => (
            <div key={r.id} className="card p-4 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p><span className="font-mono font-semibold">{r.receiptNumber}</span> · {formatDateTime(r.receivedAt)} · {r.receivedBy ?? 'System'}</p>
                <p className="font-medium">{formatNumber(r.totalUnits)} units</p>
              </div>
              <p className="mt-1 text-slate-500">{r.items.map((i) => `${i.sku} × ${i.quantityReceived}`).join(', ')}</p>
              {r.notes && <p className="mt-1 text-slate-500">{r.notes}</p>}
            </div>
          ))}
        </div>
      )}

      <ConfirmDialog open={action === 'submit'} loading={busy} title="Submit for approval?" confirmLabel="Submit"
                     message="Approvers will be notified. The order can no longer be edited unless it is returned to draft."
                     onCancel={() => setAction(null)} onConfirm={() => run(() => purchaseOrdersApi.submit(po.id), 'Submitted for approval')} />
      <ConfirmDialog open={action === 'approve'} loading={busy} title={`Approve ${po.poNumber}?`} confirmLabel="Approve"
                     message={`This commits ${formatMoney(po.totalAmount)} with ${po.supplierName}.`}
                     onCancel={() => setAction(null)} onConfirm={() => run(() => purchaseOrdersApi.approve(po.id), 'Purchase order approved')} />
      <ConfirmDialog open={action === 'cancel'} loading={busy} danger title={`Cancel ${po.poNumber}?`} confirmLabel="Cancel order"
                     message="Cancelled orders cannot be reopened. Orders that have already received goods cannot be cancelled."
                     onCancel={() => setAction(null)} onConfirm={() => run(() => purchaseOrdersApi.cancel(po.id), 'Purchase order cancelled')} />

      <Modal open={rejecting} onClose={() => setRejecting(false)} title="Return to draft"
             footer={<><Button variant="secondary" onClick={() => setRejecting(false)}>Cancel</Button>
               <Button variant="danger" loading={busy} disabled={!reason.trim()}
                       onClick={() => run(() => purchaseOrdersApi.reject(po.id, reason.trim()), 'Returned to draft')}>Return to draft</Button></>}>
        <TextArea label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} required
                  hint="The creator is notified with this reason." />
      </Modal>

      <Modal open={receiving} onClose={() => setReceiving(false)} title={`Receive goods — ${po.poNumber}`} size="lg"
             footer={<><Button variant="secondary" onClick={() => setReceiving(false)}>Cancel</Button>
               <Button type="submit" form="receipt-form" loading={busy}>Confirm receipt</Button></>}>
        <form id="receipt-form" onSubmit={submitReceipt} className="space-y-4">
          <p className="text-sm text-slate-500">Enter what physically arrived. Stock in {po.warehouseCode} increases immediately. Leave a line at 0 for a partial delivery.</p>
          <table className="min-w-full text-sm">
            <thead className="table-head"><tr><th className="px-3 py-2">Product</th><th className="px-3 py-2 text-right">Outstanding</th><th className="px-3 py-2 text-right">Receiving now</th></tr></thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
              {po.items.filter((i) => i.quantityRemaining > 0).map((i) => (
                <tr key={i.id}>
                  <td className="px-3 py-2"><span className="font-medium">{i.productName}</span> <span className="text-xs text-slate-500">{i.sku}</span></td>
                  <td className="px-3 py-2 text-right tabular-nums">{i.quantityRemaining}</td>
                  <td className="px-3 py-2 text-right">
                    <input type="number" min="0" max={i.quantityRemaining} step="1" aria-label={`Quantity received for ${i.sku}`}
                           className="input ml-auto w-28 text-right" value={receipt[i.id] ?? ''}
                           onChange={(e) => setReceipt({ ...receipt, [i.id]: e.target.value })} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <TextArea label="Notes (delivery note, carrier…)" value={receiptNotes} onChange={(e) => setReceiptNotes(e.target.value)} maxLength={1000} rows={2} />
        </form>
      </Modal>
    </>
  );
}
