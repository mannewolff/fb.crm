import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';

import Positionsmaske, { betragDerPosition, frischePosition } from './Positionsmaske';
import type { Maskenposition } from './Positionsmaske';
import { renderMitTheme } from '../test/render';

const POSITION: Maskenposition = {
  id: 3,
  schluessel: 'position-3',
  bezeichnung: 'Konzeption',
  abrechnungsmodus: 'AUFWAND',
  menge: '2,5',
  einheit: 'PERSONENTAG',
  einzelpreis: '1000,01',
  einheitVonHand: false,
};

/**
 * Ein Halter, der den Zustand der Position fuehrt.
 *
 * Die Maske ist gesteuert: Sie haelt nichts selbst. Ohne einen Halter blieben Eingaben ohne
 * Wirkung, und „der Betrag rechnet mit" liesse sich nicht pruefen.
 */
function Halter({
  start = POSITION,
  loesche = vi.fn(),
  nachOben = vi.fn(),
  nachUnten = vi.fn(),
  erste = false,
  letzte = false,
  bezeichnungFehler,
}: {
  readonly start?: Maskenposition;
  readonly loesche?: () => void;
  readonly nachOben?: () => void;
  readonly nachUnten?: () => void;
  readonly erste?: boolean;
  readonly letzte?: boolean;
  readonly bezeichnungFehler?: string;
}) {
  const [position, setzePosition] = useState(start);
  return (
    <Positionsmaske
      nummer={1}
      position={position}
      aendere={setzePosition}
      loesche={loesche}
      nachOben={nachOben}
      nachUnten={nachUnten}
      erste={erste}
      letzte={letzte}
      bezeichnungFehler={bezeichnungFehler}
    />
  );
}

/** Die Gruppe der Position — jedes Feld gehoert zu ihr, damit mehrere Zeilen unterscheidbar sind. */
function gruppe() {
  return within(screen.getByRole('group', { name: 'Position 1' }));
}

describe('Positionsmaske — die Felder (Kriterium 4)', () => {
  it('traegt Bezeichnung, Modus, Menge, Einheit und Einzelpreis mit Beschriftung', () => {
    renderMitTheme(<Halter />);

    expect(gruppe().getByRole('textbox', { name: 'Bezeichnung' })).toHaveValue('Konzeption');
    expect(gruppe().getByRole('combobox', { name: 'Abrechnung' })).toHaveValue('AUFWAND');
    expect(gruppe().getByRole('textbox', { name: 'Menge' })).toHaveValue('2,5');
    expect(gruppe().getByRole('combobox', { name: 'Einheit' })).toHaveValue('PERSONENTAG');
    expect(gruppe().getByRole('textbox', { name: 'Einzelpreis (netto)' })).toHaveValue('1000,01');
  });

  it('zeigt die Kennung nirgends und macht kein Feld daraus (Plan #169)', () => {
    renderMitTheme(<Halter />);

    // Fuenf Felder, nicht sechs: Die Kennung wird mitgefuehrt, nicht bedient.
    expect(gruppe().getAllByRole('textbox')).toHaveLength(3);
    expect(gruppe().getAllByRole('combobox')).toHaveLength(2);
    expect(gruppe().queryByText('3')).not.toBeInTheDocument();
  });

  it('nennt die Einheiten als Wort und nicht als Schluessel (F7)', () => {
    renderMitTheme(<Halter />);

    expect(
      within(gruppe().getByRole('combobox', { name: 'Einheit' }))
        .getAllByRole('option')
        .map((wahl) => wahl.textContent),
    ).toEqual(['Stunde', 'Personentag', 'Pauschal']);
    expect(
      within(gruppe().getByRole('combobox', { name: 'Abrechnung' }))
        .getAllByRole('option')
        .map((wahl) => wahl.textContent),
    ).toEqual(['Aufwand', 'Festpreis']);
  });
});

describe('Positionsmaske — die Kennung wandert mit (Plan #169)', () => {
  it('reicht Kennung und Schluessel bei jeder Aenderung unveraendert weiter', async () => {
    const nutzer = userEvent.setup();
    const aendere = vi.fn();
    renderMitTheme(
      <Positionsmaske
        nummer={1}
        position={POSITION}
        aendere={aendere}
        loesche={vi.fn()}
        nachOben={vi.fn()}
        nachUnten={vi.fn()}
        erste={false}
        letzte={false}
      />,
    );

    await nutzer.type(gruppe().getByRole('textbox', { name: 'Bezeichnung' }), 'X');
    await nutzer.selectOptions(gruppe().getByRole('combobox', { name: 'Abrechnung' }), 'FESTPREIS');
    await nutzer.selectOptions(gruppe().getByRole('combobox', { name: 'Einheit' }), 'STUNDE');

    for (const [neu] of aendere.mock.calls as [Maskenposition][]) {
      expect(neu.id).toBe(3);
      expect(neu.schluessel).toBe('position-3');
    }
    expect(aendere).toHaveBeenCalledTimes(3);
  });
});

