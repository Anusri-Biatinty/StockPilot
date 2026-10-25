import clsx from 'clsx';
import type { ReactNode } from 'react';
import { humanize } from '../../lib/format';

type Tone = 'gray' | 'green' | 'amber' | 'red' | 'blue' | 'indigo' | 'violet';

const tones: Record<Tone, string> = {
  gray: 'bg-slate-100 text-slate-700 ring-slate-500/20 dark:bg-slate-800 dark:text-slate-300',
  green: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20 dark:bg-emerald-500/10 dark:text-emerald-400',
  amber: 'bg-amber-50 text-amber-800 ring-amber-600/20 dark:bg-amber-500/10 dark:text-amber-400',
  red: 'bg-rose-50 text-rose-700 ring-rose-600/20 dark:bg-rose-500/10 dark:text-rose-400',
  blue: 'bg-sky-50 text-sky-700 ring-sky-600/20 dark:bg-sky-500/10 dark:text-sky-400',
  indigo: 'bg-indigo-50 text-indigo-700 ring-indigo-600/20 dark:bg-indigo-500/10 dark:text-indigo-300',
  violet: 'bg-violet-50 text-violet-700 ring-violet-600/20 dark:bg-violet-500/10 dark:text-violet-300',
};

export function Badge({ tone = 'gray', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span className={clsx('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset', tones[tone])}>
      {children}
    </span>
  );
}

const STATUS_TONES: Record<string, Tone> = {
  // stock
  IN_STOCK: 'green', LOW_STOCK: 'amber', OUT_OF_STOCK: 'red',
  // product / generic
  ACTIVE: 'green', ARCHIVED: 'gray', INACTIVE: 'gray',
  // orders
  DRAFT: 'gray', SUBMITTED: 'blue', APPROVED: 'indigo', PARTIALLY_RECEIVED: 'amber', RECEIVED: 'green',
  CONFIRMED: 'indigo', SHIPPED: 'violet', DELIVERED: 'green', CANCELLED: 'red',
  IN_TRANSIT: 'violet', OUT_FOR_DELIVERY: 'blue',
  // notifications
  INFO: 'blue', WARNING: 'amber', CRITICAL: 'red',
};

export function StatusBadge({ status }: { status: string }) {
  return <Badge tone={STATUS_TONES[status] ?? 'gray'}>{humanize(status)}</Badge>;
}

const MOVEMENT_TONES: Record<string, Tone> = {
  RECEIPT: 'green', PURCHASE_RECEIPT: 'green', TRANSFER_IN: 'blue', TRANSFER_OUT: 'violet',
  ISSUE: 'amber', SALES_SHIPMENT: 'indigo', ADJUSTMENT: 'gray',
};

export function MovementBadge({ type }: { type: string }) {
  return <Badge tone={MOVEMENT_TONES[type] ?? 'gray'}>{humanize(type)}</Badge>;
}
