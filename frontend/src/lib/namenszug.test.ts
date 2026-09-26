import { describe, expect, it } from 'vitest';

import { namensZug } from './namenszug';

describe('namensZug', () => {
  it('setzt Vor- und Nachnamen mit einem Leerzeichen zusammen', () => {
    expect(namensZug({ vorname: 'Anna', nachname: 'Berg' })).toBe('Anna Berg');
  });

  it('laesst ohne Vornamen nur den Nachnamen stehen — ohne fuehrendes Leerzeichen', () => {
    expect(namensZug({ vorname: null, nachname: 'Clausen' })).toBe('Clausen');
  });
});
