import { useState } from 'react';
import { Link } from 'react-router-dom';
import { salesOrdersApi } from '../../api/endpoints';
import type { Shipment, ShipmentStatus } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { can } from '../../auth/permissions';
import { StatusBadge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { FilterSelect } from '../../components/ui/Field';
import { FilterBar, PageHeader } from '../../components/ui/PageHeader';
import { Pagination } from '../../components/ui/Pagination';
import { useToast } from '../../components/ui/Toast';
import { useApi } from '../../hooks/useApi';
import { useTableState } from '../../hooks/useTableState';
import { errorMessage } from '../../lib/errors';
import { formatDateTime, humanize } from '../../lib/format';

const NEXT: Partial<Record<ShipmentStatus, ShipmentStatus>> = { IN_TRANSIT: 'OUT_FOR_DELIVERY', OUT_FOR_DELIVERY: 'DELIVERED' };

export function ShipmentsPage() {
  const { user } = useAuth();
  const toast = useToast();
  const [status, setStatus] = useState('');
  const table = useTableState({ key: 'shippedAt', direction: 'desc' });
  const shipments = useApi(() => salesOrdersApi.shipments({ status, page: table.page, size: table.size, sort: table.sortParam }),
    [status, table.page, table.sortParam]);
  const [busy, setBusy] = useState<number | null>(null);

  const advance = async (s: Shipment) => {
    const next = NEXT[s.status];
    if (!next) return;
    setBusy(s.id);
    try {
      await salesOrdersApi.updateShipment(s.id, next);
      toast.success(`${s.shipmentNumber} marked ${humanize(next).toLowerCase()}`);
      shipments.reload();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setBusy(null);
    }
  };

  const columns: Column<Shipment>[] = [
    { key: 'number', header: 'Shipment', render: (s) => <span className="font-mono text-xs font-semibold">{s.shipmentNumber}</span> },
    { key: 'order', header: 'Order', render: (s) => <Link to={`/sales-orders/${s.salesOrderId}`} className="font-mono text-xs font-medium text-brand-600 hover:underline">{s.orderNumber}</Link> },
    { key: 'customer', header: 'Customer', render: (s) => s.customerName },
    { key: 'carrier', header: 'Carrier', render: (s) => <span>{s.carrier}<span className="block text-xs text-slate-500">{s.trackingNumber ?? ''}</span></span> },
    { key: 'shipped', header: 'Shipped', sortKey: 'shippedAt', render: (s) => formatDateTime(s.shippedAt) },
    { key: 'delivered', header: 'Delivered', render: (s) => formatDateTime(s.deliveredAt) },
    { key: 'status', header: 'Status', render: (s) => <StatusBadge status={s.status} /> },
    ...(can.manageSales(user?.role) ? [{ key: 'action', header: '', align: 'right' as const, render: (s: Shipment) => NEXT[s.status] && (
      <Button size="sm" variant="secondary" loading={busy === s.id} onClick={() => advance(s)}>Mark {humanize(NEXT[s.status]).toLowerCase()}</Button>) }] : []),
  ];

  return (
    <>
      <PageHeader title="Shipments" description="Track outbound deliveries. Marking a shipment delivered completes its sales order." />
      <FilterBar>
        <FilterSelect label="Status" value={status} onChange={(v) => { setStatus(v); table.setPage(0); }}>
          <option value="">All statuses</option>
          {(['IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED'] as const).map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
        </FilterSelect>
      </FilterBar>
      <DataTable columns={columns} rows={shipments.data?.content} rowKey={(s) => s.id} loading={shipments.loading}
                 error={shipments.error} onRetry={shipments.reload} sort={table.sort} onSort={table.setSort} emptyTitle="No shipments"
                 footer={shipments.data && <Pagination page={shipments.data.page} size={shipments.data.size}
                   totalElements={shipments.data.totalElements} totalPages={shipments.data.totalPages} onPage={table.setPage} />} />
    </>
  );
}
