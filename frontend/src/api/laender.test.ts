import { afterEach, describe, expect, it, vi } from 'vitest';

import { laenderLaden, parseLaender } from './laender';
import { fetchNachPfad, json } from '../test/fetchNachPfad';

const LAENDER = [
  { kuerzel: 'DE', name: 'Deutschland' },
  { kuerzel: 'AT', name: 'Österreich' },
];

afterEach(() => {
  vi.restoreAllMocks();
});

describe('parseLaender', () => {
  it('verengt die Liste in ihrer Reihenfolge', () => {
    expect(parseLaender(LAENDER)).toEqual(LAENDER);
  });

  it.each([
    ['keine Liste', { kuerzel: 'DE', name: 'Deutschland' }],
    ['Eintrag ist kein Objekt', ['DE']],
    ['Eintrag ohne kuerzel', [{ name: 'Deutschland' }]],
    ['Eintrag ohne name', [{ kuerzel: 'DE', name: null }]],
  ])('weist eine Antwort %s ab', (_fall, antwort) => {
    expect(() => parseLaender(antwort)).toThrow(TypeError);
  });
});

describe('laenderLaden', () => {
  it('holt die Liste ueber GET /api/laender', async () => {
    const fetchMock = fetchNachPfad({ 'GET /api/laender': json(200, LAENDER) });

    await expect(laenderLaden()).resolves.toEqual(LAENDER);
    expect(fetchMock).toHaveBeenCalledWith('/api/laender', expect.objectContaining({ method: 'GET' }));
  });
});
