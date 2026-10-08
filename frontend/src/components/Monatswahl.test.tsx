import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import Monatswahl, { OHNE_ARBEITSZEIT, monatOderKeiner, monatswahlWert } from './Monatswahl';
import { renderMitTheme } from '../test/render';

/** Eine kleine Hülle, die den Wert hält — die Wahl führt ihn nicht selbst. */
function Huelle({ gesperrt = false }: { readonly gesperrt?: boolean }) {
  const [monat, setzeMonat] = useState<string>(monatswahlWert());
  return <Monatswahl monat={monat} setzeMonat={setzeMonat} disabled={gesperrt} />;
}

function wahl(): HTMLSelectElement {
  return screen.getByRole('combobox', { name: 'Monat der Arbeitszeit' });
}

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.setSystemTime(new Date('2026-11-12T10:00:00Z'));
});

afterEach(() => {
  vi.useRealTimers();
});

describe('Monatswahl (Issue #203, Plan A17/E7)', () => {
  it('ist mit dem laufenden Monat vorbelegt', () => {
    renderMitTheme(<Huelle />);

    expect(wahl()).toHaveValue('2026-11');
    expect(within(wahl()).getByRole('option', { selected: true })).toHaveTextContent(
      'November 2026',
    );
  });

  it('bietet den laufenden Monat, die elf davor und „ohne Arbeitszeit"', () => {
    renderMitTheme(<Huelle />);

    const eintraege = within(wahl())
      .getAllByRole('option')
      .map((eintrag) => eintrag.textContent);
    expect(eintraege).toHaveLength(13);
    expect(eintraege[0]).toBe('November 2026');
    expect(eintraege[11]).toBe('Dezember 2025');
    expect(eintraege[12]).toBe('ohne Arbeitszeit');
  });

  it('gibt den gewaehlten Vormonat heraus', async () => {
    const nutzer = userEvent.setup();
    renderMitTheme(<Huelle />);

    await nutzer.selectOptions(wahl(), '2026-10');

    expect(wahl()).toHaveValue('2026-10');
  });

  it('traegt fuer „ohne Arbeitszeit" den leeren Wert', async () => {
    const nutzer = userEvent.setup();
    renderMitTheme(<Huelle />);

    await nutzer.selectOptions(wahl(), OHNE_ARBEITSZEIT);

    expect(wahl()).toHaveValue(OHNE_ARBEITSZEIT);
  });

  it('ist gesperrt, solange die Anfrage laeuft', () => {
    renderMitTheme(<Huelle gesperrt />);

    expect(wahl()).toBeDisabled();
  });
});

describe('monatOderKeiner (was an die Schnittstelle geht)', () => {
  it('gibt den Monat heraus, wo einer gewaehlt ist', () => {
    expect(monatOderKeiner('2026-10')).toBe('2026-10');
  });

  it('gibt `undefined` heraus fuer „ohne Arbeitszeit" — dann geht kein Rumpf hinaus', () => {
    expect(monatOderKeiner(OHNE_ARBEITSZEIT)).toBeUndefined();
  });
});
