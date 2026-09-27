import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { anhangPfad } from '../api/vorgaenge';
import type { Eintrag } from '../api/vorgaenge';
import { dateigroesse } from '../lib/dateigroesse';
import { fetchNachPfad, leer } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';
import Historie from './Historie';

/**
 * Die Zeitpunkte stehen relativ zur Zeitzone der Maschine, nicht als feste Zeichenkette mit `Z`:
 * Sonst waere gruen, wer in UTC laeuft, und rot, wer in Europa/Berlin sitzt — dieselbe Begruendung
 * wie in `lib/zeitpunkt.test.ts`.
 */
const GESCHEHEN = new Date(2026, 8, 24, 11, 15);
const GESCHEHEN_TEXT = '24.09.2026, 11:15';
const GEAENDERT = new Date(2026, 8, 25, 8, 5);
const GEAENDERT_TEXT = '25.09.2026, 08:05';

const VORGANG_ID = 7;

const KOMMENTAR: Eintrag = {
  id: 42,
  art: 'KOMMENTAR',
  text: 'Erste Zeile\nZweite Zeile',
  geschehenAm: GESCHEHEN.toISOString(),
  herkunft: 'VON_HAND',
  dateiName: null,
  dateiGroesse: null,
  geaendertAm: null,
};

const ANHANG: Eintrag = {
  id: 43,
  art: 'ANHANG',
  text: 'Das Angebot als PDF',
  geschehenAm: GESCHEHEN.toISOString(),
  herkunft: 'VON_HAND',
  dateiName: 'angebot.pdf',
  dateiGroesse: 43520,
  geaendertAm: null,
};

/**
 * Ein Ereignis: von der Anwendung vermerkt, nicht von Hand erfasst (Kriterium 19).
 *
 * Die Vorlage zeigt genau diese Zeile (`docs/entwurf-kupferwolke.html` Z. 192).
 */
const EREIGNIS: Eintrag = {
  id: 45,
  art: 'EREIGNIS',
  text: 'Angebot A-2026-009 versendet',
  geschehenAm: GESCHEHEN.toISOString(),
  herkunft: 'AUTOMATISCH',
  dateiName: null,
  dateiGroesse: null,
  geaendertAm: null,
};

const GEAENDERTER_KOMMENTAR: Eintrag = {
  ...KOMMENTAR,
  id: 44,
  text: 'Nachgetragen',
  geaendertAm: GEAENDERT.toISOString(),
};

function renderHistorie(eintraege: readonly Eintrag[], geaendert: () => void = vi.fn()) {
  return renderMitTheme(
    <Historie vorgangId={VORGANG_ID} eintraege={eintraege} geaendert={geaendert} />,
  );
}

