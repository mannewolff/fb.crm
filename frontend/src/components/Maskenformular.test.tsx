import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { FormEvent } from 'react';
import { describe, expect, it, vi } from 'vitest';

import { renderMitTheme } from '../test/render';
import Maskenformular from './Maskenformular';

const GRUNDLAGE = {
  fehler: null,
  abweisung: null,
  onNeuLaden: null,
  uebersicht: '/firmen',
  eigene: null,
  onVerwerfen: vi.fn(),
} as const;

describe('Maskenformular', () => {
  it('zeigt die Meldungen vor den Eingabefeldern', () => {
    renderMitTheme(
      <Maskenformular {...GRUNDLAGE} fehler="Speichern fehlgeschlagen." onSubmit={vi.fn()}>
        <label>
          Name
          <input />
        </label>
      </Maskenformular>,
    );

    const meldung = screen.getByRole('alert');
    const feld = screen.getByRole('textbox', { name: 'Name' });
    expect(meldung).toHaveTextContent('Speichern fehlgeschlagen.');
    expect(meldung.compareDocumentPosition(feld) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('zeigt die Bestaetigung des Speicherns', () => {
    renderMitTheme(
      <Maskenformular {...GRUNDLAGE} erfolg="Gespeichert." onSubmit={vi.fn()}>
        {null}
      </Maskenformular>,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('Gespeichert.');
  });

  it('gibt das Absenden ohne Pruefung des Browsers weiter', async () => {
    const absenden = vi.fn((ereignis: FormEvent<HTMLFormElement>) => {
      ereignis.preventDefault();
    });
    renderMitTheme(
      <Maskenformular {...GRUNDLAGE} onSubmit={absenden}>
        <input required aria-label="Pflicht" />
        <button type="submit">Speichern</button>
      </Maskenformular>,
    );

    // Das leere Pflichtfeld haelt das Absenden nicht auf: Die Maske prueft selbst.
    await userEvent.click(screen.getByRole('button', { name: 'Speichern' }));
    expect(absenden).toHaveBeenCalledTimes(1);
  });
});
