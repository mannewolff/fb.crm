import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { describe, expect, test } from 'vitest';

import {
  alterInTagen,
  anlagedatum,
  berichtLesen,
  bodyMitStellen,
  ERGAENZBARE_SPALTEN,
  kartenbody,
  kartentitel,
  nachDateiGruppieren,
  OFFENE_SPALTEN,
  ohneBefund,
  quote,
  quoteText,
  stellenAusBody,
  zuAlt,
} from './mutationsbericht.mjs';

/** Siehe bump-version.test.mjs: Vitest laeuft mit `frontend` als Arbeitsverzeichnis. */
const REPO = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim();

function bericht(name) {
  return readFileSync(path.join(REPO, 'scripts', 'fixtures', name), 'utf8');
}

/**
 * Der Bericht mit 1957 Mutationen, davon 1956 getoetet.
 *
 * Die abgelegte Datei traegt einen getoeteten und einen ueberlebenden Mutanten; die Masse
 * entsteht hier, indem der getoetete so oft wiederholt wird, bis die Zaehlung von 2026-10-18
 * stimmt. 1957 von Hand geschriebene Elemente waeren kein kleiner Bericht mehr.
 */
function bericht999() {
  const text = bericht('mutations-99-9.xml');
  const getoetet = /<mutation detected='true'.*?<\/mutation>/s.exec(text)[0];
  return text.replace(getoetet, () => Array(1956).fill(getoetet).join('\n'));
}

const ANGEBOT = 'src/main/java/org/mwolff/fbcrm/angebot/domain/Angebot.java';

function mutation({ detected = 'false', status = 'SURVIVED', klasse, datei, methode, zeile, mutator }) {
  return (
    `<mutation detected='${detected}' status='${status}' numberOfTestsRun='0'>` +
    `<sourceFile>${datei}</sourceFile><mutatedClass>${klasse}</mutatedClass>` +
    `<mutatedMethod>${methode}</mutatedMethod><methodDescription>()V</methodDescription>` +
    `<lineNumber>${zeile}</lineNumber><mutator>${mutator}</mutator>` +
    `<killingTest/><description>x</description></mutation>`
  );
}

function umschlossen(...mutationen) {
  return `<?xml version="1.0" encoding="UTF-8"?>\n<mutations partial="false">\n${mutationen.join('\n')}\n</mutations>\n`;
}

