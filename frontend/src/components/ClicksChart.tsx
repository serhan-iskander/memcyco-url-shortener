import { Box, Typography, useTheme } from '@mui/material';
import dayjs from 'dayjs';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { AnalyticsBucket, BucketGranularity } from '../types';

interface Props {
  series: AnalyticsBucket[];
  bucket: BucketGranularity;
  height?: number;
}

export function ClicksChart({ series, bucket, height = 280 }: Props) {
  const theme = useTheme();
  const fmt = bucket === 'hour' ? 'MMM D HH:mm' : 'MMM D';

  const shaped = (series ?? []).map((p) => ({
    bucket: p.bucket,
    label: dayjs(p.bucket).format(fmt),
    count: p.count,
  }));

  if (shaped.length === 0) {
    return (
      <Box
        sx={{
          height,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'text.secondary',
        }}
      >
        <Typography variant="body2">No clicks recorded in this window yet.</Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ width: '100%', height }}>
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={shaped} margin={{ top: 10, right: 16, bottom: 0, left: 0 }}>
          <defs>
            <linearGradient id="clicksGradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={theme.palette.primary.main} stopOpacity={0.4} />
              <stop offset="100%" stopColor={theme.palette.primary.main} stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke={theme.palette.divider} />
          <XAxis
            dataKey="label"
            tick={{ fontSize: 12, fill: theme.palette.text.secondary }}
            minTickGap={20}
          />
          <YAxis
            allowDecimals={false}
            tick={{ fontSize: 12, fill: theme.palette.text.secondary }}
          />
          <Tooltip
            labelFormatter={(_, payload) => {
              const p = payload?.[0]?.payload as { bucket?: string } | undefined;
              return p?.bucket ? dayjs(p.bucket).format('YYYY-MM-DD HH:mm') : '';
            }}
            formatter={(value: number) => [value, 'Clicks']}
            contentStyle={{
              backgroundColor: theme.palette.background.paper,
              borderRadius: 8,
              border: `1px solid ${theme.palette.divider}`,
            }}
          />
          <Area
            type="monotone"
            dataKey="count"
            stroke={theme.palette.primary.main}
            strokeWidth={2}
            fill="url(#clicksGradient)"
          />
        </AreaChart>
      </ResponsiveContainer>
    </Box>
  );
}
