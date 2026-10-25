import clsx from 'clsx';
import { Search } from 'lucide-react';
import { useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react';

interface FieldProps {
  label: string;
  error?: string;
  hint?: string;
  required?: boolean;
  children: (id: string, describedBy: string | undefined) => ReactNode;
  className?: string;
}

/** Label + control + error text, wired together for screen readers. */
export function Field({ label, error, hint, required, children, className }: FieldProps) {
  const id = useId();
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;
  return (
    <div className={className}>
      <label htmlFor={id} className="label">
        {label}
        {required && <span className="ml-0.5 text-rose-500" aria-hidden="true">*</span>}
      </label>
      {children(id, describedBy)}
      {error ? (
        <p id={`${id}-error`} className="mt-1 text-xs text-rose-600 dark:text-rose-400">{error}</p>
      ) : hint ? (
        <p id={`${id}-hint`} className="mt-1 text-xs text-slate-500">{hint}</p>
      ) : null}
    </div>
  );
}

type Common = { label: string; error?: string; hint?: string; className?: string };

export function TextInput({ label, error, hint, className, required, ...rest }: Common & InputHTMLAttributes<HTMLInputElement>) {
  return (
    <Field label={label} error={error} hint={hint} required={required} className={className}>
      {(id, describedBy) => (
        <input id={id} required={required} aria-invalid={!!error} aria-describedby={describedBy}
               className={clsx('input', error && 'input-error')} {...rest} />
      )}
    </Field>
  );
}

export function SelectInput({ label, error, hint, className, required, children, ...rest }: Common & SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <Field label={label} error={error} hint={hint} required={required} className={className}>
      {(id, describedBy) => (
        <select id={id} required={required} aria-invalid={!!error} aria-describedby={describedBy}
                className={clsx('input', error && 'input-error')} {...rest}>
          {children}
        </select>
      )}
    </Field>
  );
}

export function TextArea({ label, error, hint, className, required, ...rest }: Common & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <Field label={label} error={error} hint={hint} required={required} className={className}>
      {(id, describedBy) => (
        <textarea id={id} required={required} aria-invalid={!!error} aria-describedby={describedBy} rows={3}
                  className={clsx('input', error && 'input-error')} {...rest} />
      )}
    </Field>
  );
}

export function SearchBox({ value, onChange, placeholder = 'Search…', className }: {
  value: string; onChange: (v: string) => void; placeholder?: string; className?: string;
}) {
  return (
    <div className={clsx('relative', className)}>
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
      <input type="search" aria-label={placeholder} value={value} onChange={(e) => onChange(e.target.value)}
             placeholder={placeholder} className="input pl-9" />
    </div>
  );
}

/** Compact unlabeled-looking select for filter bars (still has an accessible name). */
export function FilterSelect({ label, value, onChange, children, className }: {
  label: string; value: string; onChange: (v: string) => void; children: ReactNode; className?: string;
}) {
  return (
    <select aria-label={label} value={value} onChange={(e) => onChange(e.target.value)} className={clsx('input w-auto', className)}>
      {children}
    </select>
  );
}
