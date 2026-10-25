import clsx from 'clsx';
import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react';
import type { ReactNode } from 'react';
import { EmptyState, ErrorState, LoadingBlock } from './States';

export interface Column<T> {
  key: string;
  header: string;
  render: (row: T) => ReactNode;
  /** Server-side sort property; when set, the header becomes a sort toggle. */
  sortKey?: string;
  align?: 'left' | 'right';
  className?: string;
}

export interface SortState {
  key: string;
  direction: 'asc' | 'desc';
}

interface Props<T> {
  columns: Column<T>[];
  rows: T[] | undefined;
  rowKey: (row: T) => string | number;
  loading?: boolean;
  error?: string | null;
  onRetry?: () => void;
  emptyTitle?: string;
  emptyMessage?: string;
  emptyAction?: ReactNode;
  sort?: SortState;
  onSort?: (sort: SortState) => void;
  onRowClick?: (row: T) => void;
  footer?: ReactNode;
}

export function DataTable<T>({
  columns, rows, rowKey, loading, error, onRetry, emptyTitle = 'Nothing here yet', emptyMessage, emptyAction,
  sort, onSort, onRowClick, footer,
}: Props<T>) {
  const toggle = (key: string) => {
    if (!onSort) return;
    onSort(sort?.key === key ? { key, direction: sort.direction === 'asc' ? 'desc' : 'asc' } : { key, direction: 'asc' });
  };

  let body: ReactNode;
  if (error) body = <ErrorState message={error} onRetry={onRetry} />;
  else if (loading && !rows) body = <LoadingBlock />;
  else if (!rows || rows.length === 0) body = <EmptyState title={emptyTitle} message={emptyMessage} action={emptyAction} />;

  return (
    <div className="card overflow-hidden">
      {body ?? (
        <div className={clsx('overflow-x-auto', loading && 'opacity-60 transition-opacity')}>
          <table className="min-w-full divide-y divide-slate-200 dark:divide-slate-800">
            <thead className="table-head">
              <tr>
                {columns.map((c) => (
                  <th key={c.key} scope="col" className={clsx('px-4 py-2.5', c.align === 'right' && 'text-right')}
                      aria-sort={c.sortKey && sort?.key === c.sortKey ? (sort.direction === 'asc' ? 'ascending' : 'descending') : undefined}>
                    {c.sortKey && onSort ? (
                      <button onClick={() => toggle(c.sortKey!)}
                              className={clsx('inline-flex items-center gap-1 uppercase hover:text-slate-800 dark:hover:text-slate-200',
                                c.align === 'right' && 'flex-row-reverse')}>
                        {c.header}
                        {sort?.key === c.sortKey
                          ? (sort.direction === 'asc' ? <ArrowUp className="h-3 w-3" /> : <ArrowDown className="h-3 w-3" />)
                          : <ArrowUpDown className="h-3 w-3 opacity-40" />}
                      </button>
                    ) : c.header}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
              {rows!.map((row) => (
                <tr key={rowKey(row)} onClick={onRowClick ? () => onRowClick(row) : undefined}
                    className={clsx(onRowClick && 'cursor-pointer', 'hover:bg-slate-50 dark:hover:bg-slate-800/40')}>
                  {columns.map((c) => (
                    <td key={c.key} className={clsx('table-cell', c.align === 'right' && 'text-right tabular-nums', c.className)}>
                      {c.render(row)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {!error && rows && rows.length > 0 && footer}
    </div>
  );
}
