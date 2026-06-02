import { Chip, ChipProps } from '@mui/material';
import { LinkStatus } from '../types';

const STATUS_COLOR: Record<LinkStatus, { color: ChipProps['color']; label: string }> = {
  ACTIVE: { color: 'success', label: 'Active' },
  EXPIRED: { color: 'warning', label: 'Expired' },
  EXHAUSTED: { color: 'error', label: 'Exhausted' },
  INACTIVE: { color: 'default', label: 'Inactive' },
};

interface Props {
  status: LinkStatus;
  size?: ChipProps['size'];
}

export function StatusChip({ status, size = 'small' }: Props) {
  const cfg = STATUS_COLOR[status] ?? { color: 'default' as const, label: status };
  return <Chip size={size} color={cfg.color} label={cfg.label} variant="filled" />;
}
