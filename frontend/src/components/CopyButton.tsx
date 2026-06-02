import { useState } from 'react';
import { IconButton, Tooltip } from '@mui/material';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import CheckIcon from '@mui/icons-material/Check';

interface Props {
  value: string;
  title?: string;
  size?: 'small' | 'medium';
}

export function CopyButton({ value, title = 'Copy', size = 'small' }: Props) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async (e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      // Clipboard failure (insecure context, denied permission) — silently no-op;
      // the user can still copy manually from the tooltip text.
    }
  };

  return (
    <Tooltip title={copied ? 'Copied!' : title}>
      <IconButton size={size} onClick={handleCopy} aria-label={title}>
        {copied ? <CheckIcon fontSize="small" color="success" /> : <ContentCopyIcon fontSize="small" />}
      </IconButton>
    </Tooltip>
  );
}
