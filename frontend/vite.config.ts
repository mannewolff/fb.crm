import react from '@vitejs/plugin-react';
// defineConfig aus vitest/config, weil der Block `test` unten sonst kein bekanntes Feld
// ist — mit dem Import aus 'vite' meldet tsc TS2769, sobald die Datei geprueft wird.
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  server: {
    // Im Dev leitet der Vite-Server /api an Spring Boot weiter; in Produktion serviert
    // Spring Boot den Build aus classpath:/static/. Eine Origin, kein CORS (CLAUDE.md).
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    // Entspricht dem Performance-Budget aus CLAUDE-react.md (600 kB je Chunk).
    chunkSizeWarningLimit: 600,
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        // Reines Bootstrap: React-Root, Provider, Font-Importe — kein eigenes Verhalten.
        'src/main.tsx',
        // Reines Routen-Wiring ueber React.lazy; die Routen selbst tragen die Logik.
        'src/App.tsx',
        // Design-Tokens der Vorlage. Kein Verhalten; die Werte prueft src/theme.test.ts.
        'src/theme.ts',
        // Vitest-Setup (jest-dom-Matcher, cleanup) — laeuft vor jedem Test, testet nichts.
        'src/test/setup.ts',
        // Reine Typdeklaration (vite/client fuer die CSS-Importe) — enthaelt keinen Code.
        'src/vite-env.d.ts',
      ],
      // Ehrlicher Ist-Floor dieses Stands (E22). Ratchet: nur anheben, nie senken
      // (CLAUDE-react.md, Coverage-Philosophie).
      thresholds: {
        lines: 100,
        branches: 100,
        functions: 100,
        statements: 100,
      },
    },
  },
});
