import { useEffect, useState, type FormEvent } from 'react';
import { productsApi } from '../../api/endpoints';
import type { Category, Product, ProductRequest } from '../../api/types';
import { Button } from '../../components/ui/Button';
import { SelectInput, TextArea, TextInput } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import { useToast } from '../../components/ui/Toast';
import { errorMessage, fieldErrors } from '../../lib/errors';

interface Props {
  open: boolean;
  product?: Product | null;
  categories: Category[];
  onClose: () => void;
  onSaved: (product: Product) => void;
}

const empty = { sku: '', name: '', description: '', categoryId: '', unitPrice: '', costPrice: '', reorderThreshold: '0', imageUrl: '' };

/** Client-side checks mirror the backend's Bean Validation so most mistakes are caught before submitting. */
export function validateProduct(f: typeof empty): Record<string, string> {
  const e: Record<string, string> = {};
  if (f.sku && !/^[A-Za-z0-9][A-Za-z0-9-]{2,39}$/.test(f.sku)) e.sku = 'SKU must be 3-40 letters, digits or dashes';
  if (!f.name.trim()) e.name = 'Name is required';
  if (!f.categoryId) e.categoryId = 'Choose a category';
  for (const key of ['unitPrice', 'costPrice'] as const) {
    const n = Number(f[key]);
    if (f[key] === '' || Number.isNaN(n)) e[key] = 'Enter a price';
    else if (n < 0) e[key] = 'Price cannot be negative';
    else if (!/^\d+(\.\d{1,2})?$/.test(f[key])) e[key] = 'Use at most 2 decimals';
  }
  const t = Number(f.reorderThreshold);
  if (f.reorderThreshold === '' || !Number.isInteger(t) || t < 0) e.reorderThreshold = 'Enter a whole number of 0 or more';
  if (f.imageUrl && !/^https?:\/\/[^\s/]+\.[^\s]+$/i.test(f.imageUrl)) e.imageUrl = 'Must be an http(s) URL';
  return e;
}

export function ProductFormModal({ open, product, categories, onClose, onSaved }: Props) {
  const toast = useToast();
  const [form, setForm] = useState(empty);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setErrors({});
    setForm(product ? {
      sku: product.sku, name: product.name, description: product.description ?? '', categoryId: String(product.categoryId),
      unitPrice: String(product.unitPrice), costPrice: String(product.costPrice),
      reorderThreshold: String(product.reorderThreshold), imageUrl: product.imageUrl ?? '',
    } : empty);
  }, [open, product]);

  const set = (key: keyof typeof empty) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const v = validateProduct(form);
    setErrors(v);
    if (Object.keys(v).length) return;
    const body: ProductRequest = {
      sku: form.sku.trim() || undefined,
      name: form.name.trim(),
      description: form.description.trim() || undefined,
      categoryId: Number(form.categoryId),
      unitPrice: Number(form.unitPrice),
      costPrice: Number(form.costPrice),
      reorderThreshold: Number(form.reorderThreshold),
      imageUrl: form.imageUrl.trim() || undefined,
      version: product?.version,
    };
    setSaving(true);
    try {
      const saved = product ? await productsApi.update(product.id, body) : await productsApi.create(body);
      toast.success(product ? `Saved ${saved.sku}` : `Created ${saved.sku}`);
      onSaved(saved);
    } catch (err) {
      setErrors(fieldErrors(err));
      toast.error(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const margin = Number(form.unitPrice) > 0 ? ((Number(form.unitPrice) - Number(form.costPrice)) / Number(form.unitPrice)) * 100 : null;
  const activeCategories = categories.filter((c) => c.active || c.id === product?.categoryId);

  return (
    <Modal open={open} onClose={onClose} title={product ? `Edit ${product.sku}` : 'New product'} size="lg"
           footer={<>
             <Button variant="secondary" onClick={onClose} disabled={saving}>Cancel</Button>
             <Button type="submit" form="product-form" loading={saving}>{product ? 'Save changes' : 'Create product'}</Button>
           </>}>
      <form id="product-form" onSubmit={submit} noValidate className="grid gap-4 sm:grid-cols-2">
        <TextInput label="SKU" value={form.sku} onChange={set('sku')} error={errors.sku}
                   hint={product ? undefined : 'Leave blank to generate one from the category'} />
        <SelectInput label="Category" value={form.categoryId} onChange={set('categoryId')} error={errors.categoryId} required>
          <option value="">Select…</option>
          {activeCategories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </SelectInput>
        <TextInput className="sm:col-span-2" label="Name" value={form.name} onChange={set('name')} error={errors.name} required maxLength={160} />
        <TextArea className="sm:col-span-2" label="Description" value={form.description} onChange={set('description')} maxLength={4000} />
        <TextInput label="Cost price (USD)" type="number" min="0" step="0.01" inputMode="decimal" value={form.costPrice}
                   onChange={set('costPrice')} error={errors.costPrice} required />
        <TextInput label="Selling price (USD)" type="number" min="0" step="0.01" inputMode="decimal" value={form.unitPrice}
                   onChange={set('unitPrice')} error={errors.unitPrice} required
                   hint={margin != null && Number.isFinite(margin) ? `Gross margin ${margin.toFixed(1)}%` : undefined} />
        <TextInput label="Reorder threshold" type="number" min="0" step="1" value={form.reorderThreshold}
                   onChange={set('reorderThreshold')} error={errors.reorderThreshold} required
                   hint="Alert when total stock falls to this level" />
        <TextInput label="Image URL" type="url" value={form.imageUrl} onChange={set('imageUrl')} error={errors.imageUrl}
                   placeholder="https://…" />
      </form>
    </Modal>
  );
}
