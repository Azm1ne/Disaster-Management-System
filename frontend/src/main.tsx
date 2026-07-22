import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
// Self-hosted fonts (no external CDN — the SPA ships from its own nginx container).
import '@fontsource/noto-sans/400.css'
import '@fontsource/noto-sans/500.css'
import '@fontsource/noto-sans/700.css'
import '@fontsource/noto-sans/800.css'
import '@fontsource/noto-sans-bengali/400.css'
import '@fontsource/noto-sans-bengali/500.css'
import '@fontsource/noto-sans-bengali/700.css'
import '@fontsource/noto-sans-mono/400.css'
import '@fontsource/noto-sans-mono/500.css'
import './index.css'
import './i18n'
import App from './App.tsx'
import { AuthProvider } from '@/auth/AuthContext'
import { RealtimeProvider } from '@/realtime/RealtimeProvider'

// Pushed updates keep the cache fresh, so queries do not also re-fetch on window focus.
const queryClient = new QueryClient({
  defaultOptions: { queries: { refetchOnWindowFocus: false, staleTime: 1000 } },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <RealtimeProvider>
            <App />
          </RealtimeProvider>
        </AuthProvider>
      </QueryClientProvider>
    </BrowserRouter>
  </StrictMode>,
)
