import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { EigeneAngaben } from '../api/eigeneAngaben';
import KopfPfad, { KopfPfadProvider } from '../components/KopfPfad';
import { fetchNachPfad, json, leer, problem } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import EigeneAngabenMaske from './EigeneAngabenMaske';

const ANGABEN: EigeneAngaben = {
  name: 'Manfred Wolff',
  strasse: 'Hauptstraße 1',
  plz: '28195',
  ort: 'Bremen',
  land: 'Deutschland',
  email: 'info@mwolff.org',
  telefon: '0421/1234',
  steuernummer: '75/123/45678',
  umsatzsteuerId: 'DE123456789',
  bankverbindung: 'DE02120300000000202051',
  zahlungsbedingungen: 'Zahlbar innerhalb 14 Tagen ohne Abzug.',
};

/** Die frische Instanz: die Zeile steht, aber kein Feld ist gepflegt. */
const LEER: EigeneAngaben = {
  name: null,
  strasse: null,
  plz: null,
  ort: null,
  land: null,
  email: null,
  telefon: null,
  steuernummer: null,
  umsatzsteuerId: null,
  bankverbindung: null,
  zahlungsbedingungen: null,
};

const PFAD = 'GET /api/eigene-angaben';
const SCHREIBEN = 'PUT /api/eigene-angaben';

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

afterEach(() => {
  vi.restoreAllMocks();
});

describe('EigeneAngabenMaske', () => {
  it('zeigt nach dem Laden die vorhandenen Werte in ihren Feldern', async () => {
    fetchNachPfad({ [PFAD]: json(200, ANGABEN) });

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
    expect(feld('Zahlungsbedingungen')).toHaveValue('Zahlbar innerhalb 14 Tagen ohne Abzug.');
  });

  it('laesst auf einer frischen Instanz jedes Feld leer — kein Platzhalter im Feld', async () => {
    fetchNachPfad({ [PFAD]: json(200, LEER) });

    renderMaske();

    expect(await screen.findByRole('textbox', { name: 'Name' })).toHaveValue('');
    expect(feld('Zahlungsbedingungen')).toHaveValue('');
  });

  it('meldet sich mit „Eigene Angaben" im Kopf und traegt genau eine h1', async () => {
    fetchNachPfad({ [PFAD]: json(200, ANGABEN) });

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

  it('gibt jedem Eingabefeld eine Beschriftung', async () => {
    fetchNachPfad({ [PFAD]: json(200, ANGABEN) });

    renderMaske();
    await screen.findByRole('textbox', { name: 'Name' });

    const felder = screen.getAllByRole('textbox');
    expect(felder).toHaveLength(11);
    for (const eingabe of felder) {
      expect(eingabe).toHaveAccessibleName();
    }
  });

  it('gibt die Zahlungsbedingungen als mehrzeiliges Feld', async () => {
    fetchNachPfad({ [PFAD]: json(200, ANGABEN) });

    renderMaske();
    await screen.findByRole('textbox', { name: 'Name' });

    expect(feld('Zahlungsbedingungen').tagName).toBe('TEXTAREA');
    expect(feld('Name').tagName).toBe('INPUT');
  });

  it('schickt die geaenderten Angaben mit PUT und meldet den Erfolg', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      [PFAD]: json(200, ANGABEN),
      [SCHREIBEN]: leer(204),
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
        body: JSON.stringify({ ...ANGABEN, ort: 'Hamburg' }),
      }),
    );
  });

  it('schickt ein geleertes Feld als null und nicht als Leerstring', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      [PFAD]: json(200, ANGABEN),
      [SCHREIBEN]: leer(204),
    });

    renderMaske();
    await nutzer.clear(await screen.findByRole('textbox', { name: 'Steuernummer' }));
    // Auch reine Leerzeichen sind keine Angabe.
    await nutzer.type(feld('Telefon'), '   ');
    await nutzer.clear(feld('Zahlungsbedingungen'));
    await nutzer.click(speichern());

    await screen.findByText('Die Angaben sind gespeichert.');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/eigene-angaben',
      expect.objectContaining({
        body: JSON.stringify({
          ...ANGABEN,
          steuernummer: null,
          telefon: '0421/1234',
          zahlungsbedingungen: null,
        }),
      }),
    );
  });

  it('haengt die Meldung des Servers an ihr Feld', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
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
    fetchNachPfad({ [PFAD]: json(200, ANGABEN), [SCHREIBEN]: leer(503) });

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
    fetchNachPfad({ [PFAD]: json(200, ANGABEN), [SCHREIBEN]: leer(503) });

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
    fetchNachPfad({ [PFAD]: json(200, ANGABEN), [SCHREIBEN]: leer(204) });

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
    fetchNachPfad({ [PFAD]: json(200, ANGABEN) });

    renderMaske();

    expect(screen.getByText('Die eigenen Angaben werden geladen …')).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('meldet einen Ausfall beim Laden und zeigt kein Formular', async () => {
    fetchNachPfad({ [PFAD]: leer(503) });

    renderMaske();

    expect(
      await screen.findByText(
        'Die eigenen Angaben sind gerade nicht zu erreichen. Bitte später erneut versuchen.',
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('sperrt die Absendetaste, solange das Speichern laeuft', async () => {
    const nutzer = userEvent.setup();
    let loese: (() => void) | undefined;
    fetchNachPfad({
      [PFAD]: json(200, ANGABEN),
      [SCHREIBEN]: () =>
        new Promise<Response>((fertig) => {
          loese = () => {
            fertig(new Response(null, { status: 204 }));
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
