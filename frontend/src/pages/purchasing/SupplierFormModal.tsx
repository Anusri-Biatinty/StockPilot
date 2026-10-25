import { useEffect, useState, type FormEvent } from 'react';
import { suppliersApi, type SupplierRequest } from '../../api/endpoints';
import type { Supplier } from '../../api/types';
import { Button } from '../../components/ui/Button';
import { TextInput } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import { useToast } from '../../components/ui/Toast';
import { errorMessage, fieldErrors } from '../../lib/errors';

const blank: SupplierRequest = { code: '', name: '', contactName: '', email: '', phone: '', address: '', active: true };

export function SupplierFormModal({ open, supplier, onClose, onSaved }: {
  open: boolean; supplier?: Supplier | null; onClose: () => void; onSaved: (s: Supplier) => void;
}) {
  const toast = useToast();
  const [form, setForm] = useState<SupplierRequest>(blank);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setErrors({});
    setForm(supplier ? { code: supplier.code, name: supplier.name, contactName: supplier.contactName ?? '', email: supplier.email ?? '',
      phone: supplier.phone ?? '', address: supplier.address ?? '', active: supplier.active } : blank);
  }, [open, supplier]);

  const set = (k: keyof SupplierRequest) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const v: Record<string, string> = {};
    if (!form.name.trim()) v.name = 'Name is required';
    if (form.email && !/^\S+@\S+\.\S+$/.test(form.email)) v.email = 'Enter a valid email';
    if (!supplier && form.code && !/^[A-Za-z0-9][A-Za-z0-9-]{1,19}$/.test(form.code)) v.code = '2-20 letters, digits or dashes';
    setErrors(v);
    if (Object.keys(v).length) return;
    setSaving(true);
    try {
      const body = { ...form, code: form.code || undefined };
      const saved = supplier ? await suppliersApi.update(supplier.id, body) : await suppliersApi.create(body);
      toast.success(`Saved ${saved.name}`);
      onSaved(saved);
    } catch (err) {
      setErrors(fieldErrors(err));
      toast.error(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title={supplier ? `Edit ${supplier.name}` : 'New supplier'} size="lg"
           footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button type="submit" form="supplier-form" loading={saving}>Save</Button></>}>
      <form id="supplier-form" onSubmit={submit} noValidate className="grid gap-4 sm:grid-cols-2">
        <TextInput label="Code" value={form.code} onChange={set('code')} error={errors.code} disabled={!!supplier}
                   hint={supplier ? 'Codes cannot be changed' : 'Leave blank to generate'} />
        <TextInput label="Name" value={form.name} onChange={set('name')} error={errors.name} required maxLength={160} />
        <TextInput label="Contact person" value={form.contactName} onChange={set('contactName')} maxLength={120} />
        <TextInput label="Email" type="email" value={form.email} onChange={set('email')} error={errors.email} maxLength={160} />
        <TextInput label="Phone" value={form.phone} onChange={set('phone')} maxLength={40} />
        <TextInput label="Address" value={form.address} onChange={set('address')} maxLength={255} />
        <label className="flex items-center gap-2 text-sm sm:col-span-2">
          <input type="checkbox" checked={!!form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} className="h-4 w-4 rounded" />
          Active (inactive suppliers cannot receive new purchase orders)
        </label>
      </form>
    </Modal>
  );
}
