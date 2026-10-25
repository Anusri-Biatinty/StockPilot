import clsx from 'clsx';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

interface Props {
  label: string;
  value: ReactNode;
  icon: ReactNode;
  hint?: ReactNode;
  to?: string;
  tone?: 'brand' | 'green' | 'amber' | 'red' | 'blue';
}

const tones = {
  brand: 'bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-100',
  green: 'bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-400',
  amber: 'bg-amber-50 text-amber-600 dark:bg-amber-500/10 dark:text-amber-400',
  red: 'bg-rose-50 text-rose-600 dark:bg-rose-500/10 dark:text-rose-400',
  blue: 'bg-sky-50 text-sky-600 dark:bg-sky-500/10 dark:text-sky-400',
};

/** KPI tile; when `to` is set the whole tile is a drill-down link. */
export function StatCard({ label, value, icon, hint, to, tone = 'brand' }: Props) {
  const content = (
    <div className="flex items-start justify-between gap-3">
      <div className="min-w-0">
        <p className="text-sm text-slate-500">{label}</p>
        <p className="mt-1 truncate text-2xl font-semibold tabular-nums">{value}</p>
        {hint && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
      </div>
      <div className={clsx('rounded-lg p-2.5', tones[tone])} aria-hidden="true">{icon}</div>
    </div>
  );
  return to ? (
    <Link to={to} className="card block p-4 transition hover:border-brand-300 hover:shadow-md dark:hover:border-brand-700">{content}</Link>
  ) : (
    <div className="card p-4">{content}</div>
  );
}
