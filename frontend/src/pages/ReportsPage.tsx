import clsx from 'clsx';
import { Download } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { reportsApi, warehousesApi } from '../api/endpoints';
import type { ColumnType } from '../api/types';
import { Button } from '../components/ui/Button';
import { DataTable, type Column, type SortState } from '../components/ui/DataTable';
import { FilterSelect } from '../components/ui/Field';
import { FilterBar, PageHeader } from '../components/ui/PageHeader';
import { Pagination } from '../components/ui/Pagination';
import { useToast } from '../components/ui/Toast';
import { useApi } from '../hooks/useApi';
import { errorMessage } from '../lib/errors';
import { formatDate, formatDateTime, formatMoney, formatNumber, humanize, isoDate } from '../lib/format';

type Row = Record<string, string | number | null>;

function formatCell(value: string | number | null, type: ColumnType) {
  if (value == null || value === '') return '—';
  switch (type) {
    case 'money': return formatMoney(Number(value));
    case 'number': return formatNumber(Number(value));
    case 'percent': return `${Number(value).toFixed(1)}%`;
    case 'date': return formatDate(String(value));
    case 'datetime': return formatDateTime(String(value));
    default: return /^[A-Z_]+$/.test(String(value)) && String(value).includes('_') ? humanize(String(value)) : String(value);
  }
}

export function ReportsPage() {
  const toast = useToast();
  const [params, setParams] = useSearchParams();
  const catalogue = useApi(() => reportsApi.catalogue(), []);
  const warehouses = useApi(() => warehousesApi.list(), []);
  const key = params.get('report') ?? 'inventory';
  const defaultTo = isoDate(new Date());
  const defaultFrom = isoDate(new Date(Date.now() - 89 * 86400000));
  const from = params.get('from') ?? defaultFrom;
  const to = params.get('to') ?? defaultTo;
  const warehouseId = params.get('warehouseId') ?? '';
  const [sort, setSort] = useState<SortState | undefined>();
  const [page, setPage] = useState(0);
  const [exporting, setExporting] = useState(false);

  useEffect(() => { setSort(undefined); setPage(0); }, [key]);

  const info = catalogue.data?.find((r) => r.key === key);
  const query = {
    from: info?.dateRange ? from : undefined, to: info?.dateRange ? to : undefined,
    warehouseId: info?.warehouseFilter ? warehouseId : undefined, sort: sort?.key, direction: sort?.direction,
  };
  const report = useApi(() => reportsApi.run(key, { ...query, page, size: 25 }),
    [key, from, to, warehouseId, sort?.key, sort?.direction, page, !!info]);

  const update = (patch: Record<string, string>) => {
    const next = new URLSearchParams(params);
    Object.entries(patch).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)));
    setParams(next, { replace: true });
    setPage(0);
  };

  const exportCsv = async () => {
    setExporting(true);
    try {
      await reportsApi.exportCsv(key, query);
      toast.success('CSV downloaded');
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setExporting(false);
    }
  };

  const columns: Column<Row>[] = (report.data?.columns ?? []).map((c) => ({
    key: c.key, header: c.label, sortKey: c.key,
    align: ['money', 'number', 'percent'].includes(c.type) ? 'right' : 'left',
    render: (row: Row) => formatCell(row[c.key], c.type),
  }));

  return (
    <>
      <PageHeader title="Reports & exports" description="Every figure is queried live from the database. Exports include all rows, not just the visible page."
                  actions={<Button icon={<Download className="h-4 w-4" />} onClick={exportCsv} loading={exporting}>Export CSV</Button>} />
      <div className="mb-4 flex gap-2 overflow-x-auto pb-1" role="tablist" aria-label="Reports">
        {catalogue.data?.map((r) => (
          <button key={r.key} role="tab" aria-selected={r.key === key} onClick={() => update({ report: r.key })}
                  className={clsx('whitespace-nowrap rounded-lg px-3 py-2 text-sm font-medium',
                    r.key === key ? 'bg-brand-600 text-white' : 'bg-white text-slate-700 ring-1 ring-slate-200 hover:bg-slate-50 dark:bg-slate-900 dark:text-slate-300 dark:ring-slate-800')}>
            {r.title}
          </button>
        ))}
      </div>
      {info && <p className="mb-4 text-sm text-slate-500">{info.description}</p>}
      <FilterBar>
        {info?.dateRange && <>
          <label className="text-sm text-slate-500" htmlFor="report-from">From</label>
          <input id="report-from" type="date" className="input w-auto" value={from} max={to} onChange={(e) => e.target.value && update({ from: e.target.value })} />
          <label className="text-sm text-slate-500" htmlFor="report-to">To</label>
          <input id="report-to" type="date" className="input w-auto" value={to} min={from} onChange={(e) => e.target.value && update({ to: e.target.value })} />
        </>}
        {info?.warehouseFilter && (
          <FilterSelect label="Warehouse" value={warehouseId} onChange={(v) => update({ warehouseId: v })}>
            <option value="">All warehouses</option>
            {warehouses.data?.map((w) => <option key={w.id} value={w.id}>{w.code}</option>)}
          </FilterSelect>
        )}
      </FilterBar>
      <DataTable columns={columns} rows={report.data?.rows} rowKey={(r) => JSON.stringify(r)} loading={report.loading}
                 error={report.error} onRetry={report.reload} sort={sort} onSort={(s) => { setSort(s); setPage(0); }}
                 emptyTitle="No rows for these filters"
                 footer={report.data && <Pagination page={report.data.page} size={report.data.size}
                   totalElements={report.data.totalElements} totalPages={report.data.totalPages} onPage={setPage} />} />
    </>
  );
}
