import { describe, expect, it } from 'vitest';

import { dateigroesse, MAX_UPLOAD_BYTE } from './dateigroesse';

describe('dateigroesse', () => {
  it('nennt Kleinstes in Byte', () => {
    expect(dateigroesse(0)).toBe('0 B');
    expect(dateigroesse(1)).toBe('1 B');
    expect(dateigroesse(1023)).toBe('1023 B');
  });

  it('wechselt bei 1024 auf KB', () => {
    expect(dateigroesse(1024)).toBe('1 KB');
    expect(dateigroesse(1536)).toBe('1,5 KB');
    expect(dateigroesse(1048575)).toBe('1024 KB');
  });

  it('wechselt bei 1.048.576 Byte auf MB', () => {
    expect(dateigroesse(1048576)).toBe('1 MB');
  });

  it('nennt die Grenze des Uploads als glatte Zahl', () => {
    // Die Meldung der Maske nennt „25 MB" — und genau diese Zahl steht daneben (Plan E12).
    expect(MAX_UPLOAD_BYTE).toBe(26214400);
    expect(dateigroesse(MAX_UPLOAD_BYTE)).toBe('25 MB');
  });

  it('nennt nie ein Binaerpraefix', () => {
    for (const bytes of [0, 1023, 1024, 1536, 1048575, 1048576, MAX_UPLOAD_BYTE]) {
      expect(dateigroesse(bytes)).not.toContain('KiB');
      expect(dateigroesse(bytes)).not.toContain('MiB');
    }
  });
});
