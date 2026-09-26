import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import { ersteDatei } from './dateiwahl';

/**
 * Ein echtes Dateifeld und seine Auswahl.
 *
 * Eine `FileList` laesst sich in dieser Umgebung nicht von Hand bauen — `DataTransfer` gibt es in
 * jsdom nicht. Also entsteht sie dort, wo sie auch in der Anwendung entsteht: an einem
 * `<input type="file">`, in das der Benutzer etwas gewaehlt hat.
 */
async function auswahl(dateien: readonly File[]): Promise<FileList | null> {
  const feld = document.createElement('input');
  feld.type = 'file';
  feld.multiple = true;
  document.body.append(feld);
  for (const datei of dateien) {
    await userEvent.upload(feld, datei);
  }
  return feld.files;
}

afterEach(() => {
  document.body.replaceChildren();
});

describe('ersteDatei', () => {
  it('nimmt die erste Datei der Auswahl', async () => {
    const erste = new File(['inhalt'], 'anfrage.pdf');

    expect(ersteDatei(await auswahl([erste]))).toBe(erste);
  });

  it('meldet eine leere Auswahl als „keine Datei"', async () => {
    expect(ersteDatei(await auswahl([]))).toBeNull();
  });

  it('meldet eine fehlende Auswahl als „keine Datei"', () => {
    // So antwortet `files` an jedem Eingabefeld, das keines fuer Dateien ist.
    expect(ersteDatei(null)).toBeNull();
  });
});
