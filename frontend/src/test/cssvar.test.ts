import { describe, expect, it } from 'vitest';

import { ohneRueckfall } from './cssvar';

describe('ohneRueckfall', () => {
  it.each<[string, string]>([
    // Der Regelfall aus dem Theme: Variable mit Rueckfallwert.
    ['var(--fb-x, #FBE4E4)', 'var(--fb-x)'],
    // Ohne Rueckfall bleibt der Wert, wie er ist.
    ['var(--fb-x)', 'var(--fb-x)'],
    // Keine schliessende Klammer am Ende: nichts abzuschneiden.
    ['#FBE4E4', '#FBE4E4'],
    // Ein Rueckfall mit innerer Klammer bleibt stehen — so wie beim bisherigen Ausdruck.
    ['var(--fb-x, rgb(1, 2, 3))', 'var(--fb-x, rgb(1, 2, 3))'],
  ])('macht aus %s die Form %s', (wert, erwartet) => {
    expect(ohneRueckfall(wert)).toBe(erwartet);
  });
});
