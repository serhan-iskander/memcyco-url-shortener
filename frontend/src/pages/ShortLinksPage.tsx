import { useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  FormControl,
  InputAdornment,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Toolbar,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import SearchIcon from '@mui/icons-material/Search';
import { useNavigate } from 'react-router-dom';
import {
  ConfirmDialog,
  QrDialog,
  ShortLinksTable,
} from '../components';
import { useDeleteShortLink, useShortLinks } from '../hooks';
import { useSnackbar } from '../snackbar/SnackbarProvider';
import { ApiError, LinkStatus, ShortLinkResponse } from '../types';

const STATUS_OPTIONS: { value: '' | LinkStatus; label: string }[] = [
  { value: '', label: 'All statuses' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'EXPIRED', label: 'Expired' },
  { value: 'EXHAUSTED', label: 'Exhausted' },
  { value: 'INACTIVE', label: 'Inactive' },
];

export function ShortLinksPage() {
  const navigate = useNavigate();
  const snackbar = useSnackbar();
  const [search, setSearch] = useState('');
  const [tagFilter, setTagFilter] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<'' | LinkStatus>('');
  const [pendingDelete, setPendingDelete] = useState<ShortLinkResponse | null>(null);
  const [qrTarget, setQrTarget] = useState<ShortLinkResponse | null>(null);

  // We fetch a large page client-side; the filter inputs are applied locally
  // to keep the experience snappy. For larger datasets we'd push the search
  // string to the backend, but the contract only exposes tag/status filters.
  const { data, isLoading, isError, error } = useShortLinks({ size: 100, page: 0 });
  const deleteMutation = useDeleteShortLink();

  const allItems = data?.items ?? [];

  const allTags = useMemo(() => {
    const set = new Set<string>();
    allItems.forEach((item) => item.tags.forEach((t) => set.add(t)));
    return Array.from(set).sort();
  }, [allItems]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return allItems.filter((item) => {
      if (statusFilter && item.status !== statusFilter) return false;
      if (tagFilter && !item.tags.includes(tagFilter)) return false;
      if (q) {
        const haystack = `${item.shortCode} ${item.originalUrl} ${item.tags.join(' ')}`.toLowerCase();
        if (!haystack.includes(q)) return false;
      }
      return true;
    });
  }, [allItems, search, tagFilter, statusFilter]);

  const handleDeleteConfirm = async () => {
    if (!pendingDelete) return;
    try {
      await deleteMutation.mutateAsync(pendingDelete.id);
      snackbar.success(`Deleted ${pendingDelete.shortCode}`);
      setPendingDelete(null);
    } catch (e) {
      const msg = e instanceof ApiError ? e.detail || e.title : 'Failed to delete link';
      snackbar.error(msg);
    }
  };

  return (
    <Stack spacing={3}>
      <Stack direction="row" alignItems="center" justifyContent="space-between">
        <Box>
          <Typography variant="h4">Short links</Typography>
          <Typography variant="body2" color="text.secondary">
            Manage shortened URLs, view analytics, and grab QR codes.
          </Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => navigate('/links/new')}
          size="large"
        >
          Create
        </Button>
      </Stack>

      <Paper variant="outlined">
        <Toolbar sx={{ gap: 2, flexWrap: 'wrap', py: 1.5 }}>
          <TextField
            placeholder="Search code, URL or tag…"
            size="small"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" />
                </InputAdornment>
              ),
            }}
            sx={{ minWidth: 280, flexGrow: 1 }}
          />
          <FormControl size="small" sx={{ minWidth: 160 }}>
            <InputLabel id="status-filter-label">Status</InputLabel>
            <Select
              labelId="status-filter-label"
              label="Status"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as '' | LinkStatus)}
            >
              {STATUS_OPTIONS.map((opt) => (
                <MenuItem key={opt.value} value={opt.value}>
                  {opt.label}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <FormControl size="small" sx={{ minWidth: 160 }}>
            <InputLabel id="tag-filter-label">Tag</InputLabel>
            <Select
              labelId="tag-filter-label"
              label="Tag"
              value={tagFilter}
              onChange={(e) => setTagFilter(e.target.value)}
            >
              <MenuItem value="">All tags</MenuItem>
              {allTags.map((tag) => (
                <MenuItem key={tag} value={tag}>
                  {tag}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <Typography variant="caption" color="text.secondary">
            {filtered.length} of {allItems.length}
          </Typography>
        </Toolbar>

        {isError ? (
          <Alert severity="error" sx={{ m: 2 }}>
            Failed to load short links
            {error instanceof ApiError && error.detail ? `: ${error.detail}` : ''}
          </Alert>
        ) : null}

        <Box sx={{ minHeight: 320 }}>
          <ShortLinksTable
            rows={filtered}
            loading={isLoading}
            onEdit={(link) => navigate(`/links/${link.id}/edit`)}
            onAnalytics={(link) => navigate(`/links/${link.id}`)}
            onDelete={(link) => setPendingDelete(link)}
            onQr={(link) => setQrTarget(link)}
          />
        </Box>
      </Paper>

      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete short link?"
        message={
          pendingDelete
            ? `This will soft-delete ${pendingDelete.shortCode}. Its analytics history is preserved, but the link will stop redirecting.`
            : ''
        }
        confirmLabel="Delete"
        confirmColor="error"
        loading={deleteMutation.isPending}
        onConfirm={handleDeleteConfirm}
        onCancel={() => setPendingDelete(null)}
      />

      <QrDialog
        open={qrTarget !== null}
        shortLinkId={qrTarget?.id ?? null}
        shortCode={qrTarget?.shortCode}
        onClose={() => setQrTarget(null)}
      />
    </Stack>
  );
}
