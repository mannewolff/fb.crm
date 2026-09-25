import { describe, expect, it } from 'vitest';

import { istEmailForm } from './emailform';

/**
 * Dieselbe Tabelle wie `AnsprechpartnerEmailConstraintTest` im Backend (E8).
 *
 * Die beiden Seiten muessen dieselbe Eingabe gleich beurteilen: Weicht die Maske ab, weist sie
 * etwas ab, das der Server annaehme, oder sie laesst etwas durch, das er zurueckweist.
 */
describe('istEmailForm', () => {
  it.each(['max@firma.de', '  max@firma.de  ', '', '   '])('nimmt %j an', (eingabe) => {
    expect(istEmailForm(eingabe)).toBe(true);
  });

  it.each(['max@firma', 'max@@firma.de', 'a@b@c.de', '@firma.de', 'max@', 'maxfirma.de'])(
    'weist %j ab',
    (eingabe) => {
      expect(istEmailForm(eingabe)).toBe(false);
    },
  );
});
