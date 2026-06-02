import {
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Typography,
} from '@mui/material';
import DownloadIcon from '@mui/icons-material/Download';
import { useEffect, useState } from 'react';
import { shortLinksApi } from '../api';

interface Props {
  open: boolean;
  shortLinkId: number | null;
  shortCode?: string;
  onClose: () => void;
}

export function QrDialog({ open, shortLinkId, shortCode, onClose }: Props) {
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    let createdUrl: string | null = null;

    if (open && shortLinkId !== null) {
      setLoading(true);
      setError(null);
      setBlobUrl(null);
      shortLinksApi
        .qrBlob(shortLinkId, 512)
        .then((blob) => {
          if (cancelled) return;
          createdUrl = URL.createObjectURL(blob);
          setBlobUrl(createdUrl);
        })
        .catch((e: Error) => {
          if (!cancelled) setError(e.message || 'Failed to load QR code');
        })
        .finally(() => {
          if (!cancelled) setLoading(false);
        });
    }

    return () => {
      cancelled = true;
      if (createdUrl) URL.revokeObjectURL(createdUrl);
    };
  }, [open, shortLinkId]);

  const handleDownload = () => {
    if (!blobUrl) return;
    const a = document.createElement('a');
    a.href = blobUrl;
    a.download = `${shortCode ?? 'short-link'}-qr.png`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>QR code{shortCode ? ` — ${shortCode}` : ''}</DialogTitle>
      <DialogContent>
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            minHeight: 280,
          }}
        >
          {loading ? (
            <CircularProgress />
          ) : error ? (
            <Typography color="error">{error}</Typography>
          ) : blobUrl ? (
            <img
              src={blobUrl}
              alt={`QR code for ${shortCode}`}
              style={{ width: '100%', maxWidth: 320, height: 'auto' }}
            />
          ) : null}
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        <Button
          variant="contained"
          startIcon={<DownloadIcon />}
          disabled={!blobUrl}
          onClick={handleDownload}
        >
          Download PNG
        </Button>
      </DialogActions>
    </Dialog>
  );
}
