import { readdirSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

/**
 * Jede Anfrage mit gueltigem Sitzungs-Cookie verlaengert die Sitzung um die volle Laufzeit
 * (Kriterium 9, E4). Das ist nur dann dasselbe wie „jede Bedienung", wenn das Frontend keine
 * Anfrage ohne Anlass stellt — eine nur geoeffnete Seite darf die Sitzung nicht am Leben halten.
 * Weil das eine Zusage ueber den ganzen Quellbaum ist, prueft sie hier der Quelltext selbst.
 *
 * `setInterval`, `setImmediate` und `requestIdleCallback` sind ausnahmslos verboten. `setTimeout`
 * ist nur in einer Zeile erlaubt, der unmittelbar die Kommentarmarke `// entprellung` vorangeht:
 * Eine Entprellung wartet auf das Ende einer Eingabe und loest nichts aus, was nicht vorher
 * jemand angestossen hat. Die Freistellung haengt an der Marke, nicht am Dateinamen — wer einen
 * neuen Zeitgeber braucht, muss ihn an Ort und Stelle als Entprellung ausweisen.
 */
const SRC = dirname(dirname(fileURLToPath(import.meta.url)));

/** Zeitgeber, die ohne Ausnahme verboten sind. */
const VERBOTEN = /\b(setInterval|setImmediate|requestIdleCallback)\b/;

/** Der eine Zeitgeber, den die Marke freistellt. */
const ZEITGEBER = /\bsetTimeout\b/;

/** Die Marke, die unmittelbar vor einem erlaubten `setTimeout` steht. */
const MARKE = '// entprellung';

interface Quelle {
  readonly name: string;
  readonly text: string;
}

function quellen(): Quelle[] {
  return readdirSync(SRC, { recursive: true, encoding: 'utf8' })
    .filter((name) => name.endsWith('.ts') || name.endsWith('.tsx'))
    .filter((name) => !name.includes('.test.'))
    .map((name) => ({ name, text: readFileSync(join(SRC, name), 'utf8') }));
}

/** Die Verstoesse eines Quelltexts, je Fund mit Zeilennummer und Zeitgeber. */
function verstoesse(text: string): string[] {
  const zeilen = text.split(/\r?\n/);
  return zeilen.flatMap((zeile, index) => {
    const verboten = VERBOTEN.exec(zeile);
    if (verboten) {
      return [`Zeile ${index + 1}: ${verboten[1]}`];
    }
    const markiert = index > 0 && zeilen[index - 1].trim() === MARKE;
    if (ZEITGEBER.test(zeile) && !markiert) {
      return [`Zeile ${index + 1}: setTimeout ohne ${MARKE}`];
    }
    return [];
  });
}

describe('Kein Abruf ohne Anlass im Frontend', () => {
  it('stellt im Quellbaum keinen Zeitgeber auf, der von selbst Anfragen ausloesen koennte', () => {
    const treffer = quellen().flatMap((quelle) =>
      verstoesse(quelle.text).map((fund) => `${quelle.name}: ${fund}`),
    );

    expect(treffer).toEqual([]);
  });

  it('schlaegt an einem Gegenbeispiel mit jedem verbotenen Zeitgeber an', () => {
    const gegenbeispiel = [
      'setInterval(() => holeDaten(), 60_000);',
      'setImmediate(holeDaten);',
      'requestIdleCallback(holeDaten);',
      '// entprellung',
      'window.setInterval(holeDaten, 1000);',
    ].join('\n');

    expect(verstoesse(gegenbeispiel)).toEqual([
      'Zeile 1: setInterval',
      'Zeile 2: setImmediate',
      'Zeile 3: requestIdleCallback',
      'Zeile 5: setInterval',
    ]);
  });

  it('schlaegt bei einem setTimeout ohne Marke an', () => {
    const gegenbeispiel = ['const uhr = setTimeout(holeDaten, 300);'].join('\n');

    expect(verstoesse(gegenbeispiel)).toEqual(['Zeile 1: setTimeout ohne // entprellung']);
  });

  it('schlaegt an, wenn die Marke nicht unmittelbar vor dem setTimeout steht', () => {
    const gegenbeispiel = ['// entprellung', '', 'const uhr = setTimeout(holeDaten, 300);'].join(
      '\n',
    );

    expect(verstoesse(gegenbeispiel)).toEqual(['Zeile 3: setTimeout ohne // entprellung']);
  });

  it('schlaegt an, wenn die Marke nur Teil eines anderen Kommentars ist', () => {
    const gegenbeispiel = [
      '// keine entprellung, sondern ein Abruf',
      'const uhr = setTimeout(holeDaten, 300);',
    ].join('\n');

    expect(verstoesse(gegenbeispiel)).toEqual(['Zeile 2: setTimeout ohne // entprellung']);
  });

  it('laesst ein setTimeout mit unmittelbar vorangehender Marke durch', () => {
    const beispiel = ['    // entprellung', '    const uhr = setTimeout(() => {', '    }, 300);'].join(
      '\n',
    );

    expect(verstoesse(beispiel)).toEqual([]);
  });

  it('laesst clearTimeout durch', () => {
    expect(verstoesse('clearTimeout(uhr);')).toEqual([]);
  });
});
