import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { describe, expect, test } from 'vitest';

import { berichtLesen, nachDateiGruppieren, quote, quoteText } from './mutationsbericht.mjs';

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
