/**
 * StrategySelect — wraps useStrategies() and renders a MUI Select with one
 * MenuItem per StrategyDescriptor. Shows a loading state while fetching.
 *
 * Real prop shape:
 *   { value: string; onChange: (name: string, descriptor: StrategyDescriptor | null) => void;
 *     disabled?: boolean; error?: string; required?: boolean }
 */
import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import { renderWithProviders } from '../test/renderWithProviders';
import { StrategySelect } from './StrategySelect';

function getSelectButton(): HTMLElement {
  // MUI Select's combobox sets `aria-labelledby="strategy-select-label
  // strategy-select"` (space-separated). Match by id="strategy-select" which
  // is the inner element the user clicks.
  const el = document.getElementById('strategy-select');
  if (!el) throw new Error('Strategy select trigger not found');
  return el;
}

describe('<StrategySelect>', () => {
  it('renders the strategies once the query resolves and exposes them as options', async () => {
    const onChange = vi.fn();
    const { user } = renderWithProviders(
      <StrategySelect value="RANDOM_BASE62" onChange={onChange} />,
    );

    // Once the query resolves the trigger should display "Random Base62".
    await waitFor(() => {
      expect(getSelectButton()).toHaveTextContent('Random Base62');
    });

    await user.click(getSelectButton());
    const listbox = await screen.findByRole('listbox');
    expect(within(listbox).getByRole('option', { name: /Random Base62/i })).toBeInTheDocument();
    expect(within(listbox).getByRole('option', { name: /Hash Truncation/i })).toBeInTheDocument();
    expect(within(listbox).getByRole('option', { name: /Sequential/i })).toBeInTheDocument();
    expect(within(listbox).getByRole('option', { name: /Custom Alias/i })).toBeInTheDocument();
  });

  it('fires onChange with the selected strategy name and descriptor', async () => {
    const onChange = vi.fn();
    const { user } = renderWithProviders(
      <StrategySelect value="RANDOM_BASE62" onChange={onChange} />,
    );
    await waitFor(() => {
      expect(getSelectButton()).toHaveTextContent('Random Base62');
    });
    await user.click(getSelectButton());
    const listbox = await screen.findByRole('listbox');
    await user.click(within(listbox).getByRole('option', { name: /Custom Alias/i }));
    expect(onChange).toHaveBeenCalledWith(
      'CUSTOM_ALIAS',
      expect.objectContaining({ name: 'CUSTOM_ALIAS' }),
    );
  });

  it('respects disabled prop', async () => {
    const onChange = vi.fn();
    renderWithProviders(
      <StrategySelect value="RANDOM_BASE62" onChange={onChange} disabled />,
    );
    await waitFor(() => {
      expect(getSelectButton()).toHaveAttribute('aria-disabled', 'true');
    });
  });

  it('surfaces an error message via FormHelperText', async () => {
    renderWithProviders(
      <StrategySelect value="RANDOM_BASE62" onChange={vi.fn()} error="Required" />,
    );
    await waitFor(() => {
      expect(screen.getByText('Required')).toBeInTheDocument();
    });
  });
});
