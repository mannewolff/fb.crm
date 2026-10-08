import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import KopfPfad, { KopfPfadProvider } from '../components/KopfPfad';
import { fetchNachPfad, json, leer, problem } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import AdministrationPage from './AdministrationPage';

/** Die Antwort der Schnittstelle, wie das Backend sie schreibt. */
const EINSTELLUNGEN = {
  nummerMuster: '{NNNN}-{JJJJ}',
  naechsteNummer: 3,
  steuersatz: 19,
  zahlungszielTage: 10,
};

const PFAD = 'GET /api/rechnung/einstellungen';
const SCHREIBEN = 'PUT /api/rechnung/einstellungen';

const MUSTER = 'Muster der Rechnungsnummer';
const NUMMER = 'Nächste laufende Nummer';
const STEUER = 'Mehrwertsteuersatz in Prozent';
const ZIEL = 'Zahlungsziel in Tagen';

function renderSeite() {
  return renderMitTheme(
    <MemoryRouter initialEntries={['/administration']}>
      <KopfPfadProvider>
        <KopfPfad />
        <AdministrationPage />
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

function vorschau() {
  return screen.getByTestId('nummer-vorschau');
}

/**
 * Ein Feld leeren und neu befuellen — der haeufigste Handgriff dieser Proben.
 *
 * Die geschweifte Klammer wird dabei verdoppelt: `userEvent.type` liest `{…}` sonst als Taste und
 * nicht als Zeichen — und genau diese Klammern sind hier der Inhalt.
 */
async function ersetze(nutzer: ReturnType<typeof userEvent.setup>, name: string, wert: string) {
  await nutzer.clear(feld(name));
  if (wert !== '') {
    await nutzer.type(feld(name), wert.replace(/\{/gu, '{{'));
  }
}

beforeEach(() => {
  // Die Vorschau rechnet mit dem heutigen Jahr; ohne feste Zeit liefe die Probe zum Jahreswechsel
  // auf einen anderen Erwartungswert.
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.setSystemTime(new Date('2026-09-30T10:00:00Z'));
});

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe('AdministrationPage', () => {
  it('zeigt waehrend des Ladens einen Hinweis statt eines leeren Formulars', () => {
    fetchNachPfad({ [PFAD]: json(200, EINSTELLUNGEN) });

    renderSeite();

    expect(screen.getByText('Die Einstellungen werden geladen …')).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('meldet einen Ausfall beim Laden und zeigt kein Formular', async () => {
    fetchNachPfad({ [PFAD]: leer(503) });

    renderSeite();

    expect(
      await screen.findByText(
        'Die Einstellungen zur Rechnung sind gerade nicht zu erreichen. Bitte später erneut versuchen.',
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('zeigt nach dem Laden die Vorbelegungen in ihren Feldern', async () => {
    fetchNachPfad({ [PFAD]: json(200, EINSTELLUNGEN) });

    renderSeite();

    expect(await screen.findByRole('textbox', { name: MUSTER })).toHaveValue('{NNNN}-{JJJJ}');
    expect(feld(NUMMER)).toHaveValue('3');
    // Der Steuersatz steht mit Komma — so schreibt man ihn hierzulande (E5).
    expect(feld(STEUER)).toHaveValue('19,00');
    expect(feld(ZIEL)).toHaveValue('10');
  });

  it('traegt „Administration" als einzige h1 und „Rechnung" als Bereich darunter', async () => {
    fetchNachPfad({ [PFAD]: json(200, EINSTELLUNGEN) });

    renderSeite();
    await screen.findByRole('textbox', { name: MUSTER });

    const ueberschriften = screen.getAllByRole('heading', { level: 1 });
    expect(ueberschriften).toHaveLength(1);
    expect(ueberschriften[0]).toHaveTextContent('Administration');
    expect(screen.getByRole('heading', { level: 2, name: 'Rechnung' })).toBeInTheDocument();
  });

  it('nennt am Feld des Musters die Platzhalter mit einem Beispiel', async () => {
    fetchNachPfad({ [PFAD]: json(200, EINSTELLUNGEN) });

    renderSeite();

    expect(await screen.findByRole('textbox', { name: MUSTER })).toHaveAccessibleDescription(
      /\{NNNN\}.*\{JJJJ\}.*\{JJ\}/u,
    );
  });

  it('zeigt die Vorschau der naechsten Nummer mit dem heutigen Jahr', async () => {
    fetchNachPfad({ [PFAD]: json(200, EINSTELLUNGEN) });

    renderSeite();
    await screen.findByRole('textbox', { name: MUSTER });

    expect(vorschau()).toHaveTextContent('0003-2026');
  });

  it('aendert die Vorschau, waehrend das Muster geaendert wird', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ [PFAD]: json(200, EINSTELLUNGEN) });

    renderSeite();
    await screen.findByRole('textbox', { name: MUSTER });
    await ersetze(nutzer, MUSTER, 'R{JJ}-{NNNN}');

    expect(vorschau()).toHaveTextContent('R26-0003');
  });

  it('aendert die Vorschau, waehrend die naechste Nummer geaendert wird', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ [PFAD]: json(200, EINSTELLUNGEN) });

    renderSeite();
    await screen.findByRole('textbox', { name: MUSTER });
    await ersetze(nutzer, NUMMER, '10000');

    // Die Stellenzahl ist eine Mindestbreite: Laengeres geht vollstaendig hinaus (#159, K3).
    expect(vorschau()).toHaveTextContent('10000-2026');
  });

  it('setzt an die Stelle der Vorschau einen Hinweis, wenn das Muster nicht taugt', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ [PFAD]: json(200, EINSTELLUNGEN) });

    renderSeite();
    await screen.findByRole('textbox', { name: MUSTER });
    await ersetze(nutzer, MUSTER, '{JJJJ}');

    expect(vorschau()).toHaveTextContent('Keine Vorschau: Muster oder nächste Nummer sind nicht gültig.');
  });

  it('setzt an die Stelle der Vorschau einen Hinweis, wenn die naechste Nummer nicht taugt', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ [PFAD]: json(200, EINSTELLUNGEN) });

    renderSeite();
    await screen.findByRole('textbox', { name: MUSTER });
    await ersetze(nutzer, NUMMER, '0');

    expect(vorschau()).toHaveTextContent('Keine Vorschau: Muster oder nächste Nummer sind nicht gültig.');
  });

  it('haelt ein Muster ohne Platzhalter der Nummer zurueck und meldet es am Feld', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ [PFAD]: json(200, EINSTELLUNGEN) });

    renderSeite();
    await screen.findByRole('textbox', { name: MUSTER });
    await ersetze(nutzer, MUSTER, 'RE-{JJJJ}');
    await nutzer.click(speichern());

    expect(feld(MUSTER)).toHaveAccessibleDescription(/genau einen Platzhalter/u);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    // Die uebrigen Eingaben bleiben stehen (#159, K10).
    expect(feld(STEUER)).toHaveValue('19,00');
  });

  it('haelt eine naechste Nummer unter 1 zurueck und meldet es am Feld', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ [PFAD]: json(200, EINSTELLUNGEN) });

    renderSeite();
    await screen.findByRole('textbox', { name: MUSTER });
    await ersetze(nutzer, NUMMER, '0');
    await nutzer.click(speichern());

    expect(feld(NUMMER)).toHaveAccessibleDescription('Die nächste Nummer ist eine ganze Zahl ab 1.');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('haelt einen Steuersatz ueber 100 zurueck und meldet es am Feld', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ [PFAD]: json(200, EINSTELLUNGEN) });

    renderSeite();
    await screen.findByRole('textbox', { name: MUSTER });
    await ersetze(nutzer, STEUER, '100,01');
    await nutzer.click(speichern());

    expect(feld(STEUER)).toHaveAccessibleDescription(
      'Der Steuersatz ist eine Zahl von 0 bis 100 mit höchstens zwei Nachkommastellen.',
    );
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('haelt einen Steuersatz mit drei Nachkommastellen zurueck', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ [PFAD]: json(200, EINSTELLUNGEN) });

    renderSeite();
    await screen.findByRole('textbox', { name: MUSTER });
    await ersetze(nutzer, STEUER, '19,555');
    await nutzer.click(speichern());

    expect(feld(STEUER)).toHaveAccessibleDescription(
      'Der Steuersatz ist eine Zahl von 0 bis 100 mit höchstens zwei Nachkommastellen.',
    );
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('haelt ein Zahlungsziel zurueck, das keine ganze Zahl ist', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({ [PFAD]: json(200, EINSTELLUNGEN) });

    renderSeite();
    await screen.findByRole('textbox', { name: MUSTER });
    await ersetze(nutzer, ZIEL, '14,5');
    await nutzer.click(speichern());

    expect(feld(ZIEL)).toHaveAccessibleDescription(
      'Das Zahlungsziel ist eine ganze Zahl von Tagen ab 0.',
    );
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('haengt die Meldung des Servers an ihr Feld', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({
      [PFAD]: json(200, EINSTELLUNGEN),
      [SCHREIBEN]: problem(400, 'Die Eingabe ist nicht gültig.', {
        nummerMuster: ['Das Muster ist nicht gültig.'],
        steuersatz: ['Der Steuersatz ist zu hoch.'],
      }),
    });

    renderSeite();
    await screen.findByRole('textbox', { name: MUSTER });
    await nutzer.click(speichern());

    expect(await screen.findByText(/Das Muster ist nicht gültig\./u)).toBeInTheDocument();
    expect(feld(STEUER)).toHaveAccessibleDescription('Der Steuersatz ist zu hoch.');
    // Eine feldweise Meldung steht am Feld und nicht zusaetzlich als Stoerung darueber.
    expect(screen.queryByText(/nicht gespeichert/u)).not.toBeInTheDocument();
  });

  it('meldet einen Fehlschlag ohne Feldbezug als Stoerung in der Karte', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ [PFAD]: json(200, EINSTELLUNGEN), [SCHREIBEN]: leer(503) });

    renderSeite();
    await screen.findByRole('textbox', { name: MUSTER });
    await nutzer.click(speichern());

    expect(
      await screen.findByText(
        'Die Einstellungen wurden nicht gespeichert. Bitte später erneut versuchen.',
      ),
    ).toBeInTheDocument();
  });

  it('schickt die vier Werte mit PUT und meldet den Erfolg', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({
      [PFAD]: json(200, EINSTELLUNGEN),
      [SCHREIBEN]: leer(204),
    });

    renderSeite();
    await screen.findByRole('textbox', { name: MUSTER });
    await ersetze(nutzer, MUSTER, 'R{JJ}-{NNNN}');
    await ersetze(nutzer, NUMMER, '4');
    await ersetze(nutzer, STEUER, '19,5');
    await ersetze(nutzer, ZIEL, '14');
    await nutzer.click(speichern());

    expect(await screen.findByText('Die Einstellungen sind gespeichert.')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/rechnung/einstellungen',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({
          nummerMuster: 'R{JJ}-{NNNN}',
          naechsteNummer: 4,
          steuersatz: '19.50',
          zahlungszielTage: 14,
        }),
      }),
    );
  });

  it('nimmt mit „Abbrechen" die Aenderungen auf den gespeicherten Stand zurueck', async () => {
    const nutzer = userEvent.setup();
    fetchNachPfad({ [PFAD]: json(200, EINSTELLUNGEN), [SCHREIBEN]: leer(503) });

    renderSeite();
    await screen.findByRole('textbox', { name: MUSTER });
    await ersetze(nutzer, MUSTER, 'R{JJ}-{NNNN}');
    await nutzer.click(speichern());
    await screen.findByText(/nicht gespeichert/u);
    await nutzer.click(screen.getByRole('button', { name: 'Abbrechen' }));

    expect(feld(MUSTER)).toHaveValue('{NNNN}-{JJJJ}');
    expect(vorschau()).toHaveTextContent('0003-2026');
    // Die Ruecknahme raeumt auch die Meldungen des letzten Versuchs weg.
    expect(screen.queryByText(/nicht gespeichert/u)).not.toBeInTheDocument();
  });

  it('sperrt die Absendetaste, solange das Speichern laeuft', async () => {
    const nutzer = userEvent.setup();
    let loese: (() => void) | undefined;
    fetchNachPfad({
      [PFAD]: json(200, EINSTELLUNGEN),
      [SCHREIBEN]: () =>
        new Promise<Response>((fertig) => {
          loese = () => {
            fertig(new Response(null, { status: 204 }));
          };
        }),
    });

    renderSeite();
    await screen.findByRole('textbox', { name: MUSTER });
    await nutzer.click(speichern());

    expect(speichern()).toBeDisabled();

    loese?.();

    expect(await screen.findByText('Die Einstellungen sind gespeichert.')).toBeInTheDocument();
    expect(speichern()).toBeEnabled();
  });
});
