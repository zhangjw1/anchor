import { createRoot } from 'react-dom/client';
import App from './App.tsx';
import { AppProviders } from './providers/AppProviders';
import './index.css';

createRoot(document.getElementById('root')!).render(
  <AppProviders>
    <App />
  </AppProviders>,
);
