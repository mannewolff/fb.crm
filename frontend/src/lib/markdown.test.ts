import { describe, expect, it } from 'vitest';

import { ankerName, leseMarkdown, type Block, type Inline } from './markdown';

describe('leseMarkdown', () => {
  it('liefert die #-Ueberschrift als Titel und erzeugt dafuer keinen Block (E7)', () => {
    const dokument = leseMarkdown('# Bedienung\n\nErster Satz.');

    expect(dokument.titel).toBe('Bedienung');
    expect(dokument.bloecke).toEqual([
      { art: 'text', inhalt: [{ art: 'klartext', text: 'Erster Satz.' }] },
    ]);
  });

  it('laesst den Titel leer, wenn die #-Ueberschrift fehlt', () => {
    expect(leseMarkdown('Nur Text.').titel).toBe('');
  });

  it('liefert eine zweite #-Ueberschrift als Fliesstext und verschluckt sie nicht (E4)', () => {
    const dokument = leseMarkdown('# Erster\n\n# Zweiter');

    expect(dokument.titel).toBe('Erster');
    expect(dokument.bloecke).toEqual([
      { art: 'text', inhalt: [{ art: 'klartext', text: '# Zweiter' }] },
    ]);
  });

  it('liefert ## und ### als Ueberschrift mit Ebene und Anker', () => {
    expect(leseMarkdown('## Betrieb unter eigenem Namen\n### Zertifikat').bloecke).toEqual([
      {
        art: 'ueberschrift',
        ebene: 2,
        text: 'Betrieb unter eigenem Namen',
        anker: 'betrieb-unter-eigenem-namen',
      },
      { art: 'ueberschrift', ebene: 3, text: 'Zertifikat', anker: 'zertifikat' },
    ]);
  });

  it('gibt zwei Ueberschriften mit gleichem Text eine laufende Nummer', () => {
    expect(leseMarkdown('## Sicherung\n\n## Sicherung\n\n### Sicherung').bloecke).toMatchObject([
      { anker: 'sicherung' },
      { anker: 'sicherung-2' },
      { anker: 'sicherung-3' },
    ]);
  });

  it('faellt fuer #### und eine Tabellenzeile auf einen Textblock mit ihrem Wortlaut zurueck (E4)', () => {
    expect(leseMarkdown('#### Vierte Ebene\n\n| Spalte | Wert |').bloecke).toEqual([
      { art: 'text', inhalt: [{ art: 'klartext', text: '#### Vierte Ebene' }] },
      { art: 'text', inhalt: [{ art: 'klartext', text: '| Spalte | Wert |' }] },
    ]);
  });

  it('liest eine Ueberschrift ohne Text als Fliesstext', () => {
    expect(leseMarkdown('##').bloecke).toEqual([
      { art: 'text', inhalt: [{ art: 'klartext', text: '##' }] },
    ]);
  });

  it('laesst ein # mitten im Fliesstext stehen', () => {
    expect(leseMarkdown('Siehe Abschnitt # 3 weiter unten.').bloecke).toEqual([
      { art: 'text', inhalt: [{ art: 'klartext', text: 'Siehe Abschnitt # 3 weiter unten.' }] },
    ]);
  });

  it('fasst aufeinanderfolgende Zeilen zu einem Absatz zusammen und trennt an der Leerzeile', () => {
    expect(leseMarkdown('Erste Zeile\n  zweite Zeile\n\n\nNeuer Absatz').bloecke).toEqual([
      { art: 'text', inhalt: [{ art: 'klartext', text: 'Erste Zeile zweite Zeile' }] },
      { art: 'text', inhalt: [{ art: 'klartext', text: 'Neuer Absatz' }] },
    ]);
  });

  it('liest Windows-Zeilenenden wie Unix-Zeilenenden', () => {
    expect(leseMarkdown('# Titel\r\n\r\nSatz.')).toEqual(leseMarkdown('# Titel\n\nSatz.'));
  });

  it('liefert fuer eine leere Quelle ein leeres Dokument', () => {
    expect(leseMarkdown('')).toEqual({ titel: '', bloecke: [] });
  });

  it('liest eine Aufzaehlung mit - und *', () => {
    expect(leseMarkdown('- erstens\n* zweitens').bloecke).toEqual([
      {
        art: 'liste',
        numeriert: false,
        eintraege: [
          [{ art: 'klartext', text: 'erstens' }],
          [{ art: 'klartext', text: 'zweitens' }],
        ],
      },
    ]);
  });

  it('liest eine numerierte Aufzaehlung', () => {
    expect(leseMarkdown('1. starten\n2. anmelden').bloecke).toEqual([
      {
        art: 'liste',
        numeriert: true,
        eintraege: [
          [{ art: 'klartext', text: 'starten' }],
          [{ art: 'klartext', text: 'anmelden' }],
        ],
      },
    ]);
  });

  it('haengt eine eingerueckte Zeile an den letzten Eintrag', () => {
    expect(leseMarkdown('1. starten und\n   warten').bloecke).toEqual([
      { art: 'liste', numeriert: true, eintraege: [[{ art: 'klartext', text: 'starten und warten' }]] },
    ]);
  });

  it('beginnt bei wechselnder Art eine neue Liste', () => {
    expect(leseMarkdown('- Punkt\n1. Nummer').bloecke).toMatchObject([
      { art: 'liste', numeriert: false },
      { art: 'liste', numeriert: true },
    ]);
  });

  it('beendet eine Liste an einer nicht eingerueckten Zeile, die kein Eintrag ist', () => {
    expect(leseMarkdown('Absatz davor\n- Punkt\nAbsatz danach').bloecke).toEqual([
      { art: 'text', inhalt: [{ art: 'klartext', text: 'Absatz davor' }] },
      { art: 'liste', numeriert: false, eintraege: [[{ art: 'klartext', text: 'Punkt' }]] },
      { art: 'text', inhalt: [{ art: 'klartext', text: 'Absatz danach' }] },
    ]);
  });

  it('liest einen Befehlsblock samt Leerzeilen und Markdown-Zeichen woertlich', () => {
    expect(leseMarkdown('```\ndocker compose up -d\n\n# kein Titel\n```\nDanach.')).toEqual({
      titel: '',
      bloecke: [
        { art: 'befehl', inhalt: 'docker compose up -d\n\n# kein Titel' },
        { art: 'text', inhalt: [{ art: 'klartext', text: 'Danach.' }] },
      ],
    });
  });

  it('verwirft die Sprachangabe am Zaun und liefert denselben Inhalt wie ohne sie (E3)', () => {
    const mit = leseMarkdown('```bash\ncp .env.example .env\n```');
    const ohne = leseMarkdown('```\ncp .env.example .env\n```');

    expect(mit).toEqual(ohne);
    expect(mit.bloecke).toEqual([{ art: 'befehl', inhalt: 'cp .env.example .env' }]);
    expect(JSON.stringify(mit)).not.toContain('bash');
  });

  it('beendet einen nicht geschlossenen Befehlsblock am Dateiende mit seinem Inhalt', () => {
    expect(leseMarkdown('Vorher.\n```bash\npg_dump fbcrm\ngzip sicherung.sql').bloecke).toEqual([
      { art: 'text', inhalt: [{ art: 'klartext', text: 'Vorher.' }] },
      { art: 'befehl', inhalt: 'pg_dump fbcrm\ngzip sicherung.sql' },
    ]);
  });

  it('schliesst eine offene Liste vor dem Befehlsblock', () => {
    expect(leseMarkdown('- Punkt\n```\nls\n```').bloecke).toEqual([
      { art: 'liste', numeriert: false, eintraege: [[{ art: 'klartext', text: 'Punkt' }]] },
      { art: 'befehl', inhalt: 'ls' },
    ]);
  });
});

