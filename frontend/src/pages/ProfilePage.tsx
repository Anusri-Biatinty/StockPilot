import { useState, type FormEvent } from 'react';
import { applySession } from '../api/client';
import { authApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { ROLE_LABELS } from '../auth/permissions';
import { Button } from '../components/ui/Button';
import { TextInput } from '../components/ui/Field';
import { PageHeader } from '../components/ui/PageHeader';
import { useToast } from '../components/ui/Toast';
import { useTheme } from '../hooks/useTheme';
import { errorMessage } from '../lib/errors';
import { formatDateTime } from '../lib/format';
import { validatePassword } from './auth/RegisterPage';

export function ProfilePage() {
  const { user, setUser } = useAuth();
  const toast = useToast();
  const { theme, toggle } = useTheme();
  const [fullName, setFullName] = useState(user?.fullName ?? '');
  const [savingName, setSavingName] = useState(false);
  const [pw, setPw] = useState({ current: '', next: '', confirm: '' });
  const [pwErrors, setPwErrors] = useState<Record<string, string | undefined>>({});
  const [savingPw, setSavingPw] = useState(false);

  if (!user) return null;

  const saveName = async (e: FormEvent) => {
    e.preventDefault();
    if (!fullName.trim()) return;
    setSavingName(true);
    try {
      setUser(await authApi.updateProfile(fullName.trim()));
      toast.success('Profile updated');
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setSavingName(false);
    }
  };

  const savePassword = async (e: FormEvent) => {
    e.preventDefault();
    const v = { current: pw.current ? undefined : 'Enter your current password', next: validatePassword(pw.next),
      confirm: pw.next === pw.confirm ? undefined : 'Passwords do not match' };
    setPwErrors(v);
    if (Object.values(v).some(Boolean)) return;
    setSavingPw(true);
    try {
      applySession(await authApi.changePassword(pw.current, pw.next));
      setPw({ current: '', next: '', confirm: '' });
      toast.success('Password changed. Other sessions were signed out.');
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setSavingPw(false);
    }
  };

  return (
    <>
      <PageHeader title="Profile & settings" />
      <div className="grid gap-6 lg:grid-cols-2">
        <section className="card p-5">
          <h2 className="mb-4 font-semibold">Account</h2>
          <dl className="mb-4 space-y-2 text-sm">
            <div className="flex justify-between"><dt className="text-slate-500">Email</dt><dd>{user.email}</dd></div>
            <div className="flex justify-between"><dt className="text-slate-500">Role</dt><dd>{ROLE_LABELS[user.role]}</dd></div>
            <div className="flex justify-between"><dt className="text-slate-500">Member since</dt><dd>{formatDateTime(user.createdAt)}</dd></div>
          </dl>
          <form onSubmit={saveName} className="flex items-end gap-2">
            <TextInput className="flex-1" label="Full name" value={fullName} onChange={(e) => setFullName(e.target.value)} required maxLength={120} />
            <Button type="submit" loading={savingName} disabled={!fullName.trim() || fullName.trim() === user.fullName}>Save</Button>
          </form>
        </section>
        <section className="card p-5">
          <h2 className="mb-4 font-semibold">Change password</h2>
          <form onSubmit={savePassword} noValidate className="space-y-4">
            <TextInput label="Current password" type="password" autoComplete="current-password" value={pw.current}
                       onChange={(e) => setPw({ ...pw, current: e.target.value })} error={pwErrors.current} required />
            <TextInput label="New password" type="password" autoComplete="new-password" value={pw.next}
                       onChange={(e) => setPw({ ...pw, next: e.target.value })} error={pwErrors.next} hint="8-72 characters with a letter and a digit" required />
            <TextInput label="Confirm new password" type="password" autoComplete="new-password" value={pw.confirm}
                       onChange={(e) => setPw({ ...pw, confirm: e.target.value })} error={pwErrors.confirm} required />
            <Button type="submit" loading={savingPw}>Change password</Button>
          </form>
        </section>
        <section className="card p-5">
          <h2 className="mb-2 font-semibold">Appearance</h2>
          <p className="mb-4 text-sm text-slate-500">Saved in this browser only.</p>
          <Button variant="secondary" onClick={toggle}>Switch to {theme === 'dark' ? 'light' : 'dark'} theme</Button>
        </section>
      </div>
    </>
  );
}
