import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { MAX_UPLOAD_BYTE } from '../lib/dateigroesse';
import { alsEingabe, alsZeitstempel } from '../lib/zeitpunkt';
import { fetchNachPfad, formularWeg, leer, problem } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import EintragMaske from './EintragMaske';

/**
 * Die Maske fuer einen Eintrag der Historie (Kriterien 13, 14, 18, 26).
 *
 * Zwei Dinge pruefen diese Tests genauer als die uebrigen Masken:
 *
 * <ul>
 *   <li><b>Was hinausgeht, ist ein Formular</b> (E21). Die Erwartungen lesen deshalb die
 *       {@link FormData} des Aufrufs und nicht einen JSON-Rumpf.</li>
 *   <li><b>Die Grenze der Dateigroesse greift vor dem Absenden</b> (E10, Kriterium 18): Eine zu
 *       grosse Datei darf gar nicht erst ans Netz gehen — geprueft wird darum auch, dass
 *       {@code fetch} ungerufen bleibt.</li>
 * </ul>
 */

const WEG = 'POST /api/vorgaenge/5/eintraege';

const TEXT = 'Kunde hat telefonisch angefragt.';

function renderMaske(hinzugefuegt: () => void) {
  renderMitTheme(<EintragMaske vorgangId={5} hinzugefuegt={hinzugefuegt} />);
}

function artWahl() {
  return screen.getByRole('combobox', { name: 'Art' });
}

function textFeld() {
  return screen.getByRole('textbox', { name: /^Text/ });
}

function zeitpunktFeld() {
  return screen.getByLabelText<HTMLInputElement>(/^Zeitpunkt/);
}

function dateiFeld() {
  return screen.getByLabelText<HTMLInputElement>(/^Datei/);
}

function taste() {
  return screen.getByRole('button', { name: 'Hinzufügen' });
}

