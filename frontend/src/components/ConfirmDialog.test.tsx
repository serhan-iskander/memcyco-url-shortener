/**
 * ConfirmDialog — fires onConfirm/onCancel as expected and respects loading.
 */
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ConfirmDialog } from './ConfirmDialog';

describe('<ConfirmDialog>', () => {
  function renderDialog(overrides: Partial<Parameters<typeof ConfirmDialog>[0]> = {}) {
    const onConfirm = vi.fn();
    const onCancel = vi.fn();
    const props = {
      open: true,
      title: 'Delete link',
      message: 'Are you sure?',
      onConfirm,
      onCancel,
      ...overrides,
    } as const;
    const utils = render(<ConfirmDialog {...props} />);
    return { ...utils, onConfirm, onCancel };
  }

  it('renders title and message when open', () => {
    renderDialog();
    expect(screen.getByText('Delete link')).toBeInTheDocument();
    expect(screen.getByText('Are you sure?')).toBeInTheDocument();
  });

  it('invokes onConfirm when the confirm button is clicked', async () => {
    const user = userEvent.setup();
    const { onConfirm } = renderDialog({ confirmLabel: 'Yes, delete' });
    await user.click(screen.getByRole('button', { name: 'Yes, delete' }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it('invokes onCancel when the cancel button is clicked', async () => {
    const user = userEvent.setup();
    const { onCancel } = renderDialog();
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('disables both buttons while loading', () => {
    renderDialog({ loading: true });
    expect(screen.getByRole('button', { name: 'Confirm' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled();
  });

  it('renders nothing when closed', () => {
    render(
      <ConfirmDialog
        open={false}
        title="X"
        message="Y"
        onConfirm={() => {}}
        onCancel={() => {}}
      />,
    );
    expect(screen.queryByText('X')).not.toBeInTheDocument();
  });
});
