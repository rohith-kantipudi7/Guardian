import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { CssBaseline, ThemeProvider } from '@mui/material';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { Layout } from './components/Layout';
import { About } from './pages/About';
import { Devices } from './pages/Devices';
import { History } from './pages/History';
import { Incident } from './pages/Incident';
import { LiveOps } from './pages/LiveOps';
import { ResponderAlert } from './pages/ResponderAlert';
import { Showcase } from './pages/Showcase';
import { Vehicles } from './pages/Vehicles';
import { RealtimeProvider } from './services/realtime';
import { theme } from './theme';

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: true, staleTime: 5000 } },
});

const router = createBrowserRouter([
  {
    element: (
      <RealtimeProvider>
        <Layout />
      </RealtimeProvider>
    ),
    children: [
      { path: '/', element: <LiveOps /> },
      { path: '/showcase', element: <Showcase /> },
      { path: '/incidents', element: <History /> },
      { path: '/incidents/:id', element: <Incident /> },
      { path: '/devices', element: <Devices /> },
      { path: '/vehicles', element: <Vehicles /> },
      { path: '/about', element: <About /> },
    ],
  },
  // Opened by police / hospital / family from the alert SMS: no sidebar, no login.
  { path: '/alert/:token', element: <ResponderAlert /> },
]);

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ThemeProvider>
  </StrictMode>,
);
