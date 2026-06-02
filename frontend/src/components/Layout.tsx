import { AppBar, Box, Container, Toolbar, Typography } from '@mui/material';
import LinkIcon from '@mui/icons-material/Link';
import { Link as RouterLink, Outlet } from 'react-router-dom';

const APP_VERSION = '0.1.0';

export function Layout() {
  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <AppBar position="sticky">
        <Toolbar>
          <Box
            component={RouterLink}
            to="/"
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
              textDecoration: 'none',
              color: 'inherit',
            }}
          >
            <LinkIcon color="primary" />
            <Typography variant="h6" component="span" sx={{ fontWeight: 700 }}>
              memcyco
            </Typography>
            <Typography variant="caption" color="text.secondary" sx={{ ml: 1 }}>
              URL shortener
            </Typography>
          </Box>
        </Toolbar>
      </AppBar>
      <Container component="main" maxWidth="xl" sx={{ flexGrow: 1, py: 3 }}>
        <Outlet />
      </Container>
      <Box
        component="footer"
        sx={{
          py: 1.5,
          textAlign: 'center',
          borderTop: '1px solid',
          borderColor: 'divider',
          color: 'text.secondary',
          backgroundColor: 'background.paper',
        }}
      >
        <Typography variant="caption">memcyco v{APP_VERSION}</Typography>
      </Box>
    </Box>
  );
}
