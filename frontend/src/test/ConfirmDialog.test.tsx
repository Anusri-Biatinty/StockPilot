import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ConfirmDialog } from '../components/ui/Modal';

describe('ConfirmDialog', () => {
  it('calls confirm and cancel handlers', async () => {
    const onConfirm = vi.fn();
    const onCancel = vi.fn();
    render(<ConfirmDialog open title="Archive product?" message="Are you sure?" confirmLabel="Archive" danger
                          onConfirm={onConfirm} onCancel={onCancel} />);
    expect(screen.getByRole('dialog', { name: 'Archive product?' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Archive' }));
    expect(onConfirm).toHaveBeenCalledOnce();
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onCancel).toHaveBeenCalledOnce();
  });

  it('closes on Escape and renders nothing when closed', async () => {
    const onCancel = vi.fn();
    const { rerender } = render(<ConfirmDialog open title="Delete?" message="x" onConfirm={vi.fn()} onCancel={onCancel} />);
    await userEvent.keyboard('{Escape}');
    expect(onCancel).toHaveBeenCalled();
    rerender(<ConfirmDialog open={false} title="Delete?" message="x" onConfirm={vi.fn()} onCancel={onCancel} />);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('disables buttons while loading', () => {
    render(<ConfirmDialog open loading title="Saving" message="x" onConfirm={vi.fn()} onCancel={vi.fn()} />);
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled();
  });
});
