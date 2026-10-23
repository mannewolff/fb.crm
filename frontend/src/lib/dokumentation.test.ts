import { readdirSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

import bedienungRoh from '../../../docs/bedienung.md?raw';
import betriebRoh from '../../../docs/betrieb.md?raw';

import { TEILE, teilNach, type Kennung } from './dokumentation';
import { leseMarkdown, type Block, type Dokument, type Inline } from './markdown';

/**
 * Die Waechter ueber die echten Texte unter `docs/` (Issue #386; Plan #378, E4, E16).
 *
 * Geprueft wird gegen den Rohtext desselben Imports, den auch `dokumentation.ts` liest — kein
 * Satz der Dokumentation steht hier abgeschrieben, sonst waere dieser Test selbst die zweite
 * Fassung, die Kriterium 2 verbietet. Nur Abschnittsnamen und Stichworte der Szenarien (E16).
 */
const WURZEL = join(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');

const ROH: Record<Kennung, string> = { bedienung: bedienungRoh, betrieb: betriebRoh };

const ZEILENENDE = /\r?\n/u;
const ZAUN = /^```/u;
const LISTENMARKE = /^(?:[-*]|\d+\.) +/u;

/**
 * Markdown, das der Leser nicht kennt und darum als Fliesstext ausgibt (E4): weitere `#`-Ebenen,
 * Zitat, Tabelle, HTML, Trennlinie, `+`-Aufzaehlung und `1)`-Numerierung.
 */
const UNBEKANNTE_ZEILE = /^(?:#|>|\||<|[-*_]{3,}\s*$|\+ |\d+\) )/u;

/** Auszeichnung, die nach dem Lesen noch im Klartext steht, war unvollstaendig (E4). */
const RESTAUSZEICHNUNG = /`|\*\*|\]\(/u;

interface RohZeile {
  nummer: number;
  text: string;
  imBefehl: boolean;
}

/** Die nicht leeren Zeilen ausserhalb der Zaeune samt Lage; Zaunzeilen selbst tragen keinen Inhalt. */
function rohZeilen(roh: string): RohZeile[] {
  const ergebnis: RohZeile[] = [];
  let imBefehl = false;
  roh.split(ZEILENENDE).forEach((text, index) => {
    if (ZAUN.test(text)) {
      imBefehl = !imBefehl;
      return;
    }
    if (text.trim() !== '') {
      ergebnis.push({ nummer: index + 1, text, imBefehl });
    }
  });
  return ergebnis;
}

/** Eine Inline-Folge zurueck in ihre Quellform, damit die Rohzeile darin wiederzufinden ist. */
function quellform(inhalt: Inline[]): string {
  return inhalt
    .map((stueck) => {
      switch (stueck.art) {
        case 'klartext':
          return stueck.text;
        case 'betont':
          return `**${stueck.text}**`;
        case 'schnipsel':
          return `\`${stueck.text}\``;
        case 'verweis':
          return `[${stueck.text}](${stueck.ziel})`;
      }
    })
    .join('');
}

function blockform(block: Block): string {
  switch (block.art) {
    case 'ueberschrift':
      return `${'#'.repeat(block.ebene)} ${block.text}`;
    case 'text':
      return quellform(block.inhalt);
    case 'liste':
      return block.eintraege.map(quellform).join('\n');
    case 'befehl':
      return block.inhalt;
  }
}

function klartexte(block: Block): string[] {
  const inhalte = block.art === 'text' ? [block.inhalt] : block.art === 'liste' ? block.eintraege : [];
  return inhalte.flat().flatMap((stueck) => (stueck.art === 'klartext' ? [stueck.text] : []));
}

