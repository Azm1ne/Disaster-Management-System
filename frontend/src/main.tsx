import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
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

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
