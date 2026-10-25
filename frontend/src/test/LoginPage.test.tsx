import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LoginPage } from '../pages/auth/LoginPage';

const login = vi.fn();
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: null, initializing: false, login }),
}));

const renderPage = () => render(<MemoryRouter><LoginPage /></MemoryRouter>);

describe('LoginPage', () => {
  beforeEach(() => {
    login.mockReset();
  });

  it('validates fields before calling the API', async () => {
    renderPage();
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(screen.getByText('Enter a valid email address')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
    expect(login).not.toHaveBeenCalled();
  });

  it('submits trimmed credentials', async () => {
    login.mockResolvedValue(undefined);
    renderPage();
    await userEvent.type(screen.getByLabelText(/Email/), '  admin@stockpilot.local ');
    await userEvent.type(screen.getByLabelText(/Password/), 'Password123!');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(login).toHaveBeenCalledWith('admin@stockpilot.local', 'Password123!');
  });

  it('shows the server error message on failure', async () => {
    login.mockImplementation(async () => { throw new Error('Invalid email or password'); });
    renderPage();
    await userEvent.type(screen.getByLabelText(/Email/), 'admin@stockpilot.local');
    await userEvent.type(screen.getByLabelText(/Password/), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password');
  });
});
