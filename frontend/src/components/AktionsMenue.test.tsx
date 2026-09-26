import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { renderMitTheme } from '../test/render';
import AktionsMenue from './AktionsMenue';

const NAME = 'Aktionen für Gabi Rosenbaum';
const FRAGE = 'Stillgelegte Ansprechpartner stehen nicht mehr zur Auswahl.';

/**
 * Ein Menue mit beiden Arten von Eintraegen: einem harmlosen und einem folgenreichen.
 *
 * `userEvent.setup()` laeuft vor dem Rendern — so haengen die Zeiger-Ereignisse an dem Dokument,
 * in dem die Bausteine dann entstehen.
 */
function aufbau() {
  const nutzer = userEvent.setup();
  const bearbeiten = vi.fn();
  const stilllegen = vi.fn();
  renderMitTheme(
    <AktionsMenue
      name={NAME}
      objekt="Gabi Rosenbaum"
      eintraege={[
        { titel: 'Bearbeiten', onAuswahl: bearbeiten, symbol: <span>✎</span> },
        { titel: 'Stilllegen', onAuswahl: stilllegen, rueckfrage: FRAGE },
      ]}
    />,
  );
  return { nutzer, bearbeiten, stilllegen, taste: screen.getByRole('button', { name: NAME }) };
}

describe('AktionsMenue', () => {
  it('traegt den uebergebenen Namen und meldet ein Menue', () => {
    const { taste } = aufbau();

    expect(taste).toHaveAttribute('aria-haspopup', 'menu');
    expect(taste).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });

  it('fuehrt einen Eintrag ohne Rueckfrage sofort und genau einmal aus', async () => {
    const { nutzer, taste, bearbeiten, stilllegen } = aufbau();

    await nutzer.click(taste);
    expect(taste).toHaveAttribute('aria-expanded', 'true');
    await nutzer.click(screen.getByRole('menuitem', { name: 'Bearbeiten' }));

    expect(bearbeiten).toHaveBeenCalledTimes(1);
    expect(stilllegen).not.toHaveBeenCalled();
    // Kein Zwischenschritt: Nur folgenreiche Aktionen fragen nach
    // (CLAUDE-design.md, „Tasten").
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('nennt in der Rueckfrage die Aktion und das Objekt und fuehrt noch nichts aus', async () => {
    const { nutzer, taste, stilllegen } = aufbau();

    await nutzer.click(taste);
    await nutzer.click(screen.getByRole('menuitem', { name: 'Stilllegen' }));

    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveAccessibleName('Stilllegen: Gabi Rosenbaum');
    expect(within(dialog).getByText(FRAGE)).toBeInTheDocument();
    expect(stilllegen).not.toHaveBeenCalled();
  });

  it('fuehrt nach „Abbrechen" nichts aus und gibt den Fokus an die ⋯-Taste zurueck', async () => {
    const { nutzer, taste, stilllegen } = aufbau();

    await nutzer.click(taste);
    await nutzer.click(screen.getByRole('menuitem', { name: 'Stilllegen' }));
    await nutzer.click(await screen.findByRole('button', { name: 'Abbrechen' }));

    expect(stilllegen).not.toHaveBeenCalled();
    await vi.waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
    expect(taste).toHaveFocus();
  });

  it('fuehrt nach dem Bestaetigen genau einmal aus und gibt den Fokus zurueck', async () => {
    const { nutzer, taste, stilllegen } = aufbau();

    await nutzer.click(taste);
    await nutzer.click(screen.getByRole('menuitem', { name: 'Stilllegen' }));
    const dialog = await screen.findByRole('dialog');
    await nutzer.click(within(dialog).getByRole('button', { name: 'Stilllegen' }));

    expect(stilllegen).toHaveBeenCalledTimes(1);
    await vi.waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
    expect(taste).toHaveFocus();
  });

  it('schliesst die Rueckfrage mit Escape, ohne auszufuehren', async () => {
    const { nutzer, taste, stilllegen } = aufbau();

    await nutzer.click(taste);
    await nutzer.click(screen.getByRole('menuitem', { name: 'Stilllegen' }));
    await screen.findByRole('dialog');
    await nutzer.keyboard('{Escape}');

    expect(stilllegen).not.toHaveBeenCalled();
    await vi.waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
    expect(taste).toHaveFocus();
  });

  it('schliesst das Menue mit Escape, ohne auszufuehren', async () => {
    const { nutzer, taste, bearbeiten, stilllegen } = aufbau();

    await nutzer.click(taste);
    await nutzer.keyboard('{Escape}');

    await vi.waitFor(() => {
      expect(screen.queryByRole('menu')).not.toBeInTheDocument();
    });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(bearbeiten).not.toHaveBeenCalled();
    expect(stilllegen).not.toHaveBeenCalled();
    expect(taste).toHaveFocus();
  });
});
