import { describe, expect, it } from 'vitest';

import type { Rolle } from '../api/auth';
import { rechteAus, rollenname } from './rechte';
import type { Recht } from './rechte';

/** Jede Rolle gegen jedes Recht (Geklaerter Stand 5 und 6 von #409, Plan #414 E1). */
const ERWARTET: ReadonlyArray<readonly [Rolle | null, Recht, boolean]> = [
  ['C_LEVEL', 'darfFachlichSchreiben', true],
  ['C_LEVEL', 'darfEinstellungenSehen', true],
  ['C_LEVEL', 'darfEinstellungenAendern', true],
  ['C_LEVEL', 'darfKontenVerwalten', true],
  ['EMPLOYEE', 'darfFachlichSchreiben', true],
  ['EMPLOYEE', 'darfEinstellungenSehen', false],
  ['EMPLOYEE', 'darfEinstellungenAendern', false],
  ['EMPLOYEE', 'darfKontenVerwalten', false],
  ['READ_ONLY', 'darfFachlichSchreiben', false],
  ['READ_ONLY', 'darfEinstellungenSehen', true],
  ['READ_ONLY', 'darfEinstellungenAendern', false],
  ['READ_ONLY', 'darfKontenVerwalten', false],
  [null, 'darfFachlichSchreiben', false],
  [null, 'darfEinstellungenSehen', false],
  [null, 'darfEinstellungenAendern', false],
  [null, 'darfKontenVerwalten', false],
];

describe('rechteAus', () => {
  it.each(ERWARTET)('gibt %s das Recht %s: %s', (rolle, recht, erwartet) => {
    expect(rechteAus(rolle)[recht]).toBe(erwartet);
  });

  it('kennt genau die vier Rechte', () => {
    expect(Object.keys(rechteAus('C_LEVEL')).sort()).toEqual([
      'darfEinstellungenAendern',
      'darfEinstellungenSehen',
      'darfFachlichSchreiben',
      'darfKontenVerwalten',
    ]);
  });
});

describe('rollenname (E8)', () => {
  it.each([
    ['C_LEVEL', 'C-Level'],
    ['EMPLOYEE', 'Employee'],
    ['READ_ONLY', 'ReadOnly'],
  ] as const)('nennt %s „%s"', (rolle, name) => {
    expect(rollenname(rolle)).toBe(name);
  });
});
