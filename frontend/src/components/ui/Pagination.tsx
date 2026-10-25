import { ChevronLeft, ChevronRight } from 'lucide-react';
import { formatNumber } from '../../lib/format';

interface Props {
  page: number;
  totalPages: number;
  totalElements: number;
  size: number;
  onPage: (page: number) => void;
}

export function Pagination({ page, totalPages, totalElements, size, onPage }: Props) {
  if (totalElements === 0) return null;
  const from = page * size + 1;
  const to = Math.min(totalElements, (page + 1) * size);
  return (
    <nav className="flex items-center justify-between gap-4 border-t border-slate-200 px-4 py-3 text-sm dark:border-slate-800"
         aria-label="Pagination">
      <p className="text-slate-500">
        <span className="font-medium text-slate-700 dark:text-slate-300">{formatNumber(from)}–{formatNumber(to)}</span> of{' '}
        {formatNumber(totalElements)}
      </p>
      <div className="flex items-center gap-1">
        <button onClick={() => onPage(page - 1)} disabled={page === 0} aria-label="Previous page"
                className="rounded-lg p-1.5 text-slate-600 hover:bg-slate-100 disabled:opacity-40 dark:text-slate-300 dark:hover:bg-slate-800">
          <ChevronLeft className="h-4 w-4" />
        </button>
        <span className="px-2 text-slate-600 dark:text-slate-400">Page {page + 1} of {Math.max(totalPages, 1)}</span>
        <button onClick={() => onPage(page + 1)} disabled={page + 1 >= totalPages} aria-label="Next page"
                className="rounded-lg p-1.5 text-slate-600 hover:bg-slate-100 disabled:opacity-40 dark:text-slate-300 dark:hover:bg-slate-800">
          <ChevronRight className="h-4 w-4" />
        </button>
      </div>
    </nav>
  );
}