describe('Positionsmaske — der Betrag rechnet mit (Kriterium 5)', () => {
  it('zeigt Menge mal Einzelpreis, kaufmaennisch auf Cent gerundet', () => {
    renderMitTheme(<Halter />);

    // 2,5 × 1.000,01 € ergibt 2.500,025 € und damit 2.500,03 € — nicht 2.500,02 € (E5).
    expect(gruppe().getByTestId('positions-betrag')).toHaveTextContent('2.500,03 €');
  });

  it('rechnet beim Tippen mit', async () => {
    const nutzer = userEvent.setup();
    renderMitTheme(<Halter />);

    const menge = gruppe().getByRole('textbox', { name: 'Menge' });
    await nutzer.clear(menge);
    await nutzer.type(menge, '3');

    expect(gruppe().getByTestId('positions-betrag')).toHaveTextContent('3.000,03 €');
  });

  it('meldet eine Menge, die keine Zahl ist, am Feld und zeigt keinen Betrag', async () => {
    const nutzer = userEvent.setup();
    renderMitTheme(<Halter />);

    const menge = gruppe().getByRole('textbox', { name: 'Menge' });
    await nutzer.clear(menge);
    await nutzer.type(menge, '2,555');

    expect(gruppe().getByText(/höchstens zwei Nachkommastellen/)).toBeInTheDocument();
    expect(gruppe().getByTestId('positions-betrag')).toHaveTextContent('—');
  });

  it('meldet einen Einzelpreis, der keine Zahl ist, am Feld', async () => {
    const nutzer = userEvent.setup();
    renderMitTheme(<Halter />);

    const preis = gruppe().getByRole('textbox', { name: 'Einzelpreis (netto)' });
    await nutzer.clear(preis);

    expect(gruppe().getByText(/höchstens zwei Nachkommastellen/)).toBeInTheDocument();
    expect(gruppe().getByTestId('positions-betrag')).toHaveTextContent('—');
  });
});

describe('Positionsmaske — die Einheit zum Modus (Kriterium 4, F7)', () => {
  it('belegt die Einheit mit „Pauschal", wenn auf Festpreis gewechselt wird', async () => {
    const nutzer = userEvent.setup();
    renderMitTheme(<Halter />);

    await nutzer.selectOptions(gruppe().getByRole('combobox', { name: 'Abrechnung' }), 'FESTPREIS');

    expect(gruppe().getByRole('combobox', { name: 'Einheit' })).toHaveValue('PAUSCHAL');
  });

  it('belegt die Einheit mit „Personentag", wenn auf Aufwand gewechselt wird', async () => {
    const nutzer = userEvent.setup();
    renderMitTheme(
      <Halter
        start={{
          ...POSITION,
          abrechnungsmodus: 'FESTPREIS',
          einheit: 'PAUSCHAL',
        }}
      />,
    );

    await nutzer.selectOptions(gruppe().getByRole('combobox', { name: 'Abrechnung' }), 'AUFWAND');

    expect(gruppe().getByRole('combobox', { name: 'Einheit' })).toHaveValue('PERSONENTAG');
  });

  it.each([
    ['PERSONENTAG', 'PAUSCHAL'],
    ['PAUSCHAL', 'PERSONENTAG'],
  ])('nimmt die Wahl von %s auf %s an und haelt sie fest', async (vorher, gewaehlt) => {
    const nutzer = userEvent.setup();
    renderMitTheme(
      <Halter start={{ ...POSITION, einheit: vorher === 'PAUSCHAL' ? 'PAUSCHAL' : 'PERSONENTAG' }} />,
    );

    await nutzer.selectOptions(gruppe().getByRole('combobox', { name: 'Einheit' }), gewaehlt);

    expect(gruppe().getByRole('combobox', { name: 'Einheit' })).toHaveValue(gewaehlt);
    // Danach gehoert sie dem Menschen: Ein Wechsel des Modus setzt sie nicht um (F7).
    await nutzer.selectOptions(gruppe().getByRole('combobox', { name: 'Abrechnung' }), 'FESTPREIS');
    expect(gruppe().getByRole('combobox', { name: 'Einheit' })).toHaveValue(gewaehlt);
  });

  it('ueberschreibt eine selbst gewaehlte Einheit nicht stillschweigend', async () => {
    const nutzer = userEvent.setup();
    renderMitTheme(<Halter />);

    // Wer „Stunde" gewaehlt hat, hat eine Entscheidung getroffen. Ein Wechsel des Modus ist ein
    // Vorschlag (E26) und darf sie nicht zuruecknehmen.
    await nutzer.selectOptions(gruppe().getByRole('combobox', { name: 'Einheit' }), 'STUNDE');
    await nutzer.selectOptions(gruppe().getByRole('combobox', { name: 'Abrechnung' }), 'FESTPREIS');

    expect(gruppe().getByRole('combobox', { name: 'Einheit' })).toHaveValue('STUNDE');
  });
});

