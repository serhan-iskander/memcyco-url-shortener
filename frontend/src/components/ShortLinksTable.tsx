import {
  Box,
  Chip,
  IconButton,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';
import {
  DataGrid,
  GridActionsCellItem,
  GridColDef,
  GridRenderCellParams,
  GridRowParams,
} from '@mui/x-data-grid';
import EditIcon from '@mui/icons-material/Edit';
import BarChartIcon from '@mui/icons-material/BarChart';
import DeleteIcon from '@mui/icons-material/Delete';
import QrCode2Icon from '@mui/icons-material/QrCode2';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import { ShortLinkResponse } from '../types';
import { StatusChip } from './StatusChip';
import { CopyButton } from './CopyButton';
import { buildShortUrl } from '../api';
import { formatDate, truncate } from '../utils/format';

interface Props {
  rows: ShortLinkResponse[];
  loading?: boolean;
  onEdit: (link: ShortLinkResponse) => void;
  onAnalytics: (link: ShortLinkResponse) => void;
  onDelete: (link: ShortLinkResponse) => void;
  onQr: (link: ShortLinkResponse) => void;
}

export function ShortLinksTable({ rows, loading, onEdit, onAnalytics, onDelete, onQr }: Props) {
  const columns: GridColDef<ShortLinkResponse>[] = [
    {
      field: 'shortCode',
      headerName: 'Short code',
      flex: 1,
      minWidth: 220,
      renderCell: (params: GridRenderCellParams<ShortLinkResponse>) => {
        const fullUrl = buildShortUrl(params.row.shortCode);
        return (
          <Stack direction="row" alignItems="center" spacing={0.5} sx={{ width: '100%' }}>
            <Tooltip title={fullUrl}>
              <Typography
                component="a"
                href={fullUrl}
                target="_blank"
                rel="noreferrer noopener"
                sx={{
                  color: 'primary.main',
                  textDecoration: 'none',
                  fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                  fontSize: 13,
                  '&:hover': { textDecoration: 'underline' },
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
              >
                {params.row.shortCode}
              </Typography>
            </Tooltip>
            <CopyButton value={fullUrl} title="Copy short URL" />
          </Stack>
        );
      },
    },
    {
      field: 'originalUrl',
      headerName: 'Original URL',
      flex: 2,
      minWidth: 260,
      renderCell: (params: GridRenderCellParams<ShortLinkResponse>) => (
        <Tooltip title={params.row.originalUrl}>
          <Box
            component="a"
            href={params.row.originalUrl}
            target="_blank"
            rel="noreferrer noopener"
            sx={{
              color: 'text.primary',
              textDecoration: 'none',
              display: 'inline-flex',
              alignItems: 'center',
              gap: 0.5,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              '&:hover': { textDecoration: 'underline' },
            }}
          >
            <OpenInNewIcon fontSize="inherit" sx={{ fontSize: 14, opacity: 0.6 }} />
            {truncate(params.row.originalUrl, 80)}
          </Box>
        </Tooltip>
      ),
    },
    {
      field: 'clickCount',
      headerName: 'Clicks',
      type: 'number',
      width: 90,
      align: 'right',
      headerAlign: 'right',
    },
    {
      field: 'status',
      headerName: 'Status',
      width: 130,
      renderCell: (params: GridRenderCellParams<ShortLinkResponse>) => (
        <StatusChip status={params.row.status} />
      ),
    },
    {
      field: 'tags',
      headerName: 'Tags',
      flex: 1,
      minWidth: 160,
      sortable: false,
      renderCell: (params: GridRenderCellParams<ShortLinkResponse>) => (
        <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', gap: 0.5 }}>
          {params.row.tags.map((t) => (
            <Chip key={t} label={t} size="small" variant="outlined" />
          ))}
        </Stack>
      ),
    },
    {
      field: 'createdAt',
      headerName: 'Created',
      width: 170,
      valueFormatter: (value: string) => formatDate(value),
    },
    {
      field: 'actions',
      type: 'actions',
      headerName: 'Actions',
      width: 170,
      getActions: (params: GridRowParams<ShortLinkResponse>) => [
        <GridActionsCellItem
          key="analytics"
          icon={
            <Tooltip title="Analytics">
              <BarChartIcon />
            </Tooltip>
          }
          label="Analytics"
          onClick={() => onAnalytics(params.row)}
        />,
        <GridActionsCellItem
          key="edit"
          icon={
            <Tooltip title="Edit">
              <EditIcon />
            </Tooltip>
          }
          label="Edit"
          onClick={() => onEdit(params.row)}
        />,
        <GridActionsCellItem
          key="qr"
          icon={
            <Tooltip title="QR code">
              <QrCode2Icon />
            </Tooltip>
          }
          label="QR"
          onClick={() => onQr(params.row)}
        />,
        <GridActionsCellItem
          key="delete"
          icon={
            <Tooltip title="Delete">
              <IconButton size="small" color="error" sx={{ p: 0 }}>
                <DeleteIcon />
              </IconButton>
            </Tooltip>
          }
          label="Delete"
          onClick={() => onDelete(params.row)}
        />,
      ],
    },
  ];

  return (
    <DataGrid
      rows={rows}
      columns={columns}
      loading={loading}
      autoHeight
      disableRowSelectionOnClick
      pageSizeOptions={[10, 20, 50, 100]}
      initialState={{ pagination: { paginationModel: { pageSize: 20, page: 0 } } }}
      sx={{
        border: 0,
        '& .MuiDataGrid-columnHeaders': { backgroundColor: 'background.default' },
        '& .MuiDataGrid-cell:focus, & .MuiDataGrid-cell:focus-within': { outline: 'none' },
      }}
    />
  );
}
