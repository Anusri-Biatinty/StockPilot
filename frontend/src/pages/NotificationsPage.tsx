import clsx from 'clsx';
import { CheckCheck } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { notificationsApi } from '../api/endpoints';
import type { AppNotification, NotificationType } from '../api/types';
import { StatusBadge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { FilterSelect } from '../components/ui/Field';
import { FilterBar, PageHeader } from '../components/ui/PageHeader';
import { Pagination } from '../components/ui/Pagination';
import { EmptyState, ErrorState, LoadingBlock } from '../components/ui/States';
import { useToast } from '../components/ui/Toast';
import { useApi } from '../hooks/useApi';
import { errorMessage } from '../lib/errors';
import { formatDateTime, humanize } from '../lib/format';

const TYPES: NotificationType[] = ['LOW_STOCK', 'OUT_OF_STOCK', 'PO_PENDING_APPROVAL', 'PO_APPROVED', 'PO_REJECTED', 'PO_RECEIVED', 'SALES_ORDER_SHIPPED'];

export function NotificationsPage() {
  const toast = useToast();
  const navigate = useNavigate();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [type, setType] = useState('');
  const [severity, setSeverity] = useState('');
  const [page, setPage] = useState(0);
  const list = useApi(() => notificationsApi.list({ unreadOnly, type, severity, page, size: 20 }), [unreadOnly, type, severity, page]);

  const toggleRead = async (n: AppNotification) => {
    try {
      if (n.read) await notificationsApi.markUnread(n.id); else await notificationsApi.markRead(n.id);
      list.reload();
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  const markAll = async () => {
    try {
      const { updated } = await notificationsApi.markAllRead();
      toast.success(`${updated} marked as read`);
      list.reload();
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  const open = async (n: AppNotification) => {
    if (!n.read) await notificationsApi.markRead(n.id).catch(() => undefined);
    if (n.link) navigate(n.link); else list.reload();
  };

  return (
    <>
      <PageHeader title="Notifications" description="Stock alerts, approval requests and order updates for your role."
                  actions={<Button variant="secondary" icon={<CheckCheck className="h-4 w-4" />} onClick={markAll}>Mark all as read</Button>} />
      <FilterBar>
        <FilterSelect label="Read status" value={unreadOnly ? 'unread' : ''} onChange={(v) => { setUnreadOnly(v === 'unread'); setPage(0); }}>
          <option value="">All</option><option value="unread">Unread only</option>
        </FilterSelect>
        <FilterSelect label="Type" value={type} onChange={(v) => { setType(v); setPage(0); }}>
          <option value="">All types</option>
          {TYPES.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}
        </FilterSelect>
        <FilterSelect label="Severity" value={severity} onChange={(v) => { setSeverity(v); setPage(0); }}>
          <option value="">Any severity</option><option value="INFO">Info</option><option value="WARNING">Warning</option><option value="CRITICAL">Critical</option>
        </FilterSelect>
      </FilterBar>
      <div className="card overflow-hidden">
        {list.error ? <ErrorState message={list.error} onRetry={list.reload} />
          : !list.data ? <LoadingBlock />
          : list.data.content.length === 0 ? <EmptyState title="No notifications" message="You're all caught up." />
          : (
            <ul className="divide-y divide-slate-100 dark:divide-slate-800">
              {list.data.content.map((n) => (
                <li key={n.id} className={clsx('flex items-start gap-4 px-5 py-4', !n.read && 'bg-brand-50/50 dark:bg-brand-500/5')}>
                  <span className={clsx('mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full', n.read ? 'bg-slate-200 dark:bg-slate-700' : 'bg-brand-600')} aria-label={n.read ? 'Read' : 'Unread'} />
                  <button className="min-w-0 flex-1 text-left" onClick={() => open(n)}>
                    <span className="flex flex-wrap items-center gap-2">
                      <span className={clsx('text-sm', !n.read && 'font-semibold')}>{n.title}</span>
                      <StatusBadge status={n.severity} />
                    </span>
                    <span className="mt-0.5 block text-sm text-slate-600 dark:text-slate-400">{n.message}</span>
                    <span className="mt-1 block text-xs text-slate-400">{humanize(n.type)} · {formatDateTime(n.createdAt)}</span>
                  </button>
                  <Button size="sm" variant="ghost" onClick={() => toggleRead(n)}>{n.read ? 'Mark unread' : 'Mark read'}</Button>
                </li>
              ))}
            </ul>
          )}
        {list.data && <Pagination page={list.data.page} size={list.data.size} totalElements={list.data.totalElements}
                                  totalPages={list.data.totalPages} onPage={setPage} />}
      </div>
    </>
  );
}
