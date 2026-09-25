import { describe, expect, it } from 'vitest';

import { dateigroesse } from './dateigroesse';

describe('dateigroesse', () => {
  it('nennt Kleinstes in Byte', () => {
    expect(dateigroesse(0)).toBe('0 B');
    expect(dateigroesse(1)).toBe('1 B');
    expect(dateigroesse(1023)).toBe('1023 B');
  });

  it('wechselt bei 1024 auf KiB', () => {
    expect(dateigroesse(1024)).toBe('1 KiB');
    expect(dateigroesse(1536)).toBe('1,5 KiB');
    expect(dateigroesse(1048575)).toBe('1024 KiB');
  });

  it('wechselt bei einem Mebibyte auf MiB', () => {
    expect(dateigroesse(1048576)).toBe('1 MiB');
    // Die Grenze des Uploads (E10) — sie soll als glatte Zahl dastehen.
    expect(dateigroesse(26214400)).toBe('25 MiB');
  });
});
