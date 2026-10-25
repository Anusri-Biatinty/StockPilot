import { useState } from 'react';
import { auditApi } from '../../api/endpoints';
import type { AuditLog } from '../../api/types';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { SearchBox } from '../../components/ui/Field';
import { FilterBar, PageHeader } from '../../components/ui/PageHeader';
import { Pagination } from '../../components/ui/Pagination';
import { useApi, useDebounced } from '../../hooks/useApi';
import { useTableState } from '../../hooks/useTableState';
import { formatDateTime } from '../../lib/format';

function details(raw: string | null) {
  if (!raw) return '—';
  try {
    const obj = JSON.parse(raw) as Record<string, unknown>;
    return Object.entries(obj).map(([k, v]) => `${k}: ${String(v)}`).join(' · ');
  } catch {
    return raw;
  }
}

const tone = (action: string) =>
  action.includes('FAILED') || action.includes('REUSE') ? 'red'
    : action.startsWith('LOGIN') || action === 'LOGOUT' ? 'gray'
    : action.includes('APPROVED') || action.includes('RECEIVED') ? 'green'
    : action.includes('CANCELLED') || action.includes('ARCHIVED') || action.includes('DELETED') ? 'amber' : 'indigo';

export function AuditLogsPage() {
  const [actor, setActor] = useState('');
  const [action, setAction] = useState('');
  const [entityType, setEntityType] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const debouncedActor = useDebounced(actor);
  const debouncedAction = useDebounced(action);
  const debouncedEntity = useDebounced(entityType);
  const table = useTableState({ key: 'createdAt', direction: 'desc' }, 25);
  const logs = useApi(() => auditApi.list({ actor: debouncedActor, action: debouncedAction, entityType: debouncedEntity, from, to,
    page: table.page, size: table.size, sort: table.sortParam }),
    [debouncedActor, debouncedAction, debouncedEntity, from, to, table.page, table.sortParam]);

  const columns: Column<AuditLog>[] = [
    { key: 'time', header: 'Time', sortKey: 'createdAt', render: (a) => formatDateTime(a.createdAt) },
    { key: 'actor', header: 'Actor', sortKey: 'actorEmail', render: (a) => a.actorEmail ?? 'System' },
    { key: 'action', header: 'Action', sortKey: 'action', render: (a) => <Badge tone={tone(a.action)}>{a.action}</Badge> },
    { key: 'entity', header: 'Entity', render: (a) => `${a.entityType}${a.entityId ? ` #${a.entityId}` : ''}` },
    { key: 'details', header: 'Details', render: (a) => <span className="block max-w-md truncate text-slate-500" title={details(a.details)}>{details(a.details)}</span> },
    { key: 'ip', header: 'IP', render: (a) => <span className="font-mono text-xs">{a.ipAddress ?? '—'}</span> },
  ];

  return (
    <>
      <PageHeader title="Audit logs" description="Security events and business changes, recorded with actor, time and details. Visible to administrators only." />
      <FilterBar>
        <SearchBox value={actor} onChange={(v) => { setActor(v); table.setPage(0); }} placeholder="Actor email" className="sm:w-56" />
        <input aria-label="Action" placeholder="Action, e.g. PO_APPROVED" className="input sm:w-56" value={action}
               onChange={(e) => { setAction(e.target.value); table.setPage(0); }} />
        <input aria-label="Entity type" placeholder="Entity, e.g. Product" className="input sm:w-44" value={entityType}
               onChange={(e) => { setEntityType(e.target.value); table.setPage(0); }} />
        <input type="date" aria-label="From date" className="input w-auto" value={from} onChange={(e) => { setFrom(e.target.value); table.setPage(0); }} />
        <input type="date" aria-label="To date" className="input w-auto" value={to} onChange={(e) => { setTo(e.target.value); table.setPage(0); }} />
      </FilterBar>
      <DataTable columns={columns} rows={logs.data?.content} rowKey={(a) => a.id} loading={logs.loading} error={logs.error}
                 onRetry={logs.reload} sort={table.sort} onSort={table.setSort} emptyTitle="No audit entries match"
                 footer={logs.data && <Pagination page={logs.data.page} size={logs.data.size}
                   totalElements={logs.data.totalElements} totalPages={logs.data.totalPages} onPage={table.setPage} />} />
    </>
  );
}