/** Eine Datei der genannten Groesse — ohne sie tatsaechlich zu fuellen. */
function datei(name: string, bytes: number): File {
  const gewaehlt = new File(['x'], name, { type: 'application/pdf' });
  Object.defineProperty(gewaehlt, 'size', { value: bytes });
  return gewaehlt;
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('EintragMaske — die Art (Kriterien 13, 14)', () => {
  it('zeigt die Dateiwahl nur beim Anhang', async () => {
    const nutzer = userEvent.setup();
    renderMaske(vi.fn());

    expect(screen.queryByLabelText(/^Datei/)).not.toBeInTheDocument();

    await nutzer.selectOptions(artWahl(), 'ANHANG');
    expect(dateiFeld()).toBeInTheDocument();

    await nutzer.selectOptions(artWahl(), 'KOMMENTAR');
    expect(screen.queryByLabelText(/^Datei/)).not.toBeInTheDocument();
  });
});

describe('EintragMaske — der Zeitpunkt (Kriterium 13)', () => {
  it('belegt den Zeitpunkt mit „jetzt" vor', () => {
    const vorher = alsEingabe(new Date().toISOString());

    renderMaske(vi.fn());

    // Zwei erlaubte Werte, weil zwischen Aufbau und Erwartung die Minute umspringen kann.
    expect([vorher, alsEingabe(new Date().toISOString())]).toContain(zeitpunktFeld().value);
  });

  it('schickt den vorbelegten Zeitpunkt als Zeitstempel in UTC hinaus', async () => {
    const nutzer = userEvent.setup();
    let gesendet = new FormData();
    fetchNachPfad({
      [WEG]: formularWeg((formular) => {
        gesendet = formular;
      }, leer(201)),
    });
    renderMaske(vi.fn());
    const angezeigt = zeitpunktFeld().value;

    await nutzer.type(textFeld(), TEXT);
    await nutzer.click(taste());

    expect(gesendet.get('geschehenAm')).toBe(alsZeitstempel(angezeigt));
  });

  it('nimmt einen zurueckdatierten Zeitpunkt an', async () => {
    const nutzer = userEvent.setup();
    let gesendet = new FormData();
    fetchNachPfad({
      [WEG]: formularWeg((formular) => {
        gesendet = formular;
      }, leer(201)),
    });
    renderMaske(vi.fn());

    await nutzer.clear(zeitpunktFeld());
    await nutzer.type(zeitpunktFeld(), '2026-09-20T08:30');
    await nutzer.type(textFeld(), TEXT);
    await nutzer.click(taste());

    expect(gesendet.get('geschehenAm')).toBe(alsZeitstempel('2026-09-20T08:30'));
  });

  it('meldet einen leeren Zeitpunkt am Feld, ohne die Schnittstelle zu rufen', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({});
    renderMaske(vi.fn());

    await nutzer.type(textFeld(), TEXT);
    await nutzer.clear(zeitpunktFeld());
    await nutzer.click(taste());

    expect(zeitpunktFeld()).toHaveAccessibleDescription('Bitte einen Zeitpunkt angeben.');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('EintragMaske — Pflichtangaben je Art (Kriterien 13, 14, 26)', () => {
  it('meldet einen Kommentar ohne Text am Feld und sagt die Meldung an', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({});
    renderMaske(vi.fn());

    await nutzer.click(taste());

    expect(textFeld()).toHaveAccessibleDescription('Bitte einen Text angeben.');
    // Die Meldung steht in einem Live-Bereich: Sie wird angesagt, sobald sie erscheint.
    expect(screen.getByRole('alert')).toHaveTextContent('Bitte einen Text angeben.');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('meldet einen Anhang ohne Datei am Feld', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({});
    renderMaske(vi.fn());

    await nutzer.selectOptions(artWahl(), 'ANHANG');
    await nutzer.click(taste());

    expect(dateiFeld()).toHaveAccessibleDescription('Bitte eine Datei wählen.');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('EintragMaske — die Grenze der Dateigroesse (E10, Kriterium 18)', () => {
  it('weist eine zu grosse Datei vor dem Absenden ab und nennt die Grenze', async () => {
    const nutzer = userEvent.setup();
    const fetchMock = fetchNachPfad({});
    renderMaske(vi.fn());

    await nutzer.selectOptions(artWahl(), 'ANHANG');
    await nutzer.upload(dateiFeld(), datei('zu-gross.pdf', MAX_UPLOAD_BYTE + 1));
    await nutzer.click(taste());

    expect(dateiFeld()).toHaveAccessibleDescription('Die Datei darf höchstens 25 MiB groß sein.');
    // Der Sinn der Vorpruefung: Die Datei geht gar nicht erst ueber die Leitung.
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('laesst eine Datei genau auf der Grenze durch', async () => {
    const nutzer = userEvent.setup();
    let gesendet = new FormData();
    fetchNachPfad({
      [WEG]: formularWeg((formular) => {
        gesendet = formular;
      }, leer(201)),
    });
    renderMaske(vi.fn());

    await nutzer.selectOptions(artWahl(), 'ANHANG');
    await nutzer.upload(dateiFeld(), datei('anfrage.pdf', MAX_UPLOAD_BYTE));
    await nutzer.click(taste());

    expect(gesendet.get('datei')).toHaveProperty('name', 'anfrage.pdf');
  });
});

describe('EintragMaske — Hinzufuegen (Kriterien 13, 14)', () => {
  it('schickt den Kommentar als Formular hinaus, leert die Maske und fordert zum Neuladen auf', async () => {
    const nutzer = userEvent.setup();
    const hinzugefuegt = vi.fn();
    let gesendet = new FormData();
    const fetchMock = fetchNachPfad({
      [WEG]: formularWeg((formular) => {
        gesendet = formular;
      }, leer(201)),
    });
    renderMaske(hinzugefuegt);

    await nutzer.type(textFeld(), TEXT);
    const vorDemAbsenden = alsEingabe(new Date().toISOString());
    await nutzer.click(taste());

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/vorgaenge/5/eintraege',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(gesendet.get('art')).toBe('KOMMENTAR');
    expect(gesendet.get('text')).toBe(TEXT);
    expect(gesendet.get('datei')).toBeNull();
    expect(hinzugefuegt).toHaveBeenCalledTimes(1);
    // Leer heisst leer: kein Text, kein Anhang, und der Zeitpunkt steht wieder auf „jetzt".
    expect(textFeld()).toHaveValue('');
    expect(artWahl()).toHaveValue('KOMMENTAR');
    expect([vorDemAbsenden, alsEingabe(new Date().toISOString())]).toContain(
      zeitpunktFeld().value,
    );
  });

  it('schickt den Anhang mit Datei und ohne Beschreibung hinaus', async () => {
    const nutzer = userEvent.setup();
    const hinzugefuegt = vi.fn();
    let gesendet = new FormData();
    fetchNachPfad({
      [WEG]: formularWeg((formular) => {
        gesendet = formular;
      }, leer(201)),
    });
    renderMaske(hinzugefuegt);

    await nutzer.selectOptions(artWahl(), 'ANHANG');
    await nutzer.upload(dateiFeld(), datei('anfrage.pdf', 20480));
    await nutzer.click(taste());

    expect(gesendet.get('art')).toBe('ANHANG');
    expect(gesendet.get('text')).toBeNull();
    expect(gesendet.get('datei')).toHaveProperty('name', 'anfrage.pdf');
    expect(hinzugefuegt).toHaveBeenCalledTimes(1);
    // Nach dem Hinzufuegen steht die Maske wieder beim Kommentar — ohne die vorige Datei.
    expect(artWahl()).toHaveValue('KOMMENTAR');
  });

  it('schickt die Beschreibung eines Anhangs mit, wenn eine dasteht', async () => {
    const nutzer = userEvent.setup();
    let gesendet = new FormData();
    fetchNachPfad({
      [WEG]: formularWeg((formular) => {
        gesendet = formular;
      }, leer(201)),
    });
    renderMaske(vi.fn());

    await nutzer.selectOptions(artWahl(), 'ANHANG');
    await nutzer.upload(dateiFeld(), datei('anfrage.pdf', 20480));
    await nutzer.type(textFeld(), 'Die Anfrage als PDF.');
    await nutzer.click(taste());

    expect(gesendet.get('text')).toBe('Die Anfrage als PDF.');
  });
});

describe('EintragMaske — Meldungen der Schnittstelle (Kriterium 26)', () => {
  it('schreibt die Feldmeldungen an das jeweilige Feld', async () => {
    const nutzer = userEvent.setup();
    const hinzugefuegt = vi.fn();
    fetchNachPfad({
      [WEG]: problem(400, 'Die Eingabe passt nicht.', {
        text: ['Der Text ist zu lang.'],
        datei: ['Der Dateiname ist unbrauchbar.'],
        geschehenAm: ['Der Zeitpunkt darf nicht in der Zukunft liegen.'],
      }),
    });
    renderMaske(hinzugefuegt);

    await nutzer.selectOptions(artWahl(), 'ANHANG');
    await nutzer.upload(dateiFeld(), datei('anfrage.pdf', 20480));
    await nutzer.type(textFeld(), TEXT);
    await nutzer.click(taste());

    expect(await screen.findByText('Der Text ist zu lang.')).toBeInTheDocument();
    expect(textFeld()).toHaveAccessibleDescription('Der Text ist zu lang.');
    expect(dateiFeld()).toHaveAccessibleDescription('Der Dateiname ist unbrauchbar.');
    expect(zeitpunktFeld()).toHaveAccessibleDescription(
      'Der Zeitpunkt darf nicht in der Zukunft liegen.',
    );
    // Die Eingaben bleiben stehen: Der Benutzer soll bessern, nicht neu tippen.
    expect(textFeld()).toHaveValue(TEXT);
    expect(hinzugefuegt).not.toHaveBeenCalled();
  });

  it('meldet einen Ausfall ohne Feldmeldungen als Ganzes', async () => {
    const nutzer = userEvent.setup();
    const hinzugefuegt = vi.fn();
    fetchNachPfad({ [WEG]: leer(500) });
    renderMaske(hinzugefuegt);

    await nutzer.type(textFeld(), TEXT);
    await nutzer.click(taste());

    expect(await screen.findByRole('alert')).toHaveTextContent('nicht gespeichert');
    expect(hinzugefuegt).not.toHaveBeenCalled();
  });
});
