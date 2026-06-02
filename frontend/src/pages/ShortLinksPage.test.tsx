/**
 * ShortLinksPage — the main DataGrid view.
 *
 * Flow covered:
 *   1. Loading → rows appear from MSW.
 *   2. "Create" button navigates to /links/new.
 *   3. Row "Delete" → confirm dialog → DELETE fires → row disappears.
 *   4. Status filter dropdown limits visible rows.
 */
import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders } from '../test/renderWithProviders';
import { ShortLinksPage } from './ShortLinksPage';

function renderPage(initialRoute = '/') {
  return renderWithProviders(
    <Routes>
      <Route path="/" element={<ShortLinksPage />} />
      <Route path="/links/new" element={<div data-testid="new-page">new</div>} />
      <Route path="/links/:id" element={<div data-testid="analytics-page">analytics</div>} />
      <Route path="/links/:id/edit" element={<div data-testid="edit-page">edit</div>} />
    </Routes>,
    { route: initialRoute },
  );
}

describe('<ShortLinksPage>', () => {
  it('renders rows fetched from the API', async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText('abc1234')).toBeInTheDocument();
    });
    expect(screen.getByText('def5678')).toBeInTheDocument();
  });

  it('navigates to /links/new when the Create button is clicked', async () => {
    const { user } = renderPage();
    await waitFor(() => {
      expect(screen.getByText('abc1234')).toBeInTheDocument();
    });
    await user.click(screen.getByRole('button', { name: /^create$/i }));
    await waitFor(() => {
      expect(screen.getByTestId('new-page')).toBeInTheDocument();
    });
  });

  it('removes a row after confirming delete', async () => {
    const { user } = renderPage();
    await waitFor(() => {
      expect(screen.getByText('abc1234')).toBeInTheDocument();
    });

    // Locate the row containing "abc1234" then click its delete action.
    // The DataGrid action item is a button with aria-label "Delete".
    const row = screen.getByText('abc1234').closest('[role="row"]') as HTMLElement;
    expect(row).toBeTruthy();
    const deleteBtn = within(row).getByRole('menuitem', { name: /delete/i });
    await user.click(deleteBtn);

    // Confirm dialog appears with "Delete" + "Cancel" buttons. Use the dialog
    // scope to disambiguate from row-level action items.
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: /delete/i }));

    await waitFor(
      () => {
        expect(screen.queryByText('abc1234')).not.toBeInTheDocument();
      },
      { timeout: 10_000 },
    );
    expect(screen.getByText('def5678')).toBeInTheDocument();
  }, 20_000);

  it('filters by status when the status dropdown is changed', async () => {
    const { user } = renderPage();
    await waitFor(() => {
      expect(screen.getByText('abc1234')).toBeInTheDocument();
    });

    // The Status filter Select is the FormControl labelled by status-filter-label.
    // There may be multiple "Status" labels (header column also says "Status"),
    // so target the labelledby element directly.
    const filterTrigger = document.querySelector(
      '[aria-labelledby="status-filter-label"]',
    ) as HTMLElement;
    expect(filterTrigger).toBeTruthy();
    await user.click(filterTrigger);

    const listbox = await screen.findByRole('listbox');
    await user.click(within(listbox).getByRole('option', { name: 'Expired' }));

    await waitFor(
      () => {
        expect(screen.queryByText('abc1234')).not.toBeInTheDocument();
      },
      { timeout: 10_000 },
    );
    expect(screen.getByText('def5678')).toBeInTheDocument();
  }, 20_000);
});
