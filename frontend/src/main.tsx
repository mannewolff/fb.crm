import CssBaseline from '@mui/material/CssBaseline';
import { ThemeProvider } from '@mui/material/styles';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';

// Alle Schriften werden offline mit der Anwendung ausgeliefert (CLAUDE-design.md, E23):
// Archivo variabel, weil die Vorlage font-stretch 110–118 % nutzt.
import '@fontsource-variable/archivo';
import '@fontsource/ibm-plex-sans/400.css';
import '@fontsource/ibm-plex-sans/500.css';
import '@fontsource/ibm-plex-sans/600.css';
import '@fontsource/ibm-plex-mono/400.css';
import '@fontsource/ibm-plex-mono/500.css';
import '@fontsource/ibm-plex-mono/600.css';

import App from './App';
import { theme } from './theme';

const wurzel = document.getElementById('root');
if (!wurzel) {
  throw new Error('Kein Element mit der Id root — index.html passt nicht zu main.tsx.');
}

createRoot(wurzel).render(
  <StrictMode>
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </ThemeProvider>
  </StrictMode>,
);
