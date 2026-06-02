/**
 * TagsInput — chip-style multi-value text input. Users press Enter (or comma)
 * to commit a tag and click the chip's delete icon to remove it.
 *
 * Assumed prop shape (Agent B to confirm):
 *   { value: string[]; onChange: (next: string[]) => void; label?: string }
 */
// AGENT-E-TODO: depends on Agent B's <TagsInput> at src/components/TagsInput.tsx
import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '../test/renderWithProviders';
import { TagsInput } from './TagsInput';

describe('<TagsInput>', () => {
  it('adds a tag when the user types and presses Enter', async () => {
    const onChange = vi.fn();
    const { user } = renderWithProviders(
      <TagsInput value={[]} onChange={onChange} label="Tags" />,
    );
    const input = screen.getByLabelText(/tags/i);
    await user.type(input, 'marketing{enter}');
    expect(onChange).toHaveBeenCalledWith(['marketing']);
  });

  it('renders existing tags as chips', () => {
    renderWithProviders(<TagsInput value={['a', 'b']} onChange={vi.fn()} label="Tags" />);
    expect(screen.getByText('a')).toBeInTheDocument();
    expect(screen.getByText('b')).toBeInTheDocument();
  });

  it('removes a tag when its delete icon is clicked', async () => {
    const onChange = vi.fn();
    const { user } = renderWithProviders(
      <TagsInput value={['a', 'b']} onChange={onChange} label="Tags" />,
    );
    // MUI renders the delete icon as a sibling element with role='button' or
    // a CancelIcon. Look it up by data-testid first, then by label fallback.
    const deletes = document.querySelectorAll('.MuiChip-deleteIcon');
    expect(deletes.length).toBeGreaterThanOrEqual(2);
    await user.click(deletes[0] as Element);
    expect(onChange).toHaveBeenCalledWith(['b']);
  });
});
