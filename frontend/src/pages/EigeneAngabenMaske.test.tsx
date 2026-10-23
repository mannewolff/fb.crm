import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { GespeicherteEigeneAngaben } from '../api/eigeneAngaben';
import KopfPfad, { KopfPfadProvider } from '../components/KopfPfad';
import { fetchNachPfad, json, leer, problem } from '../test/fetchNachPfad';
import type { Routen } from '../test/fetchNachPfad';
import { alsRolle } from '../test/rechteDoppel';
import { renderMitTheme } from '../test/render';
import EigeneAngabenMaske from './EigeneAngabenMaske';

vi.mock('../auth/rechte', () => import('../test/rechteDoppel'));

const ANGABEN: GespeicherteEigeneAngaben = {
  name: 'Manfred Wolff',
  berufsbezeichnung: 'Freiberuflicher Softwareentwickler',
  strasse: 'Hauptstraße 1',
  plz: '28195',
  ort: 'Bremen',
  land: 'Deutschland',
  email: 'info@mwolff.org',
  telefon: '0421/1234',
  webadresse: 'https://mwolff.org',
  steuernummer: '75/123/45678',
  umsatzsteuerId: 'DE123456789',
  bankverbindung: 'DE02120300000000202051',
  version: 3,
};

/** Was Anna inzwischen gespeichert hat (#411, Kriterium 1). */
const ANNAS_STAND: GespeicherteEigeneAngaben = { ...ANGABEN, ort: 'Hamburg', version: 4 };

/** Die frische Instanz: die Zeile steht, aber kein Feld ist gepflegt. */
const LEER: GespeicherteEigeneAngaben = {
  name: null,
  berufsbezeichnung: null,
  strasse: null,
  plz: null,
  ort: null,
  land: null,
  email: null,
  telefon: null,
  webadresse: null,
  steuernummer: null,
  umsatzsteuerId: null,
  bankverbindung: null,
  version: 0,
};

const PFAD = 'GET /api/eigene-angaben';
const SCHREIBEN = 'PUT /api/eigene-angaben';

/** Der Stand des Briefbogens, den die Karte „Briefpapier" unter dem Formular selbst liest. */
const BRIEFBOGEN = {
  briefpapier: false,
  dateiname: null,
  groesse: null,
  seiten: null,
  absenderzeile: true,
  steuerBankZeile: true,
  links: 250,
  rechts: 200,
  inhaltOben: 985,
  unten: 200,
  folgeOben: 200,
  folgeUnten: 200,
  version: 0,
};

/**
 * Das fetch-Doppel der Maske samt dem Leseweg der Karte „Briefpapier" — sie laedt bei jeder Probe
 * mit, und ein fehlender Weg waere ein Aufruf ins Leere.
 */
function fetchMaske(routen: Routen) {
  return fetchNachPfad({ 'GET /api/briefpapier': json(200, BRIEFBOGEN), ...routen });
}

