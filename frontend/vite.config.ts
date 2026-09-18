import react from '@vitejs/plugin-react';
// defineConfig aus vitest/config, weil der Block `test` unten sonst kein bekanntes Feld
// ist — mit dem Import aus 'vite' meldet tsc TS2769, sobald die Datei geprueft wird.
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  server: {
    fs: {
      // Nur fuer den Testlauf: Vitest laedt die Release-Skripte aus ../scripts, und die
      // liegen ausserhalb dieses Projektverzeichnisses (Issue #26). Der Dev-Server
      // serviert von hier nichts — er liefert die Anwendung aus src und leitet /api weiter.
      allow: ['..'],
    },
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
    // Die Release-Skripte unter ../scripts sind JavaScript und laufen hier mit: Im
    // Repository gibt es genau eine Node-Werkzeugkette, und ein zweiter Wurzel-Kontext mit
    // eigener package.json, eigenem Lockfile und eigenem Pflichtcheck waere ein zweites
    // Fundament fuer zwei Dateien (Issue #26). jsdom stoert sie nicht — fs, child_process
    // und git stehen in dieser Umgebung unveraendert zur Verfuegung.
    include: ['src/**/*.test.{ts,tsx}', '../scripts/**/*.test.mjs'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
      // Nur frontend/src. Die Release-Skripte unter ../scripts laufen zwar oben mit, sie
      // stehen aber bewusst NICHT in dieser Messung: Der v8-Provider laesst Dateien
      // ausserhalb des Projektverzeichnisses weg, und `allowExternal` liefert in Vitest
      // 2.1.9 einen leeren Report — die Schwellen liefen dann gegen null Dateien und waeren
      // ein falsches Gruen. Die Skripte sind stattdessen ueber ihre Testfaelle abgedeckt
      // (scripts/*.test.mjs, Issue #26).
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
