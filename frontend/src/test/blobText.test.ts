import { describe, expect, it } from 'vitest';

import { blobText } from './blobText';

describe('blobText', () => {
  it('liest den Inhalt eines Blob', async () => {
    await expect(blobText(new Blob(['inhalt']))).resolves.toBe('inhalt');
  });

  it('liest den leeren Blob als leeren Text', async () => {
    await expect(blobText(new Blob([]))).resolves.toBe('');
  });
});