describe('berichtLesen', () => {
  test('zaehlt 1957 erzeugte und 1956 getoetete Mutanten und nennt den einen Ueberlebenden', () => {
    const gelesen = berichtLesen(bericht999());

    expect(gelesen.erzeugt).toBe(1957);
    expect(gelesen.getoetet).toBe(1956);
    expect(gelesen.ueberlebende).toEqual([
      {
        datei: ANGEBOT,
        zeile: 27,
        methode: '<init>',
        mutator: 'org.pitest.mutationtest.engine.gregor.mutators.NullReturnValsMutator',
      },
    ]);
  });

  test('bildet den Dateipfad aus dem Paket, damit gleichnamige Dateien getrennt bleiben', () => {
    const gelesen = berichtLesen(bericht('mutations-75.xml'));

    expect(gelesen.erzeugt).toBe(8);
    expect(gelesen.getoetet).toBe(6);
    expect(gelesen.ueberlebende.map((stelle) => stelle.datei)).toEqual([
      'src/main/java/org/mwolff/fbcrm/rechnung/domain/Rechnung.java',
      'src/main/java/org/mwolff/fbcrm/angebot/web/Angebot.java',
    ]);
  });

  test('nimmt fuer eine innere Klasse das Paket der aeusseren und deren Quelldatei', () => {
    const gelesen = berichtLesen(
      umschlossen(
        mutation({
          klasse: 'org.mwolff.fbcrm.angebot.domain.Angebot$Position',
          datei: 'Angebot.java',
          methode: 'summe',
          zeile: 3,
          mutator: 'M',
        }),
      ),
    );

    expect(gelesen.ueberlebende[0].datei).toBe(ANGEBOT);
  });

  test('zaehlt einen nicht abgedeckten Mutanten als ueberlebend', () => {
    const gelesen = berichtLesen(
      umschlossen(
        mutation({ detected: 'true', status: 'KILLED', klasse: 'a.A', datei: 'A.java', methode: 'm', zeile: 1, mutator: 'M' }),
        mutation({ status: 'NO_COVERAGE', klasse: 'a.A', datei: 'A.java', methode: 'n', zeile: 2, mutator: 'M' }),
      ),
    );

    expect(gelesen).toEqual({
      erzeugt: 2,
      getoetet: 1,
      ueberlebende: [{ datei: 'src/main/java/a/A.java', zeile: 2, methode: 'n', mutator: 'M' }],
    });
  });

  test('liest Attribute in doppelten Anfuehrungszeichen und eine Klasse ohne Paket', () => {
    const xml = umschlossen(
      mutation({ klasse: 'A', datei: 'A.java', methode: 'm', zeile: 1, mutator: 'M' }).replace(
        "detected='false'",
        'detected="false"',
      ),
    );

    expect(berichtLesen(xml).ueberlebende[0].datei).toBe('src/main/java/A.java');
  });

  test('liest einen Bericht ohne Mutationen', () => {
    expect(berichtLesen('<mutations partial="false"/>')).toEqual({
      erzeugt: 0,
      getoetet: 0,
      ueberlebende: [],
    });
  });

  test('uebergeht Kommentare und CDATA', () => {
    const xml =
      '<?xml version="1.0"?>\n<!-- von Hand -->\n<mutations>' +
      mutation({ klasse: 'a.A', datei: 'A.java', methode: 'm', zeile: 1, mutator: 'M' }).replace(
        '<description>x</description>',
        '<description><![CDATA[a < b]]></description>',
      ) +
      '</mutations>';

    expect(berichtLesen(xml).erzeugt).toBe(1);
  });

  test('wirft bei fehlendem Bericht', () => {
    expect(() => berichtLesen(undefined)).toThrow(/fehlt/);
    expect(() => berichtLesen(null)).toThrow(/fehlt/);
  });

  test('wirft bei leerem Text', () => {
    expect(() => berichtLesen('')).toThrow(/leer/);
    expect(() => berichtLesen('  \n')).toThrow(/leer/);
  });

  test('wirft bei unparsbarem Bericht und nennt den Grund', () => {
    expect(() => berichtLesen(bericht('mutations-kaputt.xml'))).toThrow(
      /kein gueltiges XML.*<\/mutatedClass>.*<sourceFile>/,
    );
  });

  test('wirft bei abgeschnittenem Bericht und nennt das offene Wurzelelement', () => {
    expect(() => berichtLesen(bericht('mutations-abgeschnitten.xml'))).toThrow(
      /abgeschnitten.*<mutations>.*nicht geschlossen/,
    );
  });

  test('wirft, wenn der Bericht mitten in einem Tag abbricht', () => {
    expect(() => berichtLesen('<mutations><mutation detec')).toThrow(/abgeschnitten/);
    expect(() => berichtLesen('<mutations><!-- offen')).toThrow(/abgeschnitten/);
    expect(() => berichtLesen('<mutations></mutat')).toThrow(/abgeschnitten/);
    expect(() => berichtLesen('<mutations><![CDATA[offen')).toThrow(/abgeschnitten/);
  });

  test.each([
    ['Text vor dem Wurzelelement', 'x<mutations/>', /Text ausserhalb/],
    ['Text hinter dem Wurzelelement', '<mutations/>x', /Text ausserhalb/],
    ['CDATA vor dem Wurzelelement', '<![CDATA[x]]><mutations/>', /Text ausserhalb/],
    ['kaputtes schliessendes Tag', '<mutations></ mutations>', /schliessendes Tag/],
    ['zweites Wurzelelement', '<mutations/><mutations/>', /zweites Wurzelelement/],
    ['schliessendes Tag ohne oeffnendes', '</mutations>', /ohne oeffnendes/],
    ['falsches Wurzelelement', '<report/>', /Wurzelelement <report>.*<mutations>/],
    ['unbekannte Entitaet', '<mutations>&nbsp;</mutations>', /Entitaet/],
    ['nacktes Kaufmanns-Und', '<mutations>a & b</mutations>', /Entitaet/],
    ['kaputtes Attribut', '<mutations partial=false/>', /Tag/],
    ['doppeltes Attribut', "<mutations a='1' a='2'/>", /doppelt/],
    ['nacktes Kleiner-Zeichen', '<mutations>a < b</mutations>', /Tag/],
    ['kein Wurzelelement', '<?xml version="1.0"?><!-- nur Kommentar -->', /kein Wurzelelement/],
  ])('wirft bei %s', (_fall, xml, grund) => {
    expect(() => berichtLesen(xml)).toThrow(grund);
  });

  test('versteht Zeichenreferenzen in Werten', () => {
    const xml = umschlossen(
      mutation({ klasse: 'a.A', datei: 'A.java', methode: '&#108;am&#x62;da&amp;&quot;&apos;', zeile: 1, mutator: 'M' }),
    );

    expect(berichtLesen(xml).ueberlebende[0].methode).toBe('lambda&"\'');
  });

  test('wirft, wenn einem Ueberlebenden eine Angabe fehlt', () => {
    const xml = umschlossen(
      mutation({ klasse: 'a.A', datei: 'A.java', methode: 'm', zeile: 1, mutator: 'M' }).replace(
        '<lineNumber>1</lineNumber>',
        '',
      ),
    );

    expect(() => berichtLesen(xml)).toThrow(/lineNumber/);
  });

  test('wirft bei einer Zeilennummer, die keine Zahl ist', () => {
    const xml = umschlossen(
      mutation({ klasse: 'a.A', datei: 'A.java', methode: 'm', zeile: 'zwoelf', mutator: 'M' }),
    );

    expect(() => berichtLesen(xml)).toThrow(/zwoelf/);
  });
});

