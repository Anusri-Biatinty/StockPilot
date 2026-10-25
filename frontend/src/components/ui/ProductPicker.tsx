import clsx from 'clsx';
import { useEffect, useId, useRef, useState } from 'react';
import { productsApi } from '../../api/endpoints';
import type { Product } from '../../api/types';
import { useDebounced } from '../../hooks/useApi';
import { formatMoney } from '../../lib/format';

interface Props {
  label: string;
  value: Product | null;
  onChange: (product: Product | null) => void;
  error?: string;
  required?: boolean;
  /** Product ids to hide (already on the order). */
  exclude?: number[];
}

/** Searchable combobox over active products (server-side search, keyboard navigable). */
export function ProductPicker({ label, value, onChange, error, required, exclude = [] }: Props) {
  const id = useId();
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const [results, setResults] = useState<Product[]>([]);
  const [active, setActive] = useState(0);
  const debounced = useDebounced(query, 200);
  const wrapper = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    let current = true;
    productsApi.list({ search: debounced, status: 'ACTIVE', size: 8, sort: 'name,asc' })
      .then((page) => current && setResults(page.content.filter((p) => !exclude.includes(p.id))))
      .catch(() => current && setResults([]));
    return () => {
      current = false;
    };
  }, [debounced, open, exclude.join(",")]);

  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      if (wrapper.current && !wrapper.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, []);

  const choose = (p: Product) => {
    onChange(p);
    setQuery('');
    setOpen(false);
  };

  return (
    <div ref={wrapper} className="relative">
      <label htmlFor={id} className="label">
        {label}{required && <span className="ml-0.5 text-rose-500" aria-hidden="true">*</span>}
      </label>
      <input
        id={id}
        role="combobox"
        aria-expanded={open}
        aria-controls={`${id}-list`}
        aria-autocomplete="list"
        aria-invalid={!!error}
        className={clsx('input', error && 'input-error')}
        placeholder="Search by name or SKU…"
        value={open ? query : value ? `${value.sku} — ${value.name}` : query}
        onFocus={() => { setOpen(true); setActive(0); }}
        onChange={(e) => { setQuery(e.target.value); setOpen(true); setActive(0); if (value) onChange(null); }}
        onKeyDown={(e) => {
          if (e.key === 'ArrowDown') { e.preventDefault(); setActive((a) => Math.min(a + 1, results.length - 1)); }
          if (e.key === 'ArrowUp') { e.preventDefault(); setActive((a) => Math.max(a - 1, 0)); }
          if (e.key === 'Enter' && open && results[active]) { e.preventDefault(); choose(results[active]); }
          if (e.key === 'Escape') setOpen(false);
        }}
      />
      {open && (
        <ul id={`${id}-list`} role="listbox"
            className="card absolute z-30 mt-1 max-h-64 w-max min-w-full max-w-[min(32rem,85vw)] overflow-auto py-1 shadow-lg">
          {results.length === 0 && <li className="px-3 py-2 text-sm text-slate-500">No matching products</li>}
          {results.map((p, i) => (
            <li key={p.id} role="option" aria-selected={i === active}
                onMouseDown={(e) => { e.preventDefault(); choose(p); }}
                onMouseEnter={() => setActive(i)}
                className={clsx('flex cursor-pointer items-center justify-between gap-3 px-3 py-2 text-sm',
                  i === active && 'bg-brand-50 dark:bg-brand-500/10')}>
              <span className="min-w-0 truncate"><span className="font-medium">{p.sku}</span> · {p.name}</span>
              <span className="shrink-0 text-xs text-slate-500">{p.quantityAvailable} avail · {formatMoney(p.unitPrice)}</span>
            </li>
          ))}
        </ul>
      )}
      {error && <p className="mt-1 text-xs text-rose-600">{error}</p>}
    </div>
  );
}
