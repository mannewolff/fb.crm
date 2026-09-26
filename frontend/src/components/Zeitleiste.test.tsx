import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { renderMitTheme } from '../test/render';
import Zeitleiste from './Zeitleiste';
import type { ZeitleisteEintrag } from './Zeitleiste';

const EINTRAEGE: readonly ZeitleisteEintrag[] = [
  {
    id: 'k-1',
    symbol: <span>✉</span>,
    toenung: 'pfirsich',
    titel: 'Kommentar von Manne',
    unterzeile: 'Heute, 09:12',
  },
  {
    id: 'a-2',
    symbol: <span>📎</span>,
    toenung: 'flieder',
    titel: 'Anhang angebot.pdf',
    unterzeile: 'Gestern, 16:40 · Manne',
  },
  {
    id: 'k-3',
    symbol: <span>✉</span>,
    toenung: 'pfirsich',
    titel: 'Kommentar von Gabi Rosenbaum',
    inhalt: (
      <button type="button" data-testid="probe-inhalt">
        Ändern
      </button>
    ),
  },
];

describe('Zeitleiste', () => {
  it('ist eine Liste in der uebergebenen Reihenfolge', () => {
    renderMitTheme(<Zeitleiste eintraege={EINTRAEGE} />);

    // Eine echte Liste: Der Screenreader sagt damit an, wie viele Ereignisse es sind und an
    // welchem man gerade ist (Vorlage `.zeit` Z. 94–99).
    expect(screen.getByRole('list')).toBeInTheDocument();
    const eintraege = screen.getAllByRole('listitem');
    expect(eintraege).toHaveLength(3);
    expect(eintraege[0]).toHaveTextContent('Kommentar von Manne');
    expect(eintraege[1]).toHaveTextContent('Anhang angebot.pdf');
    expect(eintraege[2]).toHaveTextContent('Kommentar von Gabi Rosenbaum');
  });

  it('zeigt Titel und Unterzeile als Text', () => {
    renderMitTheme(<Zeitleiste eintraege={EINTRAEGE} />);

    // Die Art des Ereignisses steht im Wort, nicht nur in der Toenung
    // (CLAUDE-design.md, „Zustandsformen").
    expect(screen.getByText('Anhang angebot.pdf')).toBeInTheDocument();
    expect(screen.getByText('Gestern, 16:40 · Manne')).toBeInTheDocument();
  });

  it('haelt die Symbolfelder aus dem Vorgelesenen heraus', () => {
    renderMitTheme(<Zeitleiste eintraege={EINTRAEGE} />);

    const felder = screen.getAllByTestId('zeitleiste-symbol');
    expect(felder).toHaveLength(3);
    for (const feld of felder) {
      expect(feld).toHaveAttribute('aria-hidden', 'true');
    }
  });

  it('rendert den optionalen Inhalt im Eintrag, zu dem er gehoert', () => {
    renderMitTheme(<Zeitleiste eintraege={EINTRAEGE} />);

    const eintraege = screen.getAllByRole('listitem');
    expect(within(eintraege[2]).getByRole('button', { name: 'Ändern' })).toBeInTheDocument();
    expect(within(eintraege[0]).queryByTestId('probe-inhalt')).not.toBeInTheDocument();
    // Ein Eintrag ohne Unterzeile traegt keine leere Zeile.
    expect(within(eintraege[2]).queryByTestId('zeitleiste-unterzeile')).not.toBeInTheDocument();
  });
});