describe('leseMarkdown, Inline-Folge', () => {
  // Eine Zeile ergibt genau einen Textblock; verglichen wird der ganze Block, ohne Verzweigung im
  // Test, weil die Testdatei selbst in der Zweigabdeckung steht.
  const bloecke = (zeile: string) => leseMarkdown(zeile).bloecke;
  const textblock = (...inhalt: Inline[]): Block[] => [{ art: 'text', inhalt }];

  it('liest Betonung und Schnipsel in derselben Zeile', () => {
    expect(bloecke('Mit **Vorsicht** den Befehl `docker compose down` rufen.')).toEqual(
      textblock(
        { art: 'klartext', text: 'Mit ' },
        { art: 'betont', text: 'Vorsicht' },
        { art: 'klartext', text: ' den Befehl ' },
        { art: 'schnipsel', text: 'docker compose down' },
        { art: 'klartext', text: ' rufen.' },
      ),
    );
  });

  it('liest im Schnipsel keine Betonung', () => {
    expect(bloecke('`**roh**`')).toEqual(textblock({ art: 'schnipsel', text: '**roh**' }));
  });

  it('liest einen Verweis', () => {
    expect(bloecke('Siehe [Sicherung](betrieb.md#sicherung).')).toEqual(
      textblock(
        { art: 'klartext', text: 'Siehe ' },
        { art: 'verweis', text: 'Sicherung', ziel: 'betrieb.md#sicherung' },
        { art: 'klartext', text: '.' },
      ),
    );
  });

  it('liest einen Verweis mit Klammern im Text und im Ziel', () => {
    expect(bloecke('[Betrieb (mit [Traefik])](https://de.wikipedia.org/wiki/Traefik_(Software))')).toEqual(
      textblock({
        art: 'verweis',
        text: 'Betrieb (mit [Traefik])',
        ziel: 'https://de.wikipedia.org/wiki/Traefik_(Software)',
      }),
    );
  });

  it('laesst unvollstaendige Auszeichnung als Klartext stehen (E4)', () => {
    for (const zeile of [
      'ein `offener Schnipsel',
      'ein leerer `` Schnipsel',
      'eine **offene Betonung',
      'eine leere **** Betonung',
      'eine [eckige Klammer ohne Ziel',
      'ein [Text] ohne Ziel',
      'ein [Verweis](ohne Ende',
      'ein [](leerer-text)',
      'ein [leeres Ziel]()',
    ]) {
      expect(bloecke(zeile)).toEqual(textblock({ art: 'klartext', text: zeile }));
    }
  });
});

describe('ankerName', () => {
  it('schreibt klein und setzt - zwischen die Woerter (E9)', () => {
    expect(ankerName('Betrieb unter eigenem Namen', [])).toBe('betrieb-unter-eigenem-namen');
  });

  it('schreibt Umlaute und ß aus', () => {
    expect(ankerName('Ändern, Öffnen, Übernehmen und Größe', [])).toBe(
      'aendern-oeffnen-uebernehmen-und-groesse',
    );
  });

  it('zieht mehrfache - zusammen, entfernt sie am Rand und laesst Ziffern stehen', () => {
    expect(ankerName('  Schritt 2 -- `docker` (neu)!  ', [])).toBe('schritt-2-docker-neu');
  });

  it('haengt an einen schon belegten Namen eine laufende Nummer an', () => {
    expect(ankerName('Sicherung', ['sicherung'])).toBe('sicherung-2');
    expect(ankerName('Sicherung', ['sicherung', 'sicherung-2'])).toBe('sicherung-3');
  });

  it('nennt einen Text ohne Buchstaben und Ziffern abschnitt', () => {
    expect(ankerName('???', [])).toBe('abschnitt');
    expect(ankerName('!', ['abschnitt'])).toBe('abschnitt-2');
  });
});
