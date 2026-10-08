/// <reference types="vite/client" />

// Bringt die Typen fuer die Seiteneffekt-Importe der Schriften mit (`…/400.css`).
// Ohne sie meldet tsc unter `noUncheckedSideEffectImports` fuer jede CSS-Zeile in
// main.tsx TS2307 — die Pakete sind installiert, nur ihre Deklaration fehlte.
