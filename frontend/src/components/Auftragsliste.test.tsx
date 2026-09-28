import { screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import Auftragsliste from './Auftragsliste';
import type { AuftragZeile } from '../api/auftraege';
import { renderMitTheme } from '../test/render';

const JUENGER: AuftragZeile = {
  id: 4,
  nummer: 'AU-2026-002',
  status: 'IN_ARBEIT',
  auftragDatum: '2026-09-28',
  leistungAb: '2026-10-01',
  leistungBis: '2026-12-31',
  summeInCent: 300003,
};

const AELTER: AuftragZeile = {
  id: 3,
  nummer: 'AU-2026-001',
  status: 'ABGESCHLOSSEN',
  auftragDatum: '2026-09-20',
  leistungAb: null,
  leistungBis: null,
  summeInCent: 50000,
};

function renderListe(auftraege: readonly AuftragZeile[]) {
  return renderMitTheme(
    <MemoryRouter>
      <Auftragsliste vorgangId={5} auftraege={auftraege} />
    </MemoryRouter>,
  );
}

describe('Auftragsliste (Kriterium 9)', () => {
  it('zeigt je Zeile Nummer, Datum, Status, Leistungszeitraum und Summe', () => {
    renderListe([JUENGER]);

    expect(screen.getAllByRole('columnheader').map((kopf) => kopf.textContent)).toEqual([
      'Nummer',
      'Datum',
      'Status',
      'Leistungszeitraum',
      'Summe',
    ]);
    const zeile = within(screen.getAllByRole('row')[1]);
    expect(zeile.getByText('AU-2026-002')).toBeInTheDocument();
    expect(zeile.getByText('28.09.2026')).toBeInTheDocument();
    expect(zeile.getByText('In Arbeit')).toBeInTheDocument();
    expect(zeile.getByText('01.10.2026 – 31.12.2026')).toBeInTheDocument();
    expect(zeile.getByText('3.000,03 €')).toBeInTheDocument();
  });

  it('uebernimmt die Reihenfolge der Antwort und sortiert nicht nach', () => {
    renderListe([AELTER, JUENGER]);

    expect(screen.getAllByRole('link').map((weg) => weg.textContent)).toEqual([
      'AU-2026-001',
      'AU-2026-002',
    ]);
  });

  it('legt den Weg auf die Nummer — ein Tastaturziel auf die Auftragsansicht', () => {
    renderListe([JUENGER]);

    const weg = screen.getByRole('link', { name: 'AU-2026-002' });
    expect(weg).toHaveAttribute('href', '/vorgaenge/5/auftraege/4');
    weg.focus();
    expect(weg).toHaveFocus();
  });

  it('macht die Zeile selbst nicht zu einem Weg', () => {
    renderListe([JUENGER]);

    const zeile = screen.getAllByRole('row')[1];
    expect(zeile).not.toHaveAttribute('tabindex');
    expect(zeile).not.toHaveAttribute('role', 'link');
    expect(within(zeile).getAllByRole('link')).toHaveLength(1);
  });

  it('zeigt fuer einen Auftrag ohne Leistungszeitraum ein Wort und kein leeres Feld', () => {
    renderListe([AELTER]);

    expect(within(screen.getAllByRole('row')[1]).getByText('nicht angegeben')).toBeInTheDocument();
  });

  it('stellt den Status als Wort dar', () => {
    renderListe([AELTER]);

    expect(screen.getByTestId('chip')).toHaveTextContent('Abgeschlossen');
  });

  it('sagt es mit einem Satz, wenn der Vorgang noch keinen Auftrag hat', () => {
    renderListe([]);

    expect(screen.getByRole('status')).toHaveTextContent('Noch kein Auftrag zu diesem Vorgang.');
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});
