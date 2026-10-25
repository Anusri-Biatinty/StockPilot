import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import type { User } from '../api/types';
import { ProtectedRoute } from '../auth/ProtectedRoute';

const auth: { user: User | null; initializing: boolean } = { user: null, initializing: false };
vi.mock('../auth/AuthContext', () => ({ useAuth: () => auth }));

const user = (role: User['role']): User => ({
  id: 1, email: 'x@y.z', fullName: 'X', role, enabled: true, lastLoginAt: null, createdAt: '2026-01-01T00:00:00Z',
});

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/login" element={<p>login page</p>} />
        <Route path="/unauthorized" element={<p>denied</p>} />
        <Route path="/audit" element={<ProtectedRoute roles={['ADMIN']}><p>audit logs</p></ProtectedRoute>} />
        <Route path="/" element={<ProtectedRoute><p>dashboard</p></ProtectedRoute>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ProtectedRoute', () => {
  it('redirects anonymous users to login', () => {
    auth.user = null;
    renderAt('/');
    expect(screen.getByText('login page')).toBeInTheDocument();
  });

  it('shows a spinner while the session is being restored', () => {
    auth.user = null;
    auth.initializing = true;
    renderAt('/');
    expect(screen.getByRole('status', { name: 'Loading' })).toBeInTheDocument();
    auth.initializing = false;
  });

  it('blocks roles that are not allowed', () => {
    auth.user = user('VIEWER');
    renderAt('/audit');
    expect(screen.getByText('denied')).toBeInTheDocument();
  });

  it('renders the page for allowed roles', () => {
    auth.user = user('ADMIN');
    renderAt('/audit');
    expect(screen.getByText('audit logs')).toBeInTheDocument();
  });
});
