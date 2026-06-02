import {
  Box,
  LinearProgress,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import { BreakdownEntry } from '../types';
import { truncate } from '../utils/format';

interface Props {
  title: string;
  rows: BreakdownEntry[];
  valueLabel?: string;
  emptyText?: string;
}

export function BreakdownTable({
  title,
  rows,
  valueLabel = 'Value',
  emptyText = 'No data',
}: Props) {
  const total = rows.reduce((sum, r) => sum + r.count, 0);

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Typography variant="h6" sx={{ mb: 1.5 }}>
        {title}
      </Typography>
      {rows.length === 0 ? (
        <Typography color="text.secondary" variant="body2" sx={{ py: 2 }}>
          {emptyText}
        </Typography>
      ) : (
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{valueLabel}</TableCell>
                <TableCell align="right" width={80}>
                  Count
                </TableCell>
                <TableCell width={160}>Share</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => {
                const pct = total > 0 ? (row.count / total) * 100 : 0;
                return (
                  <TableRow key={row.value} hover>
                    <TableCell title={row.value}>{truncate(row.value, 60) || '(empty)'}</TableCell>
                    <TableCell align="right">{row.count}</TableCell>
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Box sx={{ flexGrow: 1 }}>
                          <LinearProgress
                            variant="determinate"
                            value={pct}
                            sx={{ height: 6, borderRadius: 3 }}
                          />
                        </Box>
                        <Typography variant="caption" color="text.secondary" sx={{ minWidth: 36 }}>
                          {pct.toFixed(1)}%
                        </Typography>
                      </Box>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Paper>
  );
}