describe('quote', () => {
  test('liegt fuer 1956 von 1957 ueber 0,8', () => {
    expect(quote(berichtLesen(bericht999()))).toBeGreaterThan(0.8);
  });

  test('liegt fuer den 75-%-Bericht darunter', () => {
    const q = quote(berichtLesen(bericht('mutations-75.xml')));

    expect(q).toBe(0.75);
    expect(q).toBeLessThan(0.8);
  });

  test('ist ohne erzeugte Mutanten nicht messbar', () => {
    expect(quote({ erzeugt: 0, getoetet: 0 })).toBeNull();
  });

  test('ist bei vollstaendig getoeteten Mutanten 1', () => {
    expect(quote({ erzeugt: 4, getoetet: 4 })).toBe(1);
  });
});

describe('quoteText', () => {
  test('rundet 0,7999 ab und zeigt nie 80,0 %', () => {
    expect(quoteText(0.7999)).toBe('79,9 %');
  });

  test('zeigt 1956 von 1957 als 99,9 %', () => {
    expect(quoteText(quote({ erzeugt: 1957, getoetet: 1956 }))).toBe('99,9 %');
  });

  test('zeigt glatte Werte mit einer Nachkommastelle', () => {
    expect(quoteText(1)).toBe('100,0 %');
    expect(quoteText(0)).toBe('0,0 %');
    expect(quoteText(0.75)).toBe('75,0 %');
  });

  test('rundet einen Wert, der in Gleitkomma knapp unter seiner Stelle liegt, nicht ab', () => {
    // 0.7 * 1000 ergibt 699.9999999999999 — abgerundet waere das faelschlich 69,9 %.
    expect(quoteText(0.7)).toBe('70,0 %');
    expect(quoteText(0.29)).toBe('29,0 %');
  });

  test('sagt ohne Quote, dass keine feststellbar ist', () => {
    expect(quoteText(null)).toBe('keine Quote feststellbar');
  });
});