/** Die Zeilen ausserhalb der Zaeune, deren Form der Leser nicht kennt (E4). */
function unbekannteZeilen(roh: string): string[] {
  const zeilen = rohZeilen(roh);
  const titelzeile = zeilen.find((z) => !z.imBefehl && /^# /u.test(z.text));
  return zeilen
    .filter((z) => !z.imBefehl && z !== titelzeile && !/^###? \S/u.test(z.text))
    .filter((z) => UNBEKANNTE_ZEILE.test(z.text.trim()))
    .map((z) => `${z.nummer}: ${z.text}`);
}

/**
 * Die Zeilen der Rohdatei, die in keinem Block des gelesenen Dokuments stehen (Kriterium 2), und
 * die Zahl der Stellen, die keine Zeile getroffen hat.
 */
function abgleich(roh: string, dokument: Dokument): { fehlend: string[]; ungetroffen: number } {
  // Der Titel ist kein Block (E7); er steht als eigene erste Stelle vorne.
  const stellen = [`# ${dokument.titel}`, ...dokument.bloecke.map(blockform)];
  const getroffen = new Set<number>();
  let block = 0;
  let ab = 0;
  const fehlend: string[] = [];

  // In Reihenfolge: Jede Zeile verbraucht ihre Stelle, eine doppelt vorkommende Zeile kann
  // darum nicht zweimal auf denselben Block zeigen.
  for (const zeile of rohZeilen(roh)) {
    const nadel = zeile.imBefehl ? zeile.text : zeile.text.trim().replace(LISTENMARKE, '');
    let gefunden = false;
    for (let b = block; b < stellen.length && !gefunden; b += 1) {
      const stelle = stellen[b].indexOf(nadel, b === block ? ab : 0);
      if (stelle !== -1) {
        block = b;
        ab = stelle + nadel.length;
        getroffen.add(b);
        gefunden = true;
      }
    }
    if (!gefunden) {
      fehlend.push(`${zeile.nummer}: ${zeile.text}`);
    }
  }
  return { fehlend, ungetroffen: stellen.length - getroffen.size };
}

/** Der Text eines `##`-Abschnitts bis zum naechsten `##`, in Quellform. */
function abschnitt(kennung: Kennung, name: string): string {
  const { bloecke } = teilNach(kennung).dokument;
  const start = bloecke.findIndex((b) => b.art === 'ueberschrift' && b.ebene === 2 && b.text === name);
  expect(start, `Abschnitt „${name}" in docs/${kennung}.md`).toBeGreaterThanOrEqual(0);
  const ende = bloecke.findIndex((b, i) => i > start && b.art === 'ueberschrift' && b.ebene === 2);
  return bloecke
    .slice(start + 1, ende === -1 ? undefined : ende)
    .map(blockform)
    .join('\n');
}

function abschnittsnamen(kennung: Kennung): string[] {
  return teilNach(kennung).dokument.bloecke.flatMap((b) =>
    b.art === 'ueberschrift' && b.ebene === 2 ? [b.text] : [],
  );
}

describe('TEILE und teilNach', () => {
  it('beginnt mit dem Teil „Bedienung", danach „Betrieb" (Kriterium 1)', () => {
    expect(TEILE.map((teil) => [teil.kennung, teil.name])).toEqual([
      ['bedienung', 'Bedienung'],
      ['betrieb', 'Betrieb'],
    ]);
  });

  it.each(['bedienung', 'betrieb'] as const)('liefert zur Kennung %s den passenden Teil', (kennung) => {
    const teil = teilNach(kennung);

    expect(teil.kennung).toBe(kennung);
    expect(teil).toBe(TEILE.find((t) => t.kennung === kennung));
  });
});

describe.each(TEILE.map((teil) => [teil.kennung, teil] as const))('docs/%s.md', (kennung, teil) => {
  const roh = ROH[kennung];
  const zeilen = rohZeilen(roh);

  it('hat genau eine #-Ueberschrift, und ihr Text ist der Titel des Teils', () => {
    const titelzeilen = zeilen.filter((z) => !z.imBefehl && /^# /u.test(z.text));

    expect(titelzeilen).toHaveLength(1);
    expect(teil.dokument.titel).toBe(teil.name);
    expect(titelzeilen[0].text).toBe(`# ${teil.name}`);
  });

  it('laesst keine nicht leere Zeile in den Rueckfall laufen (E4)', () => {
    const restauszeichnung = teil.dokument.bloecke
      .flatMap(klartexte)
      .filter((text) => RESTAUSZEICHNUNG.test(text));

    expect(unbekannteZeilen(roh)).toEqual([]);
    expect(restauszeichnung).toEqual([]);
  });

  it('fuehrt eindeutige Ankernamen', () => {
    const anker = teil.dokument.bloecke.flatMap((b) => (b.art === 'ueberschrift' ? [b.anker] : []));

    expect(anker.length).toBeGreaterThan(0);
    expect(new Set(anker).size).toBe(anker.length);
  });

  it('bringt jede nicht leere Zeile in genau einem Block unter (Kriterium 2)', () => {
    expect(abgleich(roh, teil.dokument)).toEqual({ fehlend: [], ungetroffen: 0 });
  });
});

describe('Die Waechter selbst', () => {
  // Ein Gegenbeispiel je Waechter: Ein Waechter, der nie anschlaegt, waere ein falsches Gruen.
  const probe = '# Probe\n\n#### Vierte Ebene\n\nSiehe [Betrieb](betrieb.md), **fett** und `code`.\n\n- Punkt\n';

  it('meldet eine Zeile, deren Form der Leser nicht kennt', () => {
    expect(unbekannteZeilen(probe)).toEqual(['3: #### Vierte Ebene']);
  });

  it('findet jede Zeile der Probe in ihrem Block wieder, auch mit Verweis und Auszeichnung', () => {
    expect(abgleich(probe, leseMarkdown(probe))).toEqual({ fehlend: [], ungetroffen: 0 });
  });

  it('meldet eine Zeile, die im gelesenen Dokument fehlt, und eine Stelle, die keine Zeile traf', () => {
    const anderes = leseMarkdown('# Probe\n\nEin anderer Satz.');

    expect(abgleich('# Probe\n\nDer Satz aus der Quelle.', anderes)).toEqual({
      fehlend: ['3: Der Satz aus der Quelle.'],
      ungetroffen: 1,
    });
  });
});

describe('Eine Fassung der Dokumentation (Kriterium 2)', () => {
  it('legt unter frontend/public keine Markdown-Datei ab', () => {
    const public_ = readdirSync(join(WURZEL, 'frontend', 'public'), { recursive: true, encoding: 'utf8' });

    expect(public_.filter((name) => name.toLowerCase().endsWith('.md'))).toEqual([]);
  });
});

describe('Szenarien des Fachplans (E16)', () => {
  it('Szenario: Ein neuer Betreiber richtet fb.crm ein', () => {
    expect(abschnittsnamen('betrieb')).toEqual(
      expect.arrayContaining([
        'Voraussetzungen',
        'Konfiguration anlegen',
        'Starten',
        'Konto einrichten',
        'Betrieb unter eigenem Namen',
        'Mailversand einrichten',
        'Weitere Konten anlegen',
      ]),
    );
    const readme = readFileSync(join(WURZEL, 'README.md'), 'utf8');
    expect(readme).toContain('(docs/betrieb.md)');
    expect(readme).toContain('(docs/bedienung.md)');
  });

  it('Szenario: Sicherung zurückspielen', () => {
    for (const name of ['Sicherung', 'Wiederherstellung']) {
      const text = abschnitt('betrieb', name);

      expect(text, name).toMatch(/Datenbank/u);
      expect(text, name).toMatch(/Dokumente/u);
      expect(text, name).toMatch(/\.env/u);
    }
  });

  it('Szenario: Auf eine neue Version aktualisieren', () => {
    const text = abschnitt('betrieb', 'Aktualisierung');
    const sichern = text.indexOf('Sicher');
    const checkout = text.indexOf('git checkout');
    const pruefen = text.indexOf('Prüfen');

    expect(sichern).toBeGreaterThanOrEqual(0);
    expect(checkout).toBeGreaterThan(sichern);
    expect(pruefen).toBeGreaterThan(checkout);
    // Kriterium 8 und 10 (#392): Das Update auf die Anmeldung per Code beendet alle Sitzungen einmal.
    expect(text.slice(pruefen)).toMatch(/Sitzungen/u);
    expect(text.slice(pruefen)).toMatch(/Code/u);
  });

  it('Szenario: Das Konto per Code einrichten, auch ohne Mailversand', () => {
    const text = abschnitt('betrieb', 'Konto einrichten');

    // Kriterium 6 und 10 (#392): Einrichtung mit Code, ohne Mailversand aus dem Protokoll.
    expect(text).toMatch(/Code/u);
    expect(text).toMatch(/Protokoll/u);
    expect(text).not.toMatch(/Passwort/u);
  });

  it('Szenario: Erstes Konto auf frischer Installation', () => {
    const text = abschnitt('betrieb', 'Konto einrichten');

    // Kriterium 12 (#409): Das erste Konto ist C-Level, weitere entstehen in fb.crm.
    expect(text).toMatch(/C-Level/u);
    expect(text).toMatch(/Mailversand/u);
    expect(text).toMatch(/Weitere Konten anlegen/u);
  });

  it('Szenario: C-Level legt eine Mitarbeiterin an', () => {
    const weitere = abschnitt('betrieb', 'Weitere Konten anlegen');
    const rollen = abschnitt('bedienung', 'Mitarbeitende und Rollen');

    // Kriterium 12 (#409): Mailversand im Betrieb mit mehreren Personen, ohne ihn das Protokoll.
    expect(weitere).toMatch(/Mailversand/u);
    expect(weitere).toMatch(/Protokoll/u);
    expect(abschnittsnamen('bedienung')).toContain('Mitarbeitende und Rollen');
    for (const stichwort of ['Neues Konto', 'Bearbeiten', 'Stilllegen', 'Wieder aktivieren']) {
      expect(rollen, stichwort).toContain(stichwort);
    }
    for (const rolle of ['C-Level', 'Employee', 'ReadOnly']) {
      expect(rollen, rolle).toMatch(new RegExp(`^${rolle} darf|^${rolle} liest`, 'mu'));
    }
    expect(rollen).toMatch(/letzte aktive\s+C-Level-Konto/u);
    // Die Erste Schritte nennen die eigene Adresse, der Aufbau „Mitarbeitende" im Fuss.
    const erste = abschnitt('bedienung', 'Erste Schritte');
    expect(erste).toMatch(/eigenen E-Mail-Adresse/u);
    expect(erste).toMatch(/Fuß der Schiene stehen „Mitarbeitende"/u);
  });

  it('Szenario: Gleichzeitig arbeiten (#411, Kriterium 9)', () => {
    const namen = abschnittsnamen('bedienung');
    // Gleich nach dem Einstieg, vor den Bereichen, in denen die Abweisung erscheint.
    expect(namen.indexOf('Gleichzeitig arbeiten')).toBe(namen.indexOf('Erste Schritte') + 1);
    const text = abschnitt('bedienung', 'Gleichzeitig arbeiten');

    expect(text).toMatch(/^\*\*Zweck:\*\*/u);
    expect(text).toMatch(/Nicht ausgeführt/u);
    expect(text).toMatch(/in einem anderen Fenster geändert/u);
    expect(text).toMatch(/„Neu laden"/u);
    expect(text).toMatch(/„Deine Eingaben"/u);
    expect(text).toMatch(/„Verwerfen"/u);
    expect(text).toMatch(/\*\*Ergebnis:\*\*/u);
  });

  it('Szenario: Arbeitszeit je Person (Kriterium 10, #410)', () => {
    const text = abschnitt('bedienung', 'Arbeitszeit');

    expect(text).toMatch(/Employee sieht und pflegt nur die eigenen Zeiten/u);
    expect(text).toMatch(/C-Level sieht zunächst die eigenen Zeiten/u);
    expect(text).toMatch(/ReadOnly sieht beim Öffnen „Alle"/u);
    expect(text).toMatch(/Überschneidungen prüft fb\.crm je Person/u);
  });
});
