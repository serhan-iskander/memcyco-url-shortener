/**
 * BreakdownTable — renders a list of `{ value, count }` rows alongside the
 * percentage each contributes to the total. The component renders in the
 * order it receives; the caller is responsible for pre-sorting.
 *
 * Real prop shape:
 *   { title: string; rows: BreakdownEntry[]; valueLabel?: string; emptyText?: string }
 */
import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { ThemeProvider } from '@mui/material';
import { theme } from '../theme';
import { BreakdownTable } from './BreakdownTable';

function renderWithTheme(ui: React.ReactElement) {
  return render(<ThemeProvider theme={theme}>{ui}</ThemeProvider>);
}

describe('<BreakdownTable>', () => {
  // Pre-sorted in caller-determined order; component preserves it.
  const rows = [
    { value: 'twitter.com', count: 60 },
    { value: 'reddit.com', count: 30 },
    { value: 'news.ycombinator.com', count: 10 },
  ];

  it('renders rows in the caller-supplied order', () => {
    renderWithTheme(<BreakdownTable title="Top Referers" rows={rows} />);
    const bodyRows = screen.getAllByRole('row').slice(1); // skip header
    expect(within(bodyRows[0]).getByText('twitter.com')).toBeInTheDocument();
    expect(within(bodyRows[1]).getByText('reddit.com')).toBeInTheDocument();
    expect(within(bodyRows[2]).getByText('news.ycombinator.com')).toBeInTheDocument();
  });

  it('displays per-row percentages that sum to approximately 100%', () => {
    renderWithTheme(<BreakdownTable title="Top Referers" rows={rows} />);
    // The percentage captions render as MuiTypography-caption nodes with text
    // like "60.0%". Filter to those exact elements — LinearProgress's
    // inline transform style also contains "%" and would pollute a naive
    // textContent scan.
    const captions = Array.from(document.querySelectorAll('.MuiTypography-caption'))
      .map((el) => el.textContent?.trim() ?? '')
      .filter((t) => /^\d+(?:\.\d+)?%$/.test(t));
    expect(captions.length).toBe(rows.length);
    const sum = captions.reduce((acc, t) => acc + Number(t.replace('%', '')), 0);
    expect(sum).toBeGreaterThanOrEqual(99);
    expect(sum).toBeLessThanOrEqual(101);
  });

  it('shows the table title', () => {
    renderWithTheme(<BreakdownTable title="Top Referers" rows={rows} />);
    expect(screen.getByText('Top Referers')).toBeInTheDocument();
  });

  it('renders an empty state when given no rows', () => {
    renderWithTheme(
      <BreakdownTable title="Top Referers" rows={[]} emptyText="Nothing here" />,
    );
    expect(screen.getByText('Nothing here')).toBeInTheDocument();
  });

  it('respects a custom valueLabel', () => {
    renderWithTheme(
      <BreakdownTable title="Top UAs" rows={rows} valueLabel="User agent" />,
    );
    expect(screen.getByText('User agent')).toBeInTheDocument();
  });
});
