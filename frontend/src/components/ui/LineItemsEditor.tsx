import { Plus, Trash2 } from 'lucide-react';
import type { Product } from '../../api/types';
import { formatMoney } from '../../lib/format';
import { Button } from './Button';
import { ProductPicker } from './ProductPicker';

export interface Line {
  key: number;
  product: Product | null;
  quantity: string;
  /** Purchase orders only: optional override of the supplier/product cost. */
  unitCost: string;
}

let nextKey = 1;
export const newLine = (product: Product | null = null, quantity = '1', unitCost = ''): Line =>
  ({ key: nextKey++, product, quantity, unitCost });

interface Props {
  lines: Line[];
  onChange: (lines: Line[]) => void;
  mode: 'purchase' | 'sales';
  errors?: Record<number, string>;
  /** Sales orders: available units per product id in the chosen warehouse. */
  availability?: Record<number, number>;
}

/**
 * Line editor shared by purchase and sales orders. Totals shown here are a preview only;
 * the server recalculates every amount from catalogue prices when the order is saved.
 */
export function LineItemsEditor({ lines, onChange, mode, errors = {}, availability }: Props) {
  const update = (key: number, patch: Partial<Line>) => onChange(lines.map((l) => (l.key === key ? { ...l, ...patch } : l)));
  const chosen = lines.map((l) => l.product?.id).filter((id): id is number => !!id);

  const price = (l: Line) => mode === 'sales'
    ? Number(l.product?.unitPrice ?? 0)
    : l.unitCost !== '' ? Number(l.unitCost) : Number(l.product?.costPrice ?? 0);
  const total = lines.reduce((sum, l) => sum + price(l) * (Number(l.quantity) || 0), 0);

  return (
    <div className="space-y-3">
      {lines.map((l, index) => {
        // Unknown until the lookup returns; show nothing rather than a misleading "0 available".
        const avail = l.product && availability && l.product.id in availability ? availability[l.product.id] : undefined;
        const short = avail !== undefined && Number(l.quantity) > avail;
        return (
          <div key={l.key} className="grid items-start gap-3 rounded-lg border border-slate-200 p-3 dark:border-slate-800 md:grid-cols-12">
            <div className="md:col-span-5">
              <ProductPicker label={`Line ${index + 1} product`} value={l.product} required error={errors[l.key]}
                             exclude={chosen.filter((id) => id !== l.product?.id)}
                             onChange={(p) => update(l.key, { product: p, unitCost: '' })} />
            </div>
            <div className="md:col-span-2">
              <label className="label" htmlFor={`qty-${l.key}`}>Quantity</label>
              <input id={`qty-${l.key}`} type="number" min="1" step="1" className="input" value={l.quantity}
                     onChange={(e) => update(l.key, { quantity: e.target.value })} />
              {avail !== undefined && <p className={`mt-1 text-xs ${short ? 'text-rose-600' : 'text-slate-500'}`}>{avail} available</p>}
            </div>
            <div className="md:col-span-2">
              {mode === 'purchase' ? (
                <>
                  <label className="label" htmlFor={`cost-${l.key}`}>Unit cost</label>
                  <input id={`cost-${l.key}`} type="number" min="0" step="0.01" className="input" value={l.unitCost}
                         placeholder={l.product ? String(l.product.costPrice) : 'Default'}
                         onChange={(e) => update(l.key, { unitCost: e.target.value })} />
                </>
              ) : (
                <><span className="label">Unit price</span><p className="py-2 text-sm">{l.product ? formatMoney(l.product.unitPrice) : '—'}</p></>
              )}
            </div>
            <div className="md:col-span-2">
              <span className="label">Line total</span>
              <p className="py-2 text-sm font-medium tabular-nums">{l.product ? formatMoney(price(l) * (Number(l.quantity) || 0)) : '—'}</p>
            </div>
            <div className="flex md:col-span-1 md:justify-end md:pt-6">
              <Button variant="ghost" size="sm" aria-label={`Remove line ${index + 1}`} disabled={lines.length === 1}
                      onClick={() => onChange(lines.filter((x) => x.key !== l.key))}>
                <Trash2 className="h-4 w-4 text-rose-500" />
              </Button>
            </div>
          </div>
        );
      })}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Button variant="secondary" size="sm" icon={<Plus className="h-4 w-4" />} onClick={() => onChange([...lines, newLine()])}>Add line</Button>
        <p className="text-sm">Estimated total <span className="ml-2 text-lg font-semibold tabular-nums">{formatMoney(total)}</span></p>
      </div>
    </div>
  );
}

/** Returns a map of line key -> error, plus a general message, for empty/invalid lines. */
export function validateLines(lines: Line[]): { lineErrors: Record<number, string>; message?: string } {
  const lineErrors: Record<number, string> = {};
  for (const l of lines) {
    if (!l.product) lineErrors[l.key] = 'Choose a product';
  }
  const badQty = lines.some((l) => !Number.isInteger(Number(l.quantity)) || Number(l.quantity) < 1);
  const badCost = lines.some((l) => l.unitCost !== '' && (Number.isNaN(Number(l.unitCost)) || Number(l.unitCost) < 0));
  return {
    lineErrors,
    message: badQty ? 'Every quantity must be a whole number of at least 1' : badCost ? 'Unit costs cannot be negative' : undefined,
  };
}
