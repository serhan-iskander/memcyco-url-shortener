import dayjs from 'dayjs';

export function formatDate(value: string | null | undefined): string {
  if (!value) return '—';
  const d = dayjs(value);
  if (!d.isValid()) return value;
  return d.format('YYYY-MM-DD HH:mm');
}

export function formatRelative(value: string | null | undefined): string {
  if (!value) return '—';
  const d = dayjs(value);
  if (!d.isValid()) return value;
  const diff = d.diff(dayjs(), 'minute');
  if (Math.abs(diff) < 60) return `${diff} min`;
  return d.format('YYYY-MM-DD HH:mm');
}

export function truncate(value: string, max = 60): string {
  if (!value) return '';
  if (value.length <= max) return value;
  return value.slice(0, max - 1) + '…';
}