describe('nachDateiGruppieren', () => {
  const stelle = (datei, zeile, methode = 'm', mutator = 'M') => ({ datei, zeile, methode, mutator });

  test('fasst zwei Ueberlebende derselben Datei zu einem Eintrag mit zwei Stellen zusammen', () => {
    const gruppen = nachDateiGruppieren([stelle('A.java', 2), stelle('A.java', 1)]);

    expect([...gruppen.keys()]).toEqual(['A.java']);
    expect(gruppen.get('A.java')).toEqual([stelle('A.java', 1), stelle('A.java', 2)]);
  });

  test('haelt zwei Dateien getrennt', () => {
    const gruppen = nachDateiGruppieren([stelle('A.java', 1), stelle('B.java', 1)]);

    expect(gruppen.get('A.java')).toEqual([stelle('A.java', 1)]);
    expect(gruppen.get('B.java')).toEqual([stelle('B.java', 1)]);
  });

  test('nennt zwei Stellen mit gleichem Tripel nur einmal', () => {
    const gruppen = nachDateiGruppieren([stelle('A.java', 1), stelle('A.java', 1)]);

    expect(gruppen.get('A.java')).toEqual([stelle('A.java', 1)]);
  });

  test('sortiert nach Zeile, dann Methode, dann Mutator', () => {
    const gruppen = nachDateiGruppieren([
      stelle('A.java', 10, 'b', 'X'),
      stelle('A.java', 9, 'z', 'Z'),
      stelle('A.java', 10, 'a', 'Y'),
      stelle('A.java', 10, 'a', 'X'),
    ]);

    expect(gruppen.get('A.java')).toEqual([
      stelle('A.java', 9, 'z', 'Z'),
      stelle('A.java', 10, 'a', 'X'),
      stelle('A.java', 10, 'a', 'Y'),
      stelle('A.java', 10, 'b', 'X'),
    ]);
  });

  test('ergibt ohne Ueberlebende eine leere Zuordnung', () => {
    expect(nachDateiGruppieren([]).size).toBe(0);
  });
});

