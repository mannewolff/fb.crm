import { screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import Angebotsliste from './Angebotsliste';
import type { AngebotZeile } from '../api/angebote';
import { renderMitTheme } from '../test/render';
import { ZAHLEN_KLASSE } from '../theme';

const JUENGER: AngebotZeile = {
  id: 12,
  angebotDatum: '2026-09-26',
  titel: 'Schulung Java',
  status: 'ANGELEGT',
  intern: false,
  summeInCent: 120000,
};

const AELTER: AngebotZeile = {
  id: 9,
  angebotDatum: '2026-09-24',
  titel: 'Neue Website',
  status: 'BESTELLT',
  intern: false,
  summeInCent: 250003,
};

const INTERN: AngebotZeile = {
  id: 14,
  angebotDatum: '2026-09-28',
  titel: 'Eigene Werkzeuge',
  status: 'LAEUFT',
  intern: true,
  summeInCent: 0,
};

/** Ein Angebot aus der Zeit vor dem Titel (Issue #316): Der Bestand traegt keinen. */
const OHNE_TITEL: AngebotZeile = {
  id: 7,
  angebotDatum: '2026-09-20',
  titel: null,
  status: 'ANGELEGT',
  intern: false,
  summeInCent: 50000,
};

/** Die Zellen einer Datenzeile — Zeile 0 ist der Kopf. */
function zellen(zeile: number): readonly HTMLElement[] {
  return within(screen.getAllByRole('row')[zeile]).getAllByRole('cell');
}

function renderListe(angebote: readonly AngebotZeile[]) {
  return renderMitTheme(
    <MemoryRouter>
      <Angebotsliste angebote={angebote} />
    </MemoryRouter>,
  );
}

describe('Angebotsliste (Kriterium 7)', () => {
  it('traegt Titel, Datum, Summe und Status je Zeile', () => {
    renderListe([AELTER]);

    const zeile = within(screen.getAllByRole('row')[1]);
    expect(zeile.getByRole('link', { name: 'Neue Website' })).toBeInTheDocument();
    expect(zeile.getByText('24.09.2026')).toBeInTheDocument();
    expect(zeile.getByText('Bestellt')).toBeInTheDocument();
    expect(zeile.getByText('2.500,03 €')).toBeInTheDocument();
  });

  it('gibt die Reihenfolge der Antwort wieder — neueste zuerst', () => {
    // Sortiert wird am Server; die Liste ordnet nicht um.
    renderListe([JUENGER, AELTER]);

    expect(screen.getAllByRole('link').map((weg) => weg.textContent)).toEqual([
      'Schulung Java',
      'Neue Website',
    ]);
  });

  it('oeffnet aus jeder Zeile das Angebot', () => {
    renderListe([JUENGER, AELTER]);

    expect(screen.getByRole('link', { name: 'Schulung Java' })).toHaveAttribute(
      'href',
      '/angebote/12',
    );
    expect(screen.getByRole('link', { name: 'Neue Website' })).toHaveAttribute(
      'href',
      '/angebote/9',
    );
  });

  it('nennt die Spalten der Tafel', () => {
    renderListe([AELTER]);

    expect(screen.getAllByRole('columnheader').map((kopf) => kopf.textContent)).toEqual([
      'Titel',
      'Datum',
      'Summe',
      'Status',
    ]);
  });

  it('stellt den Kopf einer Zahlenspalte rechtsbuendig und die uebrigen links (Issue #286)', () => {
    renderListe([AELTER]);

    expect(screen.getByRole('columnheader', { name: 'Summe' })).toHaveStyle({ textAlign: 'right' });
    for (const name of ['Titel', 'Datum', 'Status']) {
      expect(screen.getByRole('columnheader', { name })).toHaveStyle({ textAlign: 'left' });
    }
  });

  it('sagt es, wenn die Firma noch kein Angebot hat, statt eine leere Tafel zu zeigen', () => {
    renderListe([]);

    expect(screen.getByRole('status')).toHaveTextContent('Noch kein Angebot');
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});

describe('Angebotsliste — Kennzeichen intern und Strich in der Summe (Issue #233)', () => {
  it('zeigt am internen Angebot den Chip „Intern" und in der Spalte „Summe" den Strich', () => {
    renderListe([INTERN]);

    const zeile = within(screen.getAllByRole('row')[1]);
    expect(zeile.getByText('Intern')).toBeInTheDocument();
    expect(zeile.getByText('\u2013')).toBeInTheDocument();
    expect(zeile.queryByText(/€/)).not.toBeInTheDocument();
  });

  it('zeigt am externen Angebot keinen Chip „Intern", sondern seinen Betrag', () => {
    renderListe([AELTER]);

    const zeile = within(screen.getAllByRole('row')[1]);
    expect(zeile.queryByText('Intern')).not.toBeInTheDocument();
    expect(zeile.getByText('2.500,03 €')).toBeInTheDocument();
  });

  it('zeigt den Strich auch bei gespeicherter Summe ueber 0 (E16)', () => {
    // Menge und Preis bleiben beim Wechsel extern -> intern erhalten; der Strich haengt am
    // Kennzeichen, nicht an einer 0 vom Server.
    renderListe([{ ...INTERN, summeInCent: 450000 }]);

    const zeile = within(screen.getAllByRole('row')[1]);
    expect(zeile.getByText('\u2013')).toBeInTheDocument();
    expect(zeile.queryByText('4.500,00 €')).not.toBeInTheDocument();
  });
});

describe('Angebotsliste — der Titel (Issue #319, Kriterien 4, 6, 7)', () => {
  it('Szenario: Zwei Angebote vom selben Tag unterscheiden — Spalten Titel, Datum, Summe, Status, und der Titel fuehrt zum Angebot', () => {
    const java: AngebotZeile = {
      id: 21,
      angebotDatum: '2026-10-15',
      titel: 'Java-Grundkurs',
      status: 'ANGELEGT',
      intern: false,
      summeInCent: 300000,
    };
    const spring: AngebotZeile = { ...java, id: 22, titel: 'Spring-Aufbaukurs', summeInCent: 400000 };

    renderListe([java, spring]);

    expect(screen.getAllByRole('columnheader').map((kopf) => kopf.textContent)).toEqual([
      'Titel',
      'Datum',
      'Summe',
      'Status',
    ]);
    expect(zellen(1)[0]).toHaveTextContent(/^Java-Grundkurs$/);
    expect(zellen(2)[0]).toHaveTextContent(/^Spring-Aufbaukurs$/);
    expect(screen.getByRole('link', { name: 'Java-Grundkurs' })).toHaveAttribute(
      'href',
      '/angebote/21',
    );
  });

  it('Szenario: Bestehendes Angebot ohne Titel anzeigen — derselbe Strich wie in der Spalte Summe', () => {
    renderListe([OHNE_TITEL, INTERN]);

    const [titel, datum] = zellen(1);
    expect(titel).toHaveTextContent(/^\u2013$/);
    expect(within(titel).queryByRole('link')).not.toBeInTheDocument();
    expect(within(datum).getByRole('link', { name: '20.09.2026' })).toHaveAttribute(
      'href',
      '/angebote/7',
    );
    // Beide Zellen lesen dieselbe Konstante: Das Zeichen der Titelzelle ist das der Summenzelle
    // eines internen Angebots.
    expect(titel.textContent).toBe(zellen(2)[2].textContent);
  });

  it('zeigt einen Titel mit 200 Zeichen ohne Leerzeichen vollstaendig und kuerzt ihn nicht (E11)', () => {
    const lang = 'x'.repeat(200);

    renderListe([{ ...JUENGER, titel: lang }]);

    const titel = zellen(1)[0];
    expect(screen.getByRole('link', { name: lang })).toBeInTheDocument();
    expect(titel).toHaveStyle({ overflowWrap: 'anywhere' });
    expect(titel).not.toHaveStyle({ whiteSpace: 'nowrap' });
    expect(getComputedStyle(titel).textOverflow).toBe('');
  });

  it('laesst den Chip „Intern" in der Datumszelle und die Tabellenziffern am Datum (E18)', () => {
    renderListe([INTERN, OHNE_TITEL]);

    const mitTitel = zellen(1)[1];
    expect(within(mitTitel).getByText('Intern')).toBeInTheDocument();
    expect(within(mitTitel).queryByRole('link')).not.toBeInTheDocument();
    expect(within(mitTitel).getByText('28.09.2026')).toHaveClass(ZAHLEN_KLASSE);

    const ohneTitel = zellen(2)[1];
    expect(within(ohneTitel).getByRole('link', { name: '20.09.2026' })).toHaveClass(ZAHLEN_KLASSE);
  });
});
