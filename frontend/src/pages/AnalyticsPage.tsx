import { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Grid,
  Paper,
  Stack,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { useAnalytics, useShortLink } from '../hooks';
import { BreakdownTable, ClicksChart, CopyButton, StatusChip } from '../components';
import { ApiError, BucketGranularity } from '../types';
import { buildShortUrl } from '../api';
import { formatDate } from '../utils/format';

interface KpiCardProps {
  label: string;
  value: number | string;
  caption?: string;
}

function KpiCard({ label, value, caption }: KpiCardProps) {
  return (
    <Paper variant="outlined" sx={{ p: 2.5, height: '100%' }}>
      <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase' }}>
        {label}
      </Typography>
      <Typography variant="h4" sx={{ fontWeight: 700, my: 0.5 }}>
        {value}
      </Typography>
      {caption ? (
        <Typography variant="caption" color="text.secondary">
          {caption}
        </Typography>
      ) : null}
    </Paper>
  );
}

export function AnalyticsPage() {
  const { id } = useParams<{ id: string }>();
  const numericId = id ? Number(id) : undefined;
  const [bucket, setBucket] = useState<BucketGranularity>('hour');

  const linkQuery = useShortLink(numericId);
  const analyticsQuery = useAnalytics(numericId, bucket);

  if (linkQuery.isLoading || analyticsQuery.isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (linkQuery.isError) {
    return (
      <Alert severity="error">
        Failed to load short link
        {linkQuery.error instanceof ApiError && linkQuery.error.detail
          ? `: ${linkQuery.error.detail}`
          : '.'}
      </Alert>
    );
  }

  const link = linkQuery.data!;
  const analytics = analyticsQuery.data;
  const shortUrl = buildShortUrl(link.shortCode);

  return (
    <Stack spacing={3}>
      <Stack direction="row" alignItems="center" spacing={1}>
        <Button component={RouterLink} to="/" startIcon={<ArrowBackIcon />} color="inherit">
          Back to links
        </Button>
      </Stack>

      <Paper variant="outlined" sx={{ p: 3 }}>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems={{ md: 'center' }}>
          <Box sx={{ flexGrow: 1 }}>
            <Stack direction="row" alignItems="center" spacing={1.5}>
              <Typography
                variant="h5"
                component="a"
                href={shortUrl}
                target="_blank"
                rel="noreferrer noopener"
                sx={{
                  fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                  color: 'primary.main',
                  textDecoration: 'none',
                  '&:hover': { textDecoration: 'underline' },
                }}
              >
                /{link.shortCode}
              </Typography>
              <CopyButton value={shortUrl} title="Copy short URL" />
              <StatusChip status={link.status} />
            </Stack>
            <Typography
              variant="body2"
              color="text.secondary"
              sx={{
                mt: 0.5,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                maxWidth: 720,
              }}
              title={link.originalUrl}
            >
              → {link.originalUrl}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Created {formatDate(link.createdAt)}
              {link.expiresAt ? ` · expires ${formatDate(link.expiresAt)}` : ''}
              {link.maxClicks ? ` · max ${link.maxClicks.toLocaleString()} clicks` : ''}
            </Typography>
          </Box>
          <Button
            component={RouterLink}
            to={`/links/${link.id}/edit`}
            variant="outlined"
            sx={{ alignSelf: { md: 'center' } }}
          >
            Edit link
          </Button>
        </Stack>
      </Paper>

      <Grid container spacing={2}>
        <Grid item xs={12} md={4}>
          <KpiCard label="Total clicks" value={(analytics?.totalClicks ?? 0).toLocaleString()} />
        </Grid>
        <Grid item xs={12} md={4}>
          <KpiCard
            label="Last 24 hours"
            value={(analytics?.last24hClicks ?? 0).toLocaleString()}
            caption="Clicks in the past day"
          />
        </Grid>
        <Grid item xs={12} md={4}>
          <KpiCard
            label="Unique referers"
            value={(analytics?.uniqueReferers ?? 0).toLocaleString()}
            caption="Distinct sources sending traffic"
          />
        </Grid>
      </Grid>

      <Paper variant="outlined" sx={{ p: 3 }}>
        <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2 }}>
          <Typography variant="h6">Clicks over time</Typography>
          <ToggleButtonGroup
            size="small"
            value={bucket}
            exclusive
            onChange={(_, next) => {
              if (next) setBucket(next as BucketGranularity);
            }}
          >
            <ToggleButton value="hour">Hourly</ToggleButton>
            <ToggleButton value="day">Daily</ToggleButton>
          </ToggleButtonGroup>
        </Stack>
        {analyticsQuery.isError ? (
          <Alert severity="error">Failed to load analytics</Alert>
        ) : (
          <ClicksChart series={analytics?.series ?? []} bucket={bucket} />
        )}
      </Paper>

      <Grid container spacing={2}>
        <Grid item xs={12} md={6}>
          <BreakdownTable
            title="Top referers"
            rows={analytics?.topReferers ?? []}
            valueLabel="Referer"
            emptyText="No referers recorded yet"
          />
        </Grid>
        <Grid item xs={12} md={6}>
          <BreakdownTable
            title="Top user agents"
            rows={analytics?.topUserAgents ?? []}
            valueLabel="User agent"
            emptyText="No user agents recorded yet"
          />
        </Grid>
      </Grid>
    </Stack>
  );
}
