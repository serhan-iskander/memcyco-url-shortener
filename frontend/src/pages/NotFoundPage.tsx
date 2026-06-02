import { Box, Button, Typography } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <Box sx={{ textAlign: 'center', py: 8 }}>
      <Typography variant="h3" sx={{ fontWeight: 700 }}>
        404
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mt: 1 }}>
        This page does not exist.
      </Typography>
      <Button component={RouterLink} to="/" variant="contained" sx={{ mt: 3 }}>
        Back to short links
      </Button>
    </Box>
  );
}
