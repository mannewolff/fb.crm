import { afterEach, describe, expect, it, vi } from 'vitest';

import { fetchNachPfad, json } from '../test/fetchNachPfad';
import { instance, parseInstance } from './instance';

afterEach(() => {
  vi.restoreAllMocks();
});

describe('parseInstance', () => {
  it('nimmt eine Antwort mit Version an', () => {
    expect(parseInstance({ version: '0.1.1' })).toEqual({ version: '0.1.1' });
  });

  it.each([
    ['ohne Objekt', 'v0.1.1'],
    ['bei null', null],
    ['ohne Version', {}],
    ['mit falsch typisierter Version', { version: 11 }],
  ])('weist eine Antwort %s zurueck', (_fall, antwort) => {
    expect(() => parseInstance(antwort)).toThrow(TypeError);
  });
});

describe('instance', () => {
  it('liest den Versionsstand der Instanz', async () => {
    fetchNachPfad({ 'GET /api/instance': json(200, { version: '0.1.1' }) });

    await expect(instance()).resolves.toEqual({ version: '0.1.1' });
  });
});
