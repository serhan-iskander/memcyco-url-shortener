/**
 * ShortLinkFormPage — create + edit flows.
 *
 * Real component derives mode from `useParams().id`; the route at /links/new
 * has no id and /links/:id/edit has one. Strategy parameters (including
 * `alias` for CUSTOM_ALIAS) are rendered inside <DynamicParamFields>, whose
 * label is `alias *`.
 */
import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders } from '../test/renderWithProviders';
import { ShortLinkFormPage } from './ShortLinkFormPage';
import { seed } from '../mocks/handlers';
import { makeShortLink } from '../mocks/fixtures';

function renderForm(route: string) {
  return renderWithProviders(
    <Routes>
      <Route path="/" element={<div data-testid="home">home</div>} />
      <Route path="/links/new" element={<ShortLinkFormPage />} />
      <Route path="/links/:id/edit" element={<ShortLinkFormPage />} />
    </Routes>,
    { route },
  );
}

function getStrategySelect(): HTMLElement {
  // The Strategy <Select> renders a div with id="strategy-select" (carrying
  // role="combobox"). Matching by id avoids the multi-token aria-labelledby
  // string MUI assembles.
  const el = document.getElementById('strategy-select');
  if (!el) throw new Error('Strategy select not found');
  return el;
}

async function selectStrategy(user: ReturnType<typeof renderForm>['user'], name: RegExp) {
  await user.click(getStrategySelect());
  const listbox = await screen.findByRole('listbox');
  await user.click(within(listbox).getByRole('option', { name }));
}

describe('<ShortLinkFormPage> — create flow', () => {
  it('shows the alias field when CUSTOM_ALIAS strategy is selected', async () => {
    const { user } = renderForm('/links/new');
    await waitFor(() => {
      expect(screen.getByLabelText(/original url/i)).toBeInTheDocument();
    });
    await selectStrategy(user, /Custom Alias/i);
    // DynamicParamFields renders an input labelled "alias *".
    await waitFor(
      () => {
        expect(screen.getByLabelText(/^alias/i)).toBeInTheDocument();
      },
      { timeout: 10_000 },
    );
  }, 20_000);

  it('shows server-side inline error when alias is already taken', async () => {
    const { user } = renderForm('/links/new');
    await waitFor(() => {
      expect(screen.getByLabelText(/original url/i)).toBeInTheDocument();
    });
    await user.type(screen.getByLabelText(/original url/i), 'https://example.com');
    await selectStrategy(user, /Custom Alias/i);
    const aliasInput = await screen.findByLabelText(/^alias/i);
    await user.type(aliasInput, 'taken');
    await user.click(screen.getByRole('button', { name: /create short link/i }));
    await waitFor(
      () => {
        expect(screen.getAllByText(/already in use/i).length).toBeGreaterThan(0);
      },
      { timeout: 10_000 },
    );
  }, 20_000);

  it('shows client-side error for an invalid alias pattern', async () => {
    const { user } = renderForm('/links/new');
    await waitFor(() => {
      expect(screen.getByLabelText(/original url/i)).toBeInTheDocument();
    });
    await user.type(screen.getByLabelText(/original url/i), 'https://example.com');
    await selectStrategy(user, /Custom Alias/i);
    const aliasInput = await screen.findByLabelText(/^alias/i);
    await user.type(aliasInput, '!!');
    await user.click(screen.getByRole('button', { name: /create short link/i }));
    await waitFor(
      () => {
        // validateAlias() returns "Alias must be 3–32 chars (letters, digits, _ or -)".
        expect(screen.getAllByText(/3.{1,3}32|letters, digits/i).length).toBeGreaterThan(0);
      },
      { timeout: 10_000 },
    );
  }, 20_000);

  it('navigates home after a successful create', async () => {
    const { user } = renderForm('/links/new');
    await waitFor(() => {
      expect(screen.getByLabelText(/original url/i)).toBeInTheDocument();
    });
    await user.type(screen.getByLabelText(/original url/i), 'https://example.com/new');
    await selectStrategy(user, /Random Base62/i);
    await user.click(screen.getByRole('button', { name: /create short link/i }));
    await waitFor(
      () => {
        expect(screen.getByTestId('home')).toBeInTheDocument();
      },
      { timeout: 10_000 },
    );
  }, 20_000);

  it('shows a client-side error when maxClicks is below 1', async () => {
    const { user } = renderForm('/links/new');
    await waitFor(() => {
      expect(screen.getByLabelText(/original url/i)).toBeInTheDocument();
    });
    await user.type(screen.getByLabelText(/original url/i), 'https://example.com');
    await selectStrategy(user, /Random Base62/i);
    // `validateMaxClicks` rejects values below 1. The TextField type="number"
    // happily accepts a negative integer in jsdom.
    const maxClicks = screen.getByLabelText(/max clicks/i) as HTMLInputElement;
    await user.type(maxClicks, '0');
    await user.click(screen.getByRole('button', { name: /create short link/i }));
    await waitFor(
      () => {
        expect(screen.getAllByText(/at least 1/i).length).toBeGreaterThan(0);
      },
      { timeout: 10_000 },
    );
  }, 20_000);
});

describe('<ShortLinkFormPage> — edit flow', () => {
  it('pre-fills the form with the existing values and disables strategy', async () => {
    seed(
      makeShortLink({
        id: 42,
        shortCode: 'edit42',
        originalUrl: 'https://example.com/original',
        strategy: 'RANDOM_BASE62',
        tags: ['campaign'],
        clickCount: 0,
        status: 'ACTIVE',
      }),
    );
    renderForm('/links/42/edit');
    await waitFor(() => {
      const urlInput = screen.getByLabelText(/original url/i) as HTMLInputElement;
      expect(urlInput.value).toBe('https://example.com/original');
    });
    await waitFor(() => {
      expect(getStrategySelect()).toHaveAttribute('aria-disabled', 'true');
    });
  });

  it('submits a PUT and navigates home on success', async () => {
    seed(
      makeShortLink({
        id: 99,
        shortCode: 'edit99',
        originalUrl: 'https://example.com/original',
        strategy: 'RANDOM_BASE62',
      }),
    );
    const { user } = renderForm('/links/99/edit');
    const urlInput = (await screen.findByLabelText(/original url/i)) as HTMLInputElement;
    await waitFor(() => {
      expect(urlInput.value).toBe('https://example.com/original');
    });
    await user.clear(urlInput);
    await user.type(urlInput, 'https://example.com/updated');
    await user.click(screen.getByRole('button', { name: /save changes/i }));
    await waitFor(() => {
      expect(screen.getByTestId('home')).toBeInTheDocument();
    });
  });
});
