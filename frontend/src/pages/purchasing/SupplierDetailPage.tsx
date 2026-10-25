import { Pencil, Plus, Trash2 } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { purchaseOrdersApi, suppliersApi } from '../../api/endpoints';
import type { Product, PurchaseOrderSummary, SupplierProduct } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { can } from '../../auth/permissions';
import { StatusBadge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { TextInput } from '../../components/ui/Field';
import { ConfirmDialog, Modal } from '../../components/ui/Modal';
import { PageHeader } from '../../components/ui/PageHeader';
import { Pagination } from '../../components/ui/Pagination';
import { ProductPicker } from '../../components/ui/ProductPicker';
import { ErrorState, LoadingBlock } from '../../components/ui/States';
import { useToast } from '../../components/ui/Toast';
import { useApi } from '../../hooks/useApi';
import { errorMessage } from '../../lib/errors';
import { formatDate, formatMoney, formatNumber } from '../../lib/format';
import { SupplierFormModal } from './SupplierFormModal';

export function SupplierDetailPage() {
  const id = Number(useParams().id);
  const { user } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const canEdit = can.manageProcurement(user?.role);
  const supplier = useApi(() => suppliersApi.get(id), [id]);
  const catalogue = useApi(() => suppliersApi.products(id), [id]);
  const [page, setPage] = useState(0);
  const history = useApi(() => purchaseOrdersApi.list({ supplierId: id, page, size: 10 }), [id, page]);
  const [editing, setEditing] = useState(false);
  const [offer, setOffer] = useState<{ product: Product | null; unitCost: string; leadTimeDays: string; supplierSku: string } | null>(null);
  const [removing, setRemoving] = useState<SupplierProduct | null>(null);
  const [saving, setSaving] = useState(false);

  if (supplier.error) return <div className="card"><ErrorState message={supplier.error} onRetry={supplier.reload} /></div>;
  if (!supplier.data) return <LoadingBlock />;
  const s = supplier.data;

  const saveOffer = async (e: FormEvent) => {
    e.preventDefault();
    if (!offer?.product || offer.unitCost === '' || Number(offer.unitCost) < 0) {
      toast.error('Choose a product and enter a unit cost of 0 or more');
      return;
    }
    setSaving(true);
    try {
      await suppliersApi.saveProduct(id, { productId: offer.product.id, unitCost: Number(offer.unitCost),
        leadTimeDays: Number(offer.leadTimeDays || 0), supplierSku: offer.supplierSku || undefined });
      toast.success('Catalogue updated');
      setOffer(null);
      catalogue.reload();
      supplier.reload();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (!removing) return;
    setSaving(true);
    try {
      await suppliersApi.removeProduct(id, removing.productId);
      toast.success('Removed from catalogue');
      setRemoving(null);
      catalogue.reload();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const catalogueColumns: Column<SupplierProduct>[] = [
    { key: 'product', header: 'Product', render: (p) => <Link to={`/products/${p.productId}`} className="hover:text-brand-600"><span className="font-medium">{p.productName}</span> <span className="font-mono text-xs text-slate-500">{p.sku}</span></Link> },
    { key: 'vsku', header: 'Supplier SKU', render: (p) => p.supplierSku ?? '—' },
    { key: 'cost', header: 'Unit cost', align: 'right', render: (p) => formatMoney(p.unitCost) },
    { key: 'lead', header: 'Lead time', align: 'right', render: (p) => `${p.leadTimeDays} days` },
    ...(canEdit ? [{ key: 'actions', header: '', align: 'right' as const, render: (p: SupplierProduct) => (
      <div className="flex justify-end gap-1">
        <Button size="sm" variant="ghost" aria-label="Edit offer" onClick={() => setOffer({ product: { id: p.productId, sku: p.sku, name: p.productName } as Product,
          unitCost: String(p.unitCost), leadTimeDays: String(p.leadTimeDays), supplierSku: p.supplierSku ?? '' })}><Pencil className="h-4 w-4" /></Button>
        <Button size="sm" variant="ghost" aria-label="Remove offer" onClick={() => setRemoving(p)}><Trash2 className="h-4 w-4 text-rose-500" /></Button>
      </div>) }] : []),
  ];

  const historyColumns: Column<PurchaseOrderSummary>[] = [
    { key: 'po', header: 'PO', render: (o) => <span className="font-mono text-xs font-medium">{o.poNumber}</span> },
    { key: 'date', header: 'Ordered', render: (o) => formatDate(o.orderDate) },
    { key: 'wh', header: 'Warehouse', render: (o) => o.warehouseCode },
    { key: 'status', header: 'Status', render: (o) => <StatusBadge status={o.status} /> },
    { key: 'total', header: 'Total', align: 'right', render: (o) => formatMoney(o.totalAmount) },
  ];

  return (
    <>
      <PageHeader title={s.name} back={{ to: '/suppliers', label: 'Suppliers' }}
                  description={<span className="flex items-center gap-2"><span className="font-mono">{s.code}</span><StatusBadge status={s.active ? 'ACTIVE' : 'INACTIVE'} /></span>}
                  actions={canEdit && <>
                    <Button variant="secondary" icon={<Pencil className="h-4 w-4" />} onClick={() => setEditing(true)}>Edit</Button>
                    <Link to={`/purchase-orders/new?supplierId=${s.id}`}><Button icon={<Plus className="h-4 w-4" />}>New purchase order</Button></Link>
                  </>} />
      <div className="mb-6 grid gap-4 md:grid-cols-4">
        <div className="card p-4 md:col-span-2">
          <p className="text-sm text-slate-500">Contact</p>
          <p className="mt-1 font-medium">{s.contactName ?? '—'}</p>
          <p className="text-sm text-slate-500">{[s.email, s.phone].filter(Boolean).join(' · ') || 'No contact details'}</p>
          <p className="text-sm text-slate-500">{s.address}</p>
        </div>
        <div className="card p-4"><p className="text-sm text-slate-500">Open purchase orders</p><p className="mt-1 text-2xl font-semibold">{formatNumber(s.openOrders)}</p></div>
        <div className="card p-4"><p className="text-sm text-slate-500">Value received to date</p><p className="mt-1 text-2xl font-semibold">{formatMoney(s.totalSpend)}</p></div>
      </div>

      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-lg font-semibold">Catalogue</h2>
        {canEdit && <Button size="sm" variant="secondary" icon={<Plus className="h-4 w-4" />}
                            onClick={() => setOffer({ product: null, unitCost: '', leadTimeDays: '7', supplierSku: '' })}>Add product</Button>}
      </div>
      <DataTable columns={catalogueColumns} rows={catalogue.data} rowKey={(p) => p.id} loading={catalogue.loading}
                 error={catalogue.error} onRetry={catalogue.reload} emptyTitle="No products in this supplier's catalogue" />

      <h2 className="mb-3 mt-8 text-lg font-semibold">Purchase history</h2>
      <DataTable columns={historyColumns} rows={history.data?.content} rowKey={(o) => o.id} loading={history.loading}
                 error={history.error} onRetry={history.reload} onRowClick={(o) => navigate(`/purchase-orders/${o.id}`)}
                 emptyTitle="No purchase orders yet"
                 footer={history.data && <Pagination page={history.data.page} size={history.data.size}
                   totalElements={history.data.totalElements} totalPages={history.data.totalPages} onPage={setPage} />} />

      <SupplierFormModal open={editing} supplier={s} onClose={() => setEditing(false)} onSaved={() => { setEditing(false); supplier.reload(); }} />
      <Modal open={!!offer} onClose={() => setOffer(null)} title="Supplier product"
             footer={<><Button variant="secondary" onClick={() => setOffer(null)}>Cancel</Button><Button type="submit" form="offer-form" loading={saving}>Save</Button></>}>
        {offer && (
          <form id="offer-form" onSubmit={saveOffer} className="space-y-4">
            <ProductPicker label="Product" value={offer.product} onChange={(p) => setOffer({ ...offer, product: p })} required />
            <div className="grid gap-4 sm:grid-cols-3">
              <TextInput label="Unit cost" type="number" min="0" step="0.01" value={offer.unitCost} onChange={(e) => setOffer({ ...offer, unitCost: e.target.value })} required />
              <TextInput label="Lead time (days)" type="number" min="0" max="365" value={offer.leadTimeDays} onChange={(e) => setOffer({ ...offer, leadTimeDays: e.target.value })} />
              <TextInput label="Supplier SKU" value={offer.supplierSku} maxLength={60} onChange={(e) => setOffer({ ...offer, supplierSku: e.target.value })} />
            </div>
          </form>
        )}
      </Modal>
      <ConfirmDialog open={!!removing} danger loading={saving} title="Remove from catalogue?" confirmLabel="Remove"
                     message={`${removing?.productName} will no longer be listed for ${s.name}. Existing purchase orders are not affected.`}
                     onConfirm={remove} onCancel={() => setRemoving(null)} />
    </>
  );
}
