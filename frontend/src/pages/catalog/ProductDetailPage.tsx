import { Archive, ArchiveRestore, Pencil } from 'lucide-react';
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { categoriesApi, productsApi } from '../../api/endpoints';
import type { Movement } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { can } from '../../auth/permissions';
import { MovementBadge, StatusBadge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { ConfirmDialog } from '../../components/ui/Modal';
import { PageHeader } from '../../components/ui/PageHeader';
import { Pagination } from '../../components/ui/Pagination';
import { ErrorState, LoadingBlock } from '../../components/ui/States';
import { useToast } from '../../components/ui/Toast';
import { useApi } from '../../hooks/useApi';
import { errorMessage } from '../../lib/errors';
import { formatDateTime, formatMoney, formatNumber } from '../../lib/format';
import { ProductFormModal } from './ProductFormModal';

export const movementColumns: Column<Movement>[] = [
  { key: 'date', header: 'Date', render: (m) => formatDateTime(m.createdAt) },
  { key: 'type', header: 'Type', render: (m) => <MovementBadge type={m.type} /> },
  { key: 'warehouse', header: 'Warehouse', render: (m) => m.warehouseCode },
  { key: 'qty', header: 'Qty', align: 'right', render: (m) => (
    <span className={m.quantity > 0 ? 'font-medium text-emerald-600' : 'font-medium text-rose-600'}>{m.quantity > 0 ? '+' : ''}{m.quantity}</span>) },
  { key: 'balance', header: 'Balance', align: 'right', render: (m) => formatNumber(m.balanceAfter) },
  { key: 'reason', header: 'Reason / reference', render: (m) => (
    <span className="block max-w-xs truncate text-slate-500" title={m.reason ?? undefined}>
      {m.reason ?? (m.referenceType ? `${m.referenceType.replace(/_/g, ' ').toLowerCase()} #${m.referenceId}` : '—')}
    </span>) },
  { key: 'by', header: 'By', render: (m) => m.performedBy ?? 'System' },
];

export function ProductDetailPage() {
  const id = Number(useParams().id);
  const { user } = useAuth();
  const toast = useToast();
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState(false);
  const [confirmArchive, setConfirmArchive] = useState(false);
  const [busy, setBusy] = useState(false);

  const detail = useApi(() => productsApi.get(id), [id]);
  const movements = useApi(() => productsApi.movements(id, { page, size: 10 }), [id, page]);
  const categories = useApi(() => categoriesApi.list(), []);

  if (detail.error) return <div className="card"><ErrorState message={detail.error} onRetry={detail.reload} /></div>;
  if (!detail.data) return <LoadingBlock />;
  const { product, stock, suppliers, stockValue } = detail.data;
  const canEdit = can.manageInventory(user?.role);

  const toggleArchive = async () => {
    setBusy(true);
    try {
      if (product.status === 'ACTIVE') await productsApi.archive(product.id); else await productsApi.restore(product.id);
      toast.success(product.status === 'ACTIVE' ? 'Product archived' : 'Product restored');
      setConfirmArchive(false);
      detail.reload();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader title={product.name} back={{ to: '/products', label: 'Products' }}
                  description={<span className="flex flex-wrap items-center gap-2"><span className="font-mono">{product.sku}</span> · {product.categoryName}
                    <StatusBadge status={product.status} /><StatusBadge status={product.stockStatus} /></span>}
                  actions={canEdit && <>
                    <Button variant="secondary" icon={<Pencil className="h-4 w-4" />} onClick={() => setEditing(true)}>Edit</Button>
                    <Button variant="secondary" icon={product.status === 'ACTIVE' ? <Archive className="h-4 w-4" /> : <ArchiveRestore className="h-4 w-4" />}
                            onClick={() => setConfirmArchive(true)}>{product.status === 'ACTIVE' ? 'Archive' : 'Restore'}</Button>
                    <Link to={`/inventory/operations?productId=${product.id}`}><Button>Adjust stock</Button></Link>
                  </>} />

      <div className="grid gap-6 lg:grid-cols-3">
        <section className="card p-5 lg:col-span-1">
          {product.imageUrl && <img src={product.imageUrl} alt={product.name} referrerPolicy="no-referrer" className="mb-4 aspect-video w-full rounded-lg object-cover" />}
          <dl className="grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
            <dt className="text-slate-500">Cost price</dt><dd className="text-right font-medium">{formatMoney(product.costPrice)}</dd>
            <dt className="text-slate-500">Selling price</dt><dd className="text-right font-medium">{formatMoney(product.unitPrice)}</dd>
            <dt className="text-slate-500">On hand</dt><dd className="text-right font-medium">{formatNumber(product.quantityOnHand)}</dd>
            <dt className="text-slate-500">Reserved</dt><dd className="text-right font-medium">{formatNumber(product.quantityReserved)}</dd>
            <dt className="text-slate-500">Available</dt><dd className="text-right font-medium">{formatNumber(product.quantityAvailable)}</dd>
            <dt className="text-slate-500">Reorder threshold</dt><dd className="text-right font-medium">{formatNumber(product.reorderThreshold)}</dd>
            <dt className="text-slate-500">Stock value</dt><dd className="text-right font-semibold">{formatMoney(stockValue)}</dd>
          </dl>
          {product.description && <p className="mt-4 border-t border-slate-200 pt-4 text-sm text-slate-600 dark:border-slate-800 dark:text-slate-400">{product.description}</p>}
        </section>

        <div className="space-y-6 lg:col-span-2">
          <section className="card overflow-hidden">
            <h2 className="border-b border-slate-200 px-5 py-3 text-sm font-semibold dark:border-slate-800">Stock by warehouse</h2>
            {stock.length === 0 ? <p className="px-5 py-6 text-sm text-slate-500">No stock recorded in any warehouse yet.</p> : (
              <table className="min-w-full text-sm">
                <thead className="table-head"><tr><th className="px-5 py-2">Warehouse</th><th className="px-5 py-2 text-right">On hand</th><th className="px-5 py-2 text-right">Reserved</th><th className="px-5 py-2 text-right">Available</th></tr></thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {stock.map((s) => (
                    <tr key={s.warehouseId}><td className="px-5 py-2.5"><span className="font-medium">{s.warehouseCode}</span> <span className="text-slate-500">{s.warehouseName}</span></td>
                      <td className="px-5 py-2.5 text-right tabular-nums">{formatNumber(s.onHand)}</td>
                      <td className="px-5 py-2.5 text-right tabular-nums">{formatNumber(s.reserved)}</td>
                      <td className="px-5 py-2.5 text-right font-medium tabular-nums">{formatNumber(s.available)}</td></tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
          <section className="card overflow-hidden">
            <h2 className="border-b border-slate-200 px-5 py-3 text-sm font-semibold dark:border-slate-800">Suppliers</h2>
            {suppliers.length === 0 ? <p className="px-5 py-6 text-sm text-slate-500">No supplier offers this product yet.</p> : (
              <ul className="divide-y divide-slate-100 text-sm dark:divide-slate-800">
                {suppliers.map((s) => (
                  <li key={s.supplierId} className="flex items-center justify-between px-5 py-2.5">
                    <Link to={`/suppliers/${s.supplierId}`} className="font-medium hover:text-brand-600">{s.supplierName}</Link>
                    <span className="text-slate-500">{formatMoney(s.unitCost)} · {s.leadTimeDays} day lead time</span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      </div>

      <h2 className="mb-3 mt-8 text-lg font-semibold">Movement history</h2>
      <DataTable columns={movementColumns} rows={movements.data?.content} rowKey={(m) => m.id} loading={movements.loading}
                 error={movements.error} onRetry={movements.reload} emptyTitle="No stock movements yet"
                 footer={movements.data && <Pagination page={movements.data.page} size={movements.data.size}
                   totalElements={movements.data.totalElements} totalPages={movements.data.totalPages} onPage={setPage} />} />

      <ProductFormModal open={editing} product={product} categories={categories.data ?? []} onClose={() => setEditing(false)}
                        onSaved={() => { setEditing(false); detail.reload(); }} />
      <ConfirmDialog open={confirmArchive} loading={busy} danger={product.status === 'ACTIVE'}
                     title={product.status === 'ACTIVE' ? 'Archive product?' : 'Restore product?'}
                     confirmLabel={product.status === 'ACTIVE' ? 'Archive' : 'Restore'}
                     message={product.status === 'ACTIVE'
                       ? `${product.sku} will be hidden from the active catalog and cannot be added to new orders. Stock history is kept.`
                       : `${product.sku} will be available for new orders again.`}
                     onConfirm={toggleArchive} onCancel={() => setConfirmArchive(false)} />
    </>
  );
}
