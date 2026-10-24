import { describe, expect, it } from 'vitest';

import { ANGEMELDET, alsRolle, useAuth } from './sitzungDoppel';

/** Das Doppel der Sitzung selbst: Rolle, Rueckfall und die Wege, die in einer Probe nichts tun. */
describe('sitzungDoppel', () => {
  it('meldet ohne Aufruf von alsRolle ein angemeldetes C-Level-Konto', () => {
    expect(useAuth().sitzung).toEqual({
      status: 'angemeldet',
      konto: { ...ANGEMELDET, role: 'C_LEVEL' },
    });
  });

  it('traegt die Rolle, die alsRolle festlegt', () => {
    alsRolle('EMPLOYEE');

    expect(useAuth().sitzung).toEqual({
      status: 'angemeldet',
      konto: { ...ANGEMELDET, role: 'EMPLOYEE' },
    });
  });

  it('faellt nach dem vorigen Testfall auf C-Level zurueck', () => {
    expect(useAuth().sitzung).toEqual({
      status: 'angemeldet',
      konto: { ...ANGEMELDET, role: 'C_LEVEL' },
    });
  });

  it('laesst die Wege, die die Sitzung aendern, ohne Wirkung aufloesen', async () => {
    const wert = useAuth();

    await expect(wert.codeAnfordern('a@b.de')).resolves.toBeUndefined();
    await expect(wert.anmelden('a@b.de', '123456')).resolves.toBeUndefined();
    await expect(
      wert.einrichten({ bootstrapToken: 't', displayName: 'n', email: 'a@b.de', code: '1' }),
    ).resolves.toBeUndefined();
    await expect(wert.abmelden()).resolves.toBeUndefined();
    await expect(wert.rolleNeuLesen()).resolves.toBeUndefined();
  });
});
