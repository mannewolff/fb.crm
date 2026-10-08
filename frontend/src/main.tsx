import CssBaseline from '@mui/material/CssBaseline';
import { ThemeProvider } from '@mui/material/styles';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';

// Eine Schrift, offline mit der Anwendung ausgeliefert (CLAUDE-design.md, Typografie): Plus
// Jakarta Sans variabel — sie deckt die Gewichte 400 bis 800 der Tabelle in einer Datei ab und
// fuehrt die Tabellenziffern selbst.
import '@fontsource-variable/plus-jakarta-sans';

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