describe('Positionsmaske — Reihenfolge und Loeschen (Kriterium 6)', () => {
  it('meldet Verschieben und Loeschen nach oben', async () => {
    const nutzer = userEvent.setup();
    const loesche = vi.fn();
    const nachOben = vi.fn();
    const nachUnten = vi.fn();
    renderMitTheme(<Halter loesche={loesche} nachOben={nachOben} nachUnten={nachUnten} />);

    await nutzer.click(gruppe().getByRole('button', { name: 'Position 1 nach oben' }));
    await nutzer.click(gruppe().getByRole('button', { name: 'Position 1 nach unten' }));
    await nutzer.click(gruppe().getByRole('button', { name: 'Position 1 löschen' }));

    expect(nachOben).toHaveBeenCalledTimes(1);
    expect(nachUnten).toHaveBeenCalledTimes(1);
    expect(loesche).toHaveBeenCalledTimes(1);
  });

  it('schaltet „nach oben" an der ersten und „nach unten" an der letzten Zeile ab', () => {
    renderMitTheme(<Halter erste letzte />);

    expect(gruppe().getByRole('button', { name: 'Position 1 nach oben' })).toBeDisabled();
    expect(gruppe().getByRole('button', { name: 'Position 1 nach unten' })).toBeDisabled();
    expect(gruppe().getByRole('button', { name: 'Position 1 löschen' })).toBeEnabled();
  });
});

describe('Positionsmaske — die Meldung an der Bezeichnung (Issue #127)', () => {
  it('zeigt die Meldung des Servers an der Bezeichnung', () => {
    renderMitTheme(<Halter bezeichnungFehler="Jede Position braucht eine Bezeichnung." />);

    const feld = gruppe().getByRole('textbox', { name: 'Bezeichnung' });
    expect(feld).toHaveAttribute('aria-invalid', 'true');
    expect(gruppe().getByText('Jede Position braucht eine Bezeichnung.')).toBeInTheDocument();
  });

  it('zeigt ohne Meldung kein Fehlerbild', () => {
    renderMitTheme(<Halter />);

    expect(gruppe().getByRole('textbox', { name: 'Bezeichnung' })).toHaveAttribute(
      'aria-invalid',
      'false',
    );
  });
});

describe('frischePosition und betragDerPosition', () => {
  it('startet eine neue Zeile ohne Text, mit Aufwand, Personentag und einem gueltigen Betrag', () => {
    expect(frischePosition('neu-1')).toEqual({
      // Eine frische Zeile hat noch keine Kennung — sie entsteht erst beim Speichern (E2).
      id: null,
      schluessel: 'neu-1',
      bezeichnung: '',
      abrechnungsmodus: 'AUFWAND',
      menge: '1',
      einheit: 'PERSONENTAG',
      einzelpreis: '0',
      einheitVonHand: false,
    });
    expect(betragDerPosition(frischePosition('neu-1'))).toBe(0);
  });

  it('gibt jeder frischen Zeile den uebergebenen Schluessel', () => {
    expect(frischePosition('neu-2').schluessel).toBe('neu-2');
  });

  it('gibt nichts heraus, wenn Menge oder Einzelpreis keine Zahl sind', () => {
    expect(betragDerPosition({ ...POSITION, menge: 'x' })).toBeNull();
    expect(betragDerPosition({ ...POSITION, einzelpreis: '' })).toBeNull();
    expect(betragDerPosition(POSITION)).toBe(250003);
  });
});
