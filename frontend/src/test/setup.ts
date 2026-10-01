import { Blob as NodeBlob } from 'node:buffer';

import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// Ein `Blob` fuer den ganzen Testlauf (Issue #191). Im Browser gibt es genau einen Blob; in der
// Testumgebung treffen zwei aufeinander, und welcher aus `new Response(...).blob()` kommt, haengt
// an der Node-Version: unter Node 22.11.0 (`engines`, so baut die CI) der von Node, unter Node 26
// der von jsdom. Der Blob von jsdom kennt dabei weder `text()` noch `arrayBuffer()`, nur `slice`,
// `size` und `type` — deshalb war `instanceof Blob` in der CI rot und jsdoms `FileReader` nahm den
// Blob von Node nicht an. Node sein Blob als globalen zu setzen macht aus beiden einen: Er traegt
// `text()`, und `new Response(...).blob()` liefert ihn unter beiden Versionen.
Object.defineProperty(globalThis, 'Blob', { value: NodeBlob, configurable: true, writable: true });

afterEach(() => {
  cleanup();
});
