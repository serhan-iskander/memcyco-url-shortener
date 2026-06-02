/**
 * StatusChip — renders the right label + colour for each LinkStatus.
 */
import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { StatusChip } from './StatusChip';
import type { LinkStatus } from '../types';

describe('<StatusChip>', () => {
  const cases: Array<{ status: LinkStatus; label: string }> = [
    { status: 'ACTIVE', label: 'Active' },
    { status: 'EXPIRED', label: 'Expired' },
    { status: 'EXHAUSTED', label: 'Exhausted' },
    { status: 'INACTIVE', label: 'Inactive' },
  ];

  it.each(cases)('renders "%s" → label "$label"', ({ status, label }) => {
    render(<StatusChip status={status} />);
    expect(screen.getByText(label)).toBeInTheDocument();
  });

  it('falls back to raw status when unknown', () => {
    // Cast through unknown to bypass the strict literal type for this defensive case.
    render(<StatusChip status={'WAT' as unknown as LinkStatus} />);
    expect(screen.getByText('WAT')).toBeInTheDocument();
  });
});