function renderMaske() {
  return renderMitTheme(
    <MemoryRouter initialEntries={['/eigene-angaben']}>
      <KopfPfadProvider>
        <KopfPfad />
        <EigeneAngabenMaske />
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

function feld(name: string) {
  return screen.getByRole('textbox', { name });
}

function speichern() {
  return screen.getByRole('button', { name: 'Speichern' });
}

/** Die Angaben ohne Stand — so, wie die Maske sie als Rumpf schickt. */
function rumpf(angaben: GespeicherteEigeneAngaben): Record<string, unknown> {
  return Object.fromEntries(Object.entries(angaben).filter(([feld]) => feld !== 'version'));
}

/** Heute um die genannte Uhrzeit in der Ortszeit — die Meldung nennt dann nur die Uhrzeit. */
function heuteUm(stunde: number, minute: number): string {
  const zeitpunkt = new Date();
  zeitpunkt.setHours(stunde, minute, 0, 0);
  return zeitpunkt.toISOString();
}

/** Die Abweisung des Servers, wie `GlobalExceptionHandler` sie schreibt (Plan #415, E4). */
function abweisung(geaendertVon: string): () => Response {
  return json(409, {
    title: 'Conflict',
    status: 409,
    detail: 'Dieser Eintrag wurde inzwischen geändert.',
    konflikt: { art: 'veraltet', geaendertAm: heuteUm(10, 42), eigenesFenster: false, geaendertVon },
  });
}

/** Antwortet der Reihe nach mit den Antworten — beim ersten Aufruf mit der ersten. */
function nacheinander(...antworten: readonly (() => Response)[]): () => Response {
  let aufruf = 0;
  return () => {
    const antwort = antworten[Math.min(aufruf, antworten.length - 1)];
    aufruf += 1;
    return antwort();
  };
}

/** Die Staende, die die PUT-Aufrufe der Maske als `If-Match` vorgelegt haben. */
function vorgelegteStaende(fetchMock: ReturnType<typeof fetchNachPfad>): readonly unknown[] {
  return fetchMock.mock.calls
    .filter(([pfad, init]) => pfad === '/api/eigene-angaben' && init?.method === 'PUT')
    .map(([, init]) => (init?.headers as Record<string, string> | undefined)?.['If-Match']);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('EigeneAngabenMaske', () => {
  it('zeigt nach dem Laden die vorhandenen Werte in ihren Feldern', async () => {
    fetchMaske({ [PFAD]: json(200, ANGABEN) });

    renderMaske();

    expect(await screen.findByRole('textbox', { name: 'Name' })).toHaveValue('Manfred Wolff');
    expect(feld('Straße und Hausnummer')).toHaveValue('Hauptstraße 1');
    expect(feld('Postleitzahl')).toHaveValue('28195');
    expect(feld('Ort')).toHaveValue('Bremen');
    expect(feld('Land')).toHaveValue('Deutschland');
    expect(feld('E-Mail-Adresse')).toHaveValue('info@mwolff.org');
    expect(feld('Telefon')).toHaveValue('0421/1234');
    expect(feld('Steuernummer')).toHaveValue('75/123/45678');
    expect(feld('Umsatzsteuer-Identifikationsnummer')).toHaveValue('DE123456789');
    expect(feld('Bankverbindung')).toHaveValue('DE02120300000000202051');
    expect(feld('Berufsbezeichnung')).toHaveValue('Freiberuflicher Softwareentwickler');
    expect(feld('Webadresse')).toHaveValue('https://mwolff.org');
  });

  it('laesst auf einer frischen Instanz jedes Feld leer — kein Platzhalter im Feld', async () => {
    fetchMaske({ [PFAD]: json(200, LEER) });

    renderMaske();

    expect(await screen.findByRole('textbox', { name: 'Name' })).toHaveValue('');
    expect(feld('Berufsbezeichnung')).toHaveValue('');
    expect(feld('Webadresse')).toHaveValue('');
  });

  it('traegt unter dem Formular die Karte „Briefpapier" und weiter genau eine h1', async () => {
    fetchMaske({ [PFAD]: json(200, ANGABEN) });

    renderMaske();
    await screen.findByRole('textbox', { name: 'Name' });

    const karte = await screen.findByRole('heading', { name: 'Briefpapier' });
    expect(karte.tagName).toBe('H2');
    expect(await screen.findByRole('textbox', { name: 'Linker Rand (mm)' })).toHaveValue('25,0');
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    // Unter dem Formular: Die Karte folgt der Speichern-Taste im Dokument.
    expect(
      speichern().compareDocumentPosition(karte) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it('meldet sich mit „Eigene Angaben" im Kopf und traegt genau eine h1', async () => {
    fetchMaske({ [PFAD]: json(200, ANGABEN) });

    renderMaske();
    await screen.findByRole('textbox', { name: 'Name' });

    // Die eine Ueberschrift der Ansicht: Es gibt keine Kopfkarte darueber, also traegt der
    // Kartenkopf die h1 (E1).
    const ueberschriften = screen.getAllByRole('heading', { level: 1 });
    expect(ueberschriften).toHaveLength(1);
    expect(ueberschriften[0]).toHaveTextContent('Eigene Angaben');
    expect(screen.getByRole('navigation', { name: 'Pfad' })).toHaveTextContent(
      'Eigene Angaben',
    );
  });

  it('gibt jedem Eingabefeld eine Beschriftung — auch denen der Karte „Briefpapier"', async () => {
    fetchMaske({ [PFAD]: json(200, ANGABEN) });

    renderMaske();
    await screen.findByRole('textbox', { name: 'Name' });
    await screen.findByRole('textbox', { name: 'Linker Rand (mm)' });

    // Zwoelf Felder der eigenen Angaben, sechs Raender des Briefbogens.
    const felder = screen.getAllByRole('textbox');
    expect(felder).toHaveLength(18);
    for (const eingabe of felder) {
      expect(eingabe).toHaveAccessibleName();
    }
  });

  it('fuehrt die Webadresse nur als Text und nicht als Verweis', async () => {
    fetchMaske({ [PFAD]: json(200, ANGABEN) });

    renderMaske();
    await screen.findByRole('textbox', { name: 'Name' });

    expect(feld('Webadresse').tagName).toBe('INPUT');
    expect(screen.queryByRole('link', { name: 'https://mwolff.org' })).not.toBeInTheDocument();
  });

  it('schickt die geaenderten Angaben mit PUT und meldet den Erfolg', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchMaske({
      [PFAD]: json(200, ANGABEN),
      [SCHREIBEN]: json(200, { ...ANGABEN, ort: 'Hamburg', version: 4 }),
    });

    renderMaske();
    await nutzer.clear(await screen.findByRole('textbox', { name: 'Ort' }));
    await nutzer.type(feld('Ort'), 'Hamburg');
    await nutzer.click(speichern());

    expect(await screen.findByText('Die Angaben sind gespeichert.')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/eigene-angaben',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({ ...rumpf(ANGABEN), ort: 'Hamburg' }),
      }),
    );
    // Plan #415, E2: vorgelegt wird der gelesene Stand.
    expect(vorgelegteStaende(fetchMock)).toEqual(['"3"']);
  });

  it('legt beim zweiten Speichern den Stand aus der Antwort des ersten vor', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchMaske({
      [PFAD]: json(200, ANGABEN),
      [SCHREIBEN]: nacheinander(
        json(200, { ...ANGABEN, ort: 'Hamburg', version: 4 }),
        json(200, { ...ANGABEN, ort: 'Kiel', version: 5 }),
      ),
    });

    renderMaske();
    await nutzer.clear(await screen.findByRole('textbox', { name: 'Ort' }));
    await nutzer.type(feld('Ort'), 'Hamburg');
    await nutzer.click(speichern());
    await screen.findByText('Die Angaben sind gespeichert.');
    await nutzer.clear(feld('Ort'));
    await nutzer.type(feld('Ort'), 'Kiel');
    await nutzer.click(speichern());

    // #411, Kriterium 8: Wer nacheinander speichert, merkt vom Schutz nichts.
    await waitFor(() => {
      expect(vorgelegteStaende(fetchMock)).toEqual(['"3"', '"4"']);
    });
    expect(screen.queryByText('Nicht ausgeführt')).not.toBeInTheDocument();
  });

  it('Szenario: abgewiesenes Speichern — die Eingaben bleiben, Neu laden stellt sie daneben', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchMaske({
      [PFAD]: nacheinander(json(200, ANGABEN), json(200, ANNAS_STAND)),
      [SCHREIBEN]: nacheinander(abweisung('Anna'), json(200, { ...ANNAS_STAND, version: 5 })),
    });

    renderMaske();
    await nutzer.clear(await screen.findByRole('textbox', { name: 'Telefon' }));
    await nutzer.type(feld('Telefon'), '040 9876');
    await nutzer.click(speichern());

    // Abgewiesen: Die Meldung nennt Anna, und die eigenen Werte stehen weiter in den Feldern.
    const meldung = await screen.findByRole('alert');
    expect(meldung).toHaveTextContent('Nicht ausgeführt');
    expect(meldung).toHaveTextContent('Anna hat diesen Eintrag inzwischen geändert (10:42).');
    expect(screen.queryByText(/nicht gespeichert/u)).not.toBeInTheDocument();
    expect(screen.queryByText('Die Angaben sind gespeichert.')).not.toBeInTheDocument();
    expect(feld('Telefon')).toHaveValue('040 9876');
    expect(feld('Ort')).toHaveValue('Bremen');

    // Neu laden: Annas Stand steht in der Maske, die eigenen Werte daneben zum Kopieren.
    await nutzer.click(screen.getByRole('button', { name: 'Neu laden' }));
    await waitFor(() => {
      expect(feld('Ort')).toHaveValue('Hamburg');
    });
    expect(feld('Telefon')).toHaveValue('0421/1234');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    const eigene = screen.getByRole('region', { name: 'Deine Eingaben' });
    expect(eigene).toHaveTextContent('Telefon');
    expect(eigene).toHaveTextContent('040 9876');
    expect(eigene).toHaveTextContent('Bremen');

    // Auf Annas Stand geht das Speichern durch; vorgelegt wird der neu geladene Stand.
    await nutzer.click(speichern());
    expect(await screen.findByText('Die Angaben sind gespeichert.')).toBeInTheDocument();
    expect(vorgelegteStaende(fetchMock)).toEqual(['"3"', '"4"']);

    // „Verwerfen" nimmt die eigenen Werte weg.
    await nutzer.click(screen.getByRole('button', { name: 'Verwerfen' }));
    expect(screen.queryByRole('region', { name: 'Deine Eingaben' })).not.toBeInTheDocument();
  });

  it('meldet einen Ausfall beim Neu laden und laesst die Konfliktmeldung stehen', async () => {
    const nutzer = userEvent.setup();
    fetchMaske({
      [PFAD]: nacheinander(json(200, ANGABEN), leer(503)),
      [SCHREIBEN]: abweisung('Anna'),
    });

    renderMaske();
    await nutzer.clear(await screen.findByRole('textbox', { name: 'Telefon' }));
    await nutzer.type(feld('Telefon'), '040 9876');
    await nutzer.click(speichern());
    await nutzer.click(await screen.findByRole('button', { name: 'Neu laden' }));

    expect(
      await screen.findByText(
        'Der aktuelle Stand ist gerade nicht zu erreichen. Bitte später erneut versuchen.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Neu laden' })).toBeInTheDocument();
    expect(feld('Telefon')).toHaveValue('040 9876');
    expect(screen.queryByRole('region', { name: 'Deine Eingaben' })).not.toBeInTheDocument();
  });

  it('schickt ein geleertes Feld als null und nicht als Leerstring', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchMaske({
      [PFAD]: json(200, ANGABEN),
      [SCHREIBEN]: json(200, { ...ANGABEN, version: 4 }),
    });

    renderMaske();
    await nutzer.clear(await screen.findByRole('textbox', { name: 'Steuernummer' }));
    // Auch reine Leerzeichen sind keine Angabe.
    await nutzer.type(feld('Telefon'), '   ');
    await nutzer.clear(feld('Berufsbezeichnung'));
    await nutzer.click(speichern());

    await screen.findByText('Die Angaben sind gespeichert.');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/eigene-angaben',
      expect.objectContaining({
        body: JSON.stringify({
          ...rumpf(ANGABEN),
          berufsbezeichnung: null,
          steuernummer: null,
          telefon: '0421/1234',
        }),
      }),
    );
  });

  it('haengt die Meldung des Servers an ihr Feld', async () => {
    const nutzer = userEvent.setup();
    fetchMaske({
      [PFAD]: json(200, ANGABEN),
      [SCHREIBEN]: problem(400, 'Die Eingabe ist nicht gültig.', {
        plz: ['Die Postleitzahl ist zu lang.'],
        email: ['Die Adresse ist zu lang.'],
      }),
    });

    renderMaske();
    await screen.findByRole('textbox', { name: 'Name' });
    await nutzer.click(speichern());

    expect(await screen.findByText('Die Postleitzahl ist zu lang.')).toBeInTheDocument();
    expect(feld('Postleitzahl')).toHaveAccessibleDescription('Die Postleitzahl ist zu lang.');
    expect(feld('E-Mail-Adresse')).toHaveAccessibleDescription('Die Adresse ist zu lang.');
    // Eine feldweise Meldung steht am Feld und nicht zusaetzlich als Stoerung darueber.
    expect(screen.queryByText(/nicht gespeichert/)).not.toBeInTheDocument();
  });

  it('meldet einen Fehlschlag ohne Feldbezug als Stoerung ueber dem Formular', async () => {
    const nutzer = userEvent.setup();
    fetchMaske({ [PFAD]: json(200, ANGABEN), [SCHREIBEN]: leer(503) });

    renderMaske();
    await screen.findByRole('textbox', { name: 'Name' });
    await nutzer.click(speichern());

    expect(
      await screen.findByText(
        'Die Angaben wurden nicht gespeichert. Bitte später erneut versuchen.',
      ),
    ).toBeInTheDocument();
  });

  it('nimmt mit „Abbrechen" die Aenderungen auf den gespeicherten Stand zurueck', async () => {
    // Ueber „Eigene Angaben" gibt es keine Ebene, auf die „Abbrechen" fuehren koennte — es
    // verwirft daher die Aenderungen an der Stelle (E2).
    const nutzer = userEvent.setup();
    fetchMaske({ [PFAD]: json(200, ANGABEN), [SCHREIBEN]: leer(503) });

    renderMaske();
    await nutzer.clear(await screen.findByRole('textbox', { name: 'Ort' }));
    await nutzer.type(feld('Ort'), 'Hamburg');
    await nutzer.click(speichern());
    await screen.findByText(/nicht gespeichert/);
    await nutzer.click(screen.getByRole('button', { name: 'Abbrechen' }));

    expect(feld('Ort')).toHaveValue('Bremen');
    // Die Ruecknahme raeumt auch die Meldungen des letzten Versuchs weg.
    expect(screen.queryByText(/nicht gespeichert/)).not.toBeInTheDocument();
  });

  it('nimmt nach dem Speichern auf den neuen Stand zurueck, nicht auf den geladenen', async () => {
    const nutzer = userEvent.setup();
    fetchMaske({
      [PFAD]: json(200, ANGABEN),
      [SCHREIBEN]: json(200, { ...ANGABEN, ort: 'Hamburg', version: 4 }),
    });

    renderMaske();
    await nutzer.clear(await screen.findByRole('textbox', { name: 'Ort' }));
    await nutzer.type(feld('Ort'), 'Hamburg');
    await nutzer.click(speichern());
    await screen.findByText('Die Angaben sind gespeichert.');
    await nutzer.clear(feld('Ort'));
    await nutzer.click(screen.getByRole('button', { name: 'Abbrechen' }));

    expect(feld('Ort')).toHaveValue('Hamburg');
  });

  it('zeigt waehrend des Ladens einen Hinweis statt eines leeren Formulars', () => {
    fetchMaske({ [PFAD]: json(200, ANGABEN) });

    renderMaske();

    expect(screen.getByText('Die eigenen Angaben werden geladen …')).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('meldet einen Ausfall beim Laden und zeigt kein Formular', async () => {
    fetchMaske({ [PFAD]: leer(503) });

    renderMaske();

    expect(
      await screen.findByText(
        'Die eigenen Angaben sind gerade nicht zu erreichen. Bitte später erneut versuchen.',
      ),
    ).toBeInTheDocument();
    // Die Karte „Briefpapier" laedt fuer sich; vom Formular der eigenen Angaben steht nichts da.
    expect(screen.queryByRole('textbox', { name: 'Name' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Speichern' })).not.toBeInTheDocument();
  });

  it('sperrt die Absendetaste, solange das Speichern laeuft', async () => {
    const nutzer = userEvent.setup();
    let loese: (() => void) | undefined;
    fetchMaske({
      [PFAD]: json(200, ANGABEN),
      [SCHREIBEN]: () =>
        new Promise<Response>((fertig) => {
          loese = () => {
            fertig(json(200, { ...ANGABEN, version: 4 })());
          };
        }),
    });

    renderMaske();
    await screen.findByRole('textbox', { name: 'Name' });
    await nutzer.click(speichern());

    expect(speichern()).toBeDisabled();

    loese?.();

    expect(await screen.findByText('Die Angaben sind gespeichert.')).toBeInTheDocument();
    expect(speichern()).toBeEnabled();
  });
});

describe('EigeneAngabenMaske — je Rolle (Kriterien 9 und 10 von #409, Plan #414, A10, E1)', () => {
  it('zeigt ReadOnly die Angaben ohne „Speichern" und „Abbrechen"', async () => {
    alsRolle('READ_ONLY');
    fetchMaske({ [PFAD]: json(200, ANGABEN) });

    renderMaske();

    expect(await screen.findByRole('textbox', { name: 'Name' })).toHaveValue('Manfred Wolff');
    expect(screen.queryByRole('button', { name: 'Speichern' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Abbrechen' })).not.toBeInTheDocument();
  });

  it('zeigt einem C-Level „Speichern" und „Abbrechen"', async () => {
    alsRolle('C_LEVEL');
    fetchMaske({ [PFAD]: json(200, ANGABEN) });

    renderMaske();

    expect(await screen.findByRole('button', { name: 'Speichern' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Abbrechen' })).toBeInTheDocument();
  });
});
