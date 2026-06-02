/**
 * CopyButton — clipboard write + transient confirmation icon.
 *
 * Note: `@testing-library/user-event` v14 installs its own writable
 * `navigator.clipboard` per `setup()` call. We spy on `writeText` AFTER
 * setup so our spy reaches the same object the component will call.
 */
import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CopyButton } from './CopyButton';

describe('<CopyButton>', () => {
  it('writes value to clipboard when clicked', async () => {
    const user = userEvent.setup();
    const writeText = vi.spyOn(navigator.clipboard, 'writeText');
    render(<CopyButton value="http://short/abc" title="Copy URL" />);
    await user.click(screen.getByRole('button', { name: 'Copy URL' }));
    expect(writeText).toHaveBeenCalledWith('http://short/abc');
  });

  it('shows the copied indicator after a successful copy', async () => {
    const user = userEvent.setup();
    render(<CopyButton value="x" />);
    await user.click(screen.getByRole('button'));
    // After click the icon swaps to the Check (success) variant; assert the
    // aria-label remains stable but a success-coloured icon is now rendered.
    await waitFor(() => {
      expect(screen.getByTestId('CheckIcon')).toBeInTheDocument();
    });
  });

  it('silently swallows clipboard rejection', async () => {
    const user = userEvent.setup();
    vi.spyOn(navigator.clipboard, 'writeText').mockRejectedValueOnce(new Error('denied'));
    render(<CopyButton value="x" />);
    // Should not throw.
    await user.click(screen.getByRole('button'));
    // No Check icon should appear because the write failed.
    expect(screen.queryByTestId('CheckIcon')).not.toBeInTheDocument();
  });
});