/** Die Taste, die die Maske in der Zeile aufgehen laesst — ihr Name nennt den Eintrag. */
function aendernTaste(zeile: HTMLElement) {
  return within(zeile).getByRole('button', { name: /^Ändern/u });
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('Historie', () => {
  it('laesst die Reihenfolge der uebergebenen Eintraege unangetastet', () => {
    // Der juengste oben ist die Zusage der Schnittstelle (Kriterium 15); die Oberflaeche
    // sortiert nicht nach, sonst gaebe es zwei Reihenfolgen.
    renderHistorie([GEAENDERTER_KOMMENTAR, ANHANG, KOMMENTAR]);

    const zeilen = screen.getAllByRole('listitem');
    expect(zeilen).toHaveLength(3);
    expect(within(zeilen[0]).getByText('Nachgetragen')).toBeInTheDocument();
    expect(within(zeilen[1]).getByText('Das Angebot als PDF')).toBeInTheDocument();
    expect(within(zeilen[2]).getByText(/Erste Zeile/u)).toBeInTheDocument();
  });

  it('nennt je Eintrag Art, Zeitpunkt und Herkunft', () => {
    renderHistorie([KOMMENTAR]);

    const zeile = screen.getByRole('listitem');
    expect(within(zeile).getByText('Kommentar')).toBeInTheDocument();
    expect(within(zeile).getByText(GESCHEHEN_TEXT)).toBeInTheDocument();
    expect(within(zeile).getByText('von Hand')).toBeInTheDocument();
  });

  it('befuellt die Zeitleiste — je Eintrag ein Symbolfeld ausserhalb des Vorgelesenen', () => {
    // Der Baustein aus #79, nicht eine eigene Liste mit Linien: Die Art steht als Wort im
    // Titel, die Toenung und das Symbol stuetzen sie nur (CLAUDE-design.md, „Zustandsformen").
    renderHistorie([ANHANG, KOMMENTAR]);

    const felder = screen.getAllByTestId('zeitleiste-symbol');
    expect(felder).toHaveLength(2);
    for (const feld of felder) {
      expect(feld).toHaveAttribute('aria-hidden', 'true');
    }
  });

  it('nennt beim Anhang die Art Anhang', () => {
    renderHistorie([ANHANG]);

    expect(screen.getByText('Anhang')).toBeInTheDocument();
  });

  it('haelt die Zeilenumbrueche des Textes', () => {
    renderHistorie([KOMMENTAR]);

    // Ohne Normalisierung: Der Standardmatcher von Testing Library faltet Leerraum zusammen
    // und faende den Text auch dann, wenn der Umbruch verloren waere.
    const text = screen.getByText('Erste Zeile\nZweite Zeile', { normalizer: (wert) => wert });
    expect(text).toBeInTheDocument();
    // Der Umbruch im Markup traegt nur, wenn ihn das Rendern nicht wegwirft.
    expect(text).toHaveStyle({ whiteSpace: 'pre-wrap' });
  });

  it('gibt den Anhang als Verweis mit Dateiname, Groesse und download heraus', () => {
    renderHistorie([ANHANG]);

    const verweis = screen.getByRole('link', { name: 'angebot.pdf' });
    // Ein Pfad im `href`, kein `fetch` (E14): Der Browser holt die Datei selbst.
    expect(verweis).toHaveAttribute('href', anhangPfad(VORGANG_ID, ANHANG.id));
    expect(verweis).toHaveAttribute('download');
    expect(screen.getByText(dateigroesse(43520))).toBeInTheDocument();
  });

  it('kommt beim Anhang ohne Beschreibung ohne Textzeile aus', () => {
    // `Eintrag.anhang` im Backend laesst den beschreibenden Text offen; dann bleibt die Zeile
    // bei Dateiname und Groesse, statt eine leere Zeile darunter zu setzen.
    renderHistorie([{ ...ANHANG, text: null }]);

    const zeile = screen.getByRole('listitem');
    expect(within(zeile).getByRole('link', { name: 'angebot.pdf' })).toBeInTheDocument();
    expect(within(zeile).queryByText('Das Angebot als PDF')).not.toBeInTheDocument();
  });

  it('zeigt keinen Verweis und keine Groesse beim Kommentar', () => {
    renderHistorie([KOMMENTAR]);

    expect(screen.queryByRole('link')).not.toBeInTheDocument();
    expect(screen.queryByText(/KiB|MiB|\bB\b/u)).not.toBeInTheDocument();
  });

  it('vermerkt eine Aenderung samt Zeitpunkt', () => {
    renderHistorie([GEAENDERTER_KOMMENTAR]);

    expect(screen.getByText(`geändert ${GEAENDERT_TEXT}`)).toBeInTheDocument();
  });

  it('vermerkt nichts, solange keine Aenderung stattfand', () => {
    renderHistorie([KOMMENTAR]);

    expect(screen.queryByText(/geändert/u)).not.toBeInTheDocument();
  });

  it('sagt es, wenn die Historie leer ist', () => {
    renderHistorie([]);

    expect(screen.getByRole('status')).toHaveTextContent('Noch kein Eintrag in der Historie.');
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument();
  });

  it('traegt Art, Zeitpunkt, Herkunft und Vermerk im zugaenglichen Namen jedes Eintrags', () => {
    // Kriterium 26: Wer mit dem Screenreader durch die Eintraege geht, hoert an jedem, was er
    // ist, wann er geschah, woher er stammt und ob er geaendert wurde — ohne ihn zu betreten.
    renderHistorie([GEAENDERTER_KOMMENTAR, KOMMENTAR]);

    expect(
      screen.getByRole('listitem', {
        name: `Kommentar, ${GESCHEHEN_TEXT}, von Hand, geändert ${GEAENDERT_TEXT}`,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('listitem', { name: `Kommentar, ${GESCHEHEN_TEXT}, von Hand` }),
    ).toBeInTheDocument();
  });

  it('benennt die Liste als Historie', () => {
    renderHistorie([KOMMENTAR]);

    expect(screen.getByRole('list', { name: 'Historie' })).toBeInTheDocument();
  });
});

describe('Historie — Aendern an Ort und Stelle (E20, Kriterien 19, 26)', () => {
  it('stellt je Eintrag eine Taste „Aendern" mit dem Eintrag im Namen', () => {
    renderHistorie([ANHANG, KOMMENTAR]);

    const zeilen = screen.getAllByRole('listitem');
    // Der Name unterscheidet die Tasten: „Aendern" allein waere in jeder Zeile derselbe.
    expect(aendernTaste(zeilen[0])).toHaveAccessibleName(
      `Ändern: Anhang, ${GESCHEHEN_TEXT}, von Hand`,
    );
    expect(aendernTaste(zeilen[1])).toBeInTheDocument();
  });

  it('oeffnet die Maske in der Zeile des Eintrags', async () => {
    const nutzer = userEvent.setup();
    renderHistorie([ANHANG, KOMMENTAR]);

    await nutzer.click(aendernTaste(screen.getAllByRole('listitem')[1]));

    const zeile = screen.getAllByRole('listitem')[1];
    expect(within(zeile).getByRole('textbox', { name: /^Text/u })).toHaveValue(
      'Erste Zeile\nZweite Zeile',
    );
    expect(within(zeile).getByRole('button', { name: 'Speichern' })).toBeInTheDocument();
    // Die Zeile selbst traegt nun die Maske statt der Taste.
    expect(within(zeile).queryByRole('button', { name: /^Ändern/u })).not.toBeInTheDocument();
  });

  it('haelt hoechstens eine Zeile gleichzeitig im Aendern', async () => {
    const nutzer = userEvent.setup();
    renderHistorie([ANHANG, KOMMENTAR]);

    await nutzer.click(aendernTaste(screen.getAllByRole('listitem')[1]));
    await nutzer.click(aendernTaste(screen.getAllByRole('listitem')[0]));

    expect(screen.getAllByRole('button', { name: 'Speichern' })).toHaveLength(1);
    const zeilen = screen.getAllByRole('listitem');
    expect(within(zeilen[0]).getByRole('button', { name: 'Speichern' })).toBeInTheDocument();
    expect(aendernTaste(zeilen[1])).toBeInTheDocument();
  });

  it('springt mit dem Fokus in die Maske und nach „Abbrechen" zurueck auf die Taste', async () => {
    const nutzer = userEvent.setup();
    renderHistorie([KOMMENTAR]);

    await nutzer.click(aendernTaste(screen.getByRole('listitem')));

    expect(screen.getByRole('textbox', { name: /^Text/u })).toHaveFocus();

    await nutzer.click(screen.getByRole('button', { name: 'Abbrechen' }));

    // Kriterium 26: Wer mit der Tastatur arbeitet, steht danach wieder dort, wo er losging.
    expect(aendernTaste(screen.getByRole('listitem'))).toHaveFocus();
  });

  it('schliesst die Zeile nach dem Speichern und fordert zum Neuladen auf', async () => {
    const nutzer = userEvent.setup();
    const geaendert = vi.fn();
    fetchNachPfad({ [`PUT /api/vorgaenge/${String(VORGANG_ID)}/eintraege/42`]: leer(204) });
    renderHistorie([KOMMENTAR], geaendert);

    await nutzer.click(aendernTaste(screen.getByRole('listitem')));
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }));

    expect(geaendert).toHaveBeenCalledTimes(1);
    // Wie die Liste danach aussieht, sagt der Server — die Zeile geht wieder zu.
    expect(await screen.findByRole('button', { name: /^Ändern/u })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Speichern' })).not.toBeInTheDocument();
  });
});

describe('Historie mit Ereignissen', () => {
  it('nennt das Ereignis beim Wort — nicht nur an der Toenung (Kriterium 19)', () => {
    renderHistorie([EREIGNIS]);

    const zeile = screen.getByRole('listitem');
    expect(within(zeile).getByText('Ereignis')).toBeInTheDocument();
    expect(within(zeile).getByText('Angebot A-2026-009 versendet')).toBeInTheDocument();
  });

  it('nennt „automatisch" als Herkunft', () => {
    renderHistorie([EREIGNIS]);

    const zeile = screen.getByRole('listitem');
    expect(within(zeile).getByText('automatisch')).toBeInTheDocument();
    expect(zeile).toHaveAccessibleName('Ereignis, 24.09.2026, 11:15, automatisch');
  });

  it('traegt keine Taste „Ändern" — ein Ereignis ist ein Nachweis', () => {
    renderHistorie([EREIGNIS]);

    const zeile = screen.getByRole('listitem');
    expect(within(zeile).queryByRole('button', { name: /^Ändern/u })).not.toBeInTheDocument();
  });

  it('laesst Kommentar und Anhang daneben unveraendert aenderbar', () => {
    renderHistorie([EREIGNIS, ANHANG, KOMMENTAR]);

    const zeilen = screen.getAllByRole('listitem');
    expect(within(zeilen[0]).queryByRole('button', { name: /^Ändern/u })).not.toBeInTheDocument();
    expect(aendernTaste(zeilen[1])).toBeInTheDocument();
    expect(aendernTaste(zeilen[2])).toBeInTheDocument();
  });
});