/** Die Abschnitte eines Bodys, je Ueberschrift `## ` der Text bis zur naechsten. */
function abschnitte(body) {
  return body.split(/^(?=## )/m).filter((teil) => teil.startsWith('## '));
}

function abschnitt(body, ueberschrift) {
  return abschnitte(body).find((teil) => teil.startsWith(`${ueberschrift}\n`));
}

const tripel = (zeile, methode = 'm', mutator = 'M') => ({ zeile, methode, mutator });

describe('kartentitel', () => {
  test('beginnt mit [Task] und nennt den vollen Pfad', () => {
    const titel = kartentitel(ANGEBOT);

    expect(titel).toBe(`[Task] Mutations-Ueberlebende in ${ANGEBOT}`);
    expect(titel.startsWith('[Task] ')).toBe(true);
  });
});

describe('kartenbody', () => {
  const stellen = [tripel(27, '<init>', 'NullReturnValsMutator'), tripel(40, 'summe', 'MathMutator')];

  test('hat die vier Abschnitte in der Pflichtreihenfolge, Abhaengigkeiten zuletzt', () => {
    const body = kartenbody(ANGEBOT, stellen);

    expect(abschnitte(body).map((teil) => teil.split('\n')[0])).toEqual([
      '## Kontext',
      '## Aufgabe',
      '## Akzeptanzkriterium',
      '## Abhaengigkeiten',
    ]);
    expect(abschnitt(body, '## Abhaengigkeiten').trim()).toBe('## Abhaengigkeiten\n\nKeine.');
  });

  test('traegt im Kontext die Pflichtzeilen und den Satz zum Mutationsgate', () => {
    const kontext = abschnitt(kartenbody(ANGEBOT, stellen), '## Kontext');

    expect(kontext).toMatch(/^Autor-Modell: kein Modell, erzeugt vom CI-Mutationsgate$/m);
    expect(kontext).toMatch(/^Aufgabenstufe: \S/m);
    expect(kontext).toMatch(/^Stufengrund: \S/m);
    expect(kontext).toMatch(/^Sitzungsumfang: \S/m);
    expect(kontext).toMatch(/Diese Karte hat das CI-Mutationsgate erzeugt/);
  });

  test('nennt den Quellpfad in der Aufgabe als Backtick-Token, dazu die Stellen', () => {
    const aufgabe = abschnitt(kartenbody(ANGEBOT, stellen), '## Aufgabe');

    expect(aufgabe).toContain(`\`${ANGEBOT}\``);
    expect(aufgabe).toContain('Zeile 27');
    expect(aufgabe).toContain('Zeile 40');
  });

  test('stellt das Mutationskommando nur unter die Manuelle Pruefung, nie ins Kriterium', () => {
    const config = JSON.parse(readFileSync(path.join(REPO, '.claude', 'workflow.config.json'), 'utf8'));
    const kriterium = abschnitt(kartenbody(ANGEBOT, stellen), '## Akzeptanzkriterium');
    const [vorManuell, manuell] = kriterium.split('### Manuelle Pruefung');

    expect(vorManuell).toContain(
      'Ein neuer Test toetet jede unten genannte Stelle; `mvn verify` bleibt gruen.',
    );
    expect(vorManuell).not.toContain(config.mutationCommand);
    expect(manuell.startsWith(' (Mensch, nicht Teil des Session-Abschlusses)\n')).toBe(true);
    expect(manuell).toContain(config.mutationCommand);
  });

  test('liefert seine Stellen ueber stellenAusBody unveraendert zurueck', () => {
    expect(stellenAusBody(kartenbody(ANGEBOT, stellen))).toEqual(stellen);
  });

  test('sortiert, entdoppelt und laesst die Datei der Stelle weg', () => {
    const body = kartenbody(ANGEBOT, [
      { datei: ANGEBOT, ...tripel(40, 'summe', 'MathMutator') },
      tripel(27, '<init>', 'NullReturnValsMutator'),
      tripel(40, 'summe', 'MathMutator'),
    ]);

    expect(stellenAusBody(body)).toEqual(stellen);
  });
});

describe('stellenAusBody', () => {
  test('liest nur die Stellen in der Aufgabe, nicht gleich aussehende Zeilen anderswo', () => {
    const body = kartenbody('A.java', [tripel(1)]).replace(
      'Keine.',
      'Keine.\n\n- Zeile 9, Methode `x`, Mutator `Y`',
    );

    expect(stellenAusBody(body)).toEqual([tripel(1)]);
  });

  test('liefert ohne Abschnitt Aufgabe keine Stellen', () => {
    expect(stellenAusBody('## Kontext\n\nnichts\n')).toEqual([]);
  });
});

describe('bodyMitStellen', () => {
  const body = kartenbody(ANGEBOT, [tripel(10, 'a'), tripel(30, 'c')]);

  test('fuegt eine neue Stelle sortiert ein und verdoppelt eine vorhandene nicht', () => {
    const ergaenzt = bodyMitStellen(body, [{ datei: ANGEBOT, ...tripel(20, 'b') }, tripel(10, 'a')]);

    expect(stellenAusBody(ergaenzt)).toEqual([tripel(10, 'a'), tripel(20, 'b'), tripel(30, 'c')]);
  });

  test('laesst jeden anderen Abschnitt Zeichen fuer Zeichen stehen', () => {
    const ergaenzt = bodyMitStellen(body, [tripel(20, 'b')]);
    const ohneAufgabe = (text) => abschnitte(text).filter((teil) => !teil.startsWith('## Aufgabe\n'));

    expect(ohneAufgabe(ergaenzt)).toEqual(ohneAufgabe(body));
    expect(ohneAufgabe(ergaenzt)).toHaveLength(3);
  });

  test('laesst die uebrigen Zeilen der Aufgabe stehen', () => {
    const vonHand = body.replace('## Aufgabe\n', '## Aufgabe\n\nVon Hand ergaenzt.\n');
    const ergaenzt = bodyMitStellen(vonHand, [tripel(20, 'b')]);
    const zeilenOhneStellen = (text) =>
      abschnitt(text, '## Aufgabe')
        .split('\n')
        .filter((zeile) => !zeile.startsWith('- Zeile '));

    expect(zeilenOhneStellen(ergaenzt)).toEqual(zeilenOhneStellen(vonHand));
  });

  test('ist ohne neue Stelle der unveraenderte Body', () => {
    expect(bodyMitStellen(body, [tripel(30, 'c')])).toBe(body);
  });

  test('wirft, wenn die Aufgabe keine Stellenliste traegt', () => {
    expect(() => bodyMitStellen('## Aufgabe\n\nfrei\n', [tripel(1)])).toThrow(/Stellenliste/);
  });
});

describe('ohneBefund', () => {
  const karte = [tripel(1), tripel(2)];

  test('ist true, wenn keine aufgefuehrte Stelle mehr gefunden wird', () => {
    expect(ohneBefund(karte, [{ datei: 'A.java', ...tripel(3) }])).toBe(true);
    expect(ohneBefund(karte, [])).toBe(true);
  });

  test('ist false, sobald noch eine aufgefuehrte Stelle gefunden wird', () => {
    expect(ohneBefund(karte, [tripel(3), { datei: 'A.java', ...tripel(2) }])).toBe(false);
  });

  test('vergleicht alle drei Teile der Stelle', () => {
    expect(ohneBefund(karte, [tripel(1, 'n'), tripel(1, 'm', 'N')])).toBe(true);
  });
});

describe('anlagedatum', () => {
  const zeit = '2026-10-01T08:00:00Z';

  test.each([['createdAt'], ['created_at'], ['created']])('liest %s', (feld) => {
    expect(anlagedatum({ id: 1, [feld]: zeit })).toBe(zeit);
  });

  test('nimmt den ersten vorhandenen Wert in der Reihenfolge createdAt, created_at, created', () => {
    expect(anlagedatum({ created_at: 'b', created: 'c' })).toBe('b');
    expect(anlagedatum({ createdAt: 'a', created_at: 'b', created: 'c' })).toBe('a');
  });

  test('liefert null, wenn alle drei fehlen', () => {
    expect(anlagedatum({ id: 1 })).toBeNull();
    expect(anlagedatum({ createdAt: null })).toBeNull();
  });
});

describe('Liegezeit', () => {
  const anlage = '2026-10-01T08:00:00Z';
  const tag = 24 * 60 * 60 * 1000;
  const nach = (ms) => new Date(Date.parse(anlage) + ms);

  test('alterInTagen liefert fuer acht Tage den Wert 8', () => {
    expect(alterInTagen(anlage, nach(8 * tag))).toBe(8);
  });

  test('alterInTagen nimmt auch ein Date als Anlage', () => {
    expect(alterInTagen(new Date(anlage), nach(tag / 2))).toBe(0.5);
  });

  test('zuAlt ist false bei genau 7 x 24 Stunden', () => {
    expect(zuAlt(anlage, nach(7 * tag))).toBe(false);
  });

  test('zuAlt ist true bei 7 x 24 Stunden plus einer Minute', () => {
    expect(zuAlt(anlage, nach(7 * tag + 60 * 1000))).toBe(true);
  });

  test('wirft bei einem Anlagedatum, das keine Zeit ist, statt still durchzulassen', () => {
    expect(() => alterInTagen('gestern', nach(0))).toThrow(/gestern/);
    expect(() => zuAlt(null, nach(0))).toThrow(/Anlagedatum/);
  });
});

describe('Spaltenregel', () => {
  test('offen sind genau Backlog, Ready und In progress', () => {
    expect(OFFENE_SPALTEN).toEqual(['BACKLOG', 'READY', 'IN_PROGRESS']);
  });

  test('ergaenzt wird in den offenen Spalten und in In review', () => {
    expect(ERGAENZBARE_SPALTEN).toEqual(['BACKLOG', 'READY', 'IN_PROGRESS', 'IN_REVIEW']);
  });
});
