/**
 * Der gelesene Inhalt eines `Blob` — fuer Erwartungen an die Wege, die Bytes statt JSON holen.
 *
 * Eine eigene Hilfe und nicht `blob.text()`: Der `Blob` aus jsdom kennt von der Schnittstelle nur
 * `slice`, `size` und `type`, und `new Response(blob).text()` macht daraus die Zeichenkette
 * „[object Blob]" — eine Erwartung dagegen waere gruen, ohne den Inhalt je gesehen zu haben. Der
 * `FileReader` ist der Weg, den jsdom bereitstellt.
 *
 * Herausgegeben wird sein Ergebnis, wie es ist, und nicht in `string` umgeformt: `readAsText`
 * liefert immer eine Zeichenkette, eine Umformung waere also ein Zweig ohne Fall — und die
 * Erwartung `toBe('inhalt')` prueft ihn ohnehin mit.
 *
 * Ein Lesefehler laesst die Zusage offen und den Test in seine Zeitgrenze laufen. Das ist hier
 * der richtige Ausgang: Einen `FileReader`, der auf einem Blob im Speicher scheitert, kann kein
 * Test herstellen.
 */
export function blobText(blob: Blob): Promise<FileReader['result']> {
  return new Promise<FileReader['result']>((aufloesen) => {
    const leser = new FileReader();
    leser.addEventListener('loadend', () => {
      aufloesen(leser.result);
    });
    leser.readAsText(blob);
  });
}
