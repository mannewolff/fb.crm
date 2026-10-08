import { describe, expect, it } from 'vitest';

import { hinweisAus, meldungAm } from './feldmeldung';

describe('meldungAm', () => {
  it('liefert die Meldungen eines Feldes als einen Satz', () => {
    expect(meldungAm({ password: ['zu kurz', 'zu einfach'] }, 'password')).toBe('zu kurz; zu einfach');
  });

  it('liefert nichts fuer ein Feld ohne Meldung', () => {
    expect(meldungAm({ password: ['zu kurz'] }, 'email')).toBeUndefined();
  });

  it('liefert nichts fuer eine leere Meldungsliste', () => {
    expect(meldungAm({ email: [] }, 'email')).toBeUndefined();
  });
});

describe('hinweisAus', () => {
  it('liest den Hinweis aus dem Zustand einer Navigation', () => {
    expect(hinweisAus({ hinweis: 'Das Passwort ist gesetzt.' })).toBe('Das Passwort ist gesetzt.');
  });

  it.each([
    ['ohne Zustand', null],
    ['ohne Objekt', 'Hinweis'],
    ['ohne Hinweis', { anderes: 1 }],
    ['mit falsch typisiertem Hinweis', { hinweis: 42 }],
  ])('liefert nichts %s', (_fall, zustand) => {
    expect(hinweisAus(zustand)).toBeNull();
  });
});
