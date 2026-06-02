/**
 * AnalyticsPage — KPI cards, bucket toggle (Hourly/Daily), breakdown tables.
 *
 * Real component uses `(value ?? 0).toLocaleString()` on each KPI so the
 * rendered value for 1234 is "1,234". Bucket toggles read "Hourly" / "Daily"
 * with values `hour` and `day`.
 */
import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { renderWithProviders } from '../test/renderWithProviders';
import { AnalyticsPage } from './AnalyticsPage';
import { server } from '../mocks/server';
import { makeAnalytics } from '../mocks/fixtures';

function renderAnalytics(route = '/links/1') {
  return renderWithProviders(
    <Routes>
      <Route path="/links/:id" element={<AnalyticsPage />} />
    </Routes>,
    { route },
  );
}

describe('<AnalyticsPage>', () => {
  it('renders KPI numbers from the analytics response (locale-formatted)', async () => {
    server.use(
      http.get('/api/short-links/1/analytics', () =>
        HttpResponse.json(
          makeAnalytics({
            shortLinkId: 1,
            totalClicks: 1234,
            last24hClicks: 56,
            uniqueReferers: 7,
          }),
        ),
      ),
    );
    renderAnalytics('/links/1');
    await waitFor(() => {
      // toLocaleString('en') renders 1234 → "1,234".
      expect(screen.getByText('1,234')).toBeInTheDocument();
    });
    expect(screen.getByText('56')).toBeInTheDocument();
    expect(screen.getByText('7')).toBeInTheDocument();
  });

  it('refetches when the bucket toggle is flipped from Hourly to Daily', async () => {
    // Two distinct responses based on the bucket query param.
    server.use(
      http.get('/api/short-links/1/analytics', ({ request }) => {
        const bucket = new URL(request.url).searchParams.get('bucket');
        const totalClicks = bucket === 'day' ? 999 : 111;
        return HttpResponse.json(
          makeAnalytics({ shortLinkId: 1, totalClicks }),
        );
      }),
    );

    const { user } = renderAnalytics('/links/1');
    await waitFor(() => {
      expect(screen.getByText('111')).toBeInTheDocument();
    });
    await user.click(screen.getByRole('button', { name: /^daily$/i }));
    await waitFor(() => {
      expect(screen.getByText('999')).toBeInTheDocument();
    });
  });

  it('renders the top referers breakdown rows', async () => {
    server.use(
      http.get('/api/short-links/1/analytics', () =>
        HttpResponse.json(
          makeAnalytics({
            shortLinkId: 1,
            topReferers: [
              { value: 'bbb.com', count: 70 },
              { value: 'ccc.com', count: 20 },
              { value: 'aaa.com', count: 10 },
            ],
          }),
        ),
      ),
    );
    renderAnalytics('/links/1');
    await waitFor(() => {
      expect(screen.getByText('bbb.com')).toBeInTheDocument();
    });
    expect(screen.getByText('aaa.com')).toBeInTheDocument();
    expect(screen.getByText('ccc.com')).toBeInTheDocument();

    // Percentages should sum to ~100% per table. Filter to only the typography
    // captions to avoid LinearProgress's translate transform style.
    const captions = Array.from(document.querySelectorAll('.MuiTypography-caption'))
      .map((el) => el.textContent?.trim() ?? '')
      .filter((t) => /^\d+(?:\.\d+)?%$/.test(t))
      .map((t) => Number(t.replace('%', '')));
    expect(captions.length).toBeGreaterThanOrEqual(3);
    // Two tables × 3 rows = 6 percentages. At least one consecutive cluster
    // of 3 should sum to ~100.
    let foundCluster = false;
    for (let i = 0; i + 2 < captions.length; i += 3) {
      const total = captions.slice(i, i + 3).reduce((a, b) => a + b, 0);
      if (total >= 99 && total <= 101) {
        foundCluster = true;
        break;
      }
    }
    expect(foundCluster).toBe(true);
  });
});
