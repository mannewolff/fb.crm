/**
 * Der gelesene Inhalt eines `Blob` — fuer Erwartungen an die Wege, die Bytes statt JSON holen.
 *
 * Der Testlauf hat genau einen `Blob`, den von Node (`src/test/setup.ts`, Issue #191), und der
 * traegt `text()`. Die Hilfe bleibt trotzdem die eine Stelle, an der ein Test den Inhalt holt:
 * Welcher Blob in der Testumgebung gilt und was er kann, steht damit an einem Ort und nicht in
 * jeder Erwartung verstreut.
 */
export function blobText(blob: Blob): Promise<string> {
  return blob.text();
}
