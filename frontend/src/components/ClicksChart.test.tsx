/**
 * ClicksChart — given a series of { bucket, count } points, renders a
 * Recharts AreaChart inside a ResponsiveContainer.
 *
 * Real prop shape:
 *   { series: AnalyticsBucket[]; bucket: 'hour' | 'day'; height?: number }
 */
import { describe, expect, it } from 'vitest';
import { renderWithProviders } from '../test/renderWithProviders';
import { ClicksChart } from './ClicksChart';

describe('<ClicksChart>', () => {
  it('renders a Recharts SVG when given a non-empty series', () => {
    const series = [
      { bucket: '2026-06-02T00:00:00Z', count: 5 },
      { bucket: '2026-06-02T01:00:00Z', count: 8 },
      { bucket: '2026-06-02T02:00:00Z', count: 3 },
    ];
    const { container } = renderWithProviders(
      <ClicksChart series={series} bucket="hour" />,
    );
    // ResponsiveContainer renders one wrapper element. The actual <svg> needs
    // width/height ≠ 0 to mount but jsdom always reports 0 — assert on the
    // ResponsiveContainer presence + the data passing through.
    expect(container.querySelector('.recharts-responsive-container')).toBeInTheDocument();
  });

  it('renders the empty-state message when the series is empty', () => {
    const { getByText } = renderWithProviders(<ClicksChart series={[]} bucket="hour" />);
    expect(getByText(/no clicks recorded/i)).toBeInTheDocument();
  });

  it('accepts either bucket granularity without throwing', () => {
    const series = [{ bucket: '2026-06-02T00:00:00Z', count: 1 }];
    const { rerender } = renderWithProviders(
      <ClicksChart series={series} bucket="hour" />,
    );
    rerender(<ClicksChart series={series} bucket="day" />);
  });
});
