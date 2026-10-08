import { afterEach, describe, expect, it, vi } from 'vitest';

import { liesEingeklappt, merkeEingeklappt, SCHIENE_SCHLUESSEL } from './railState';

/**
 * Ein Speicher, der wirklich speichert.
 *
 * Node bringt in dieser Umgebung ein `localStorage` mit, das ohne `--localstorage-file`
 * nichts behaelt — Schreiben und Wiederlesen liesse sich damit nicht pruefen.
 */
function speicher(eintraege: Record<string, string> = {}) {
  const inhalt = new Map(Object.entries(eintraege));
  const doppel = {
    getItem: (schluessel: string) => inhalt.get(schluessel) ?? null,
    setItem: (schluessel: string, wert: string) => {
      inhalt.set(schluessel, wert);
    },
  };
  Object.defineProperty(globalThis, 'localStorage', { value: doppel, configurable: true });
  return inhalt;
}

/** Ein Speicher, der bei jedem Zugriff scheitert — etwa im privaten Modus mancher Browser. */
function speicherWirft() {
  const scheitern = () => {
    throw new DOMException('Zugriff verweigert', 'SecurityError');
  };
  Object.defineProperty(globalThis, 'localStorage', {
    value: { getItem: scheitern, setItem: scheitern },
    configurable: true,
  });
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('Einklapp-Zustand der Schiene (E13)', () => {
  it('ist ohne gespeicherten Wert ausgeklappt', () => {
    speicher();

    expect(liesEingeklappt()).toBe(false);
  });

  it('liest nach dem Schreiben denselben Zustand zurueck', () => {
    const inhalt = speicher();

    merkeEingeklappt(true);
    expect(liesEingeklappt()).toBe(true);
    merkeEingeklappt(false);
    expect(liesEingeklappt()).toBe(false);
    expect([...inhalt.keys()]).toEqual([SCHIENE_SCHLUESSEL]);
  });

  it('faellt bei einem unlesbaren Wert auf ausgeklappt zurueck', () => {
    speicher({ [SCHIENE_SCHLUESSEL]: 'vielleicht' });

    expect(liesEingeklappt()).toBe(false);
  });

  it('faellt auf ausgeklappt zurueck und schweigt, wenn der Speicher wirft', () => {
    speicherWirft();

    expect(liesEingeklappt()).toBe(false);
    expect(() => {
      merkeEingeklappt(true);
    }).not.toThrow();
  });

  it('legt unter einem Schluessel ohne Sitzungsbezug ab', () => {
    expect(SCHIENE_SCHLUESSEL).not.toMatch(/token|session|sitzung|auth|konto/i);
  });
});
