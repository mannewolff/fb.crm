import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import type { Block } from '../lib/markdown';
import { renderMitTheme } from '../test/render';
import Dokumenttext from './Dokumenttext';

describe('Dokumenttext', () => {
  it('setzt eine Ueberschrift der Ebene 2 als h2 mit Anker und tabIndex -1 (E8)', () => {
    renderMitTheme(
      <Dokumenttext
        bloecke={[{ art: 'ueberschrift', ebene: 2, text: 'Sicherung', anker: 'sicherung' }]}
      />,
    );

    const ueberschrift = screen.getByRole('heading', { level: 2, name: 'Sicherung' });
    expect(ueberschrift.tagName).toBe('H2');
    expect(ueberschrift).toHaveAttribute('id', 'sicherung');
    expect(ueberschrift).toHaveAttribute('tabindex', '-1');
  });

  it('setzt eine Ueberschrift der Ebene 3 als h3 mit Anker und tabIndex -1 (E8)', () => {
    renderMitTheme(
      <Dokumenttext
        bloecke={[{ art: 'ueberschrift', ebene: 3, text: 'Datenbank', anker: 'datenbank' }]}
      />,
    );

    const ueberschrift = screen.getByRole('heading', { level: 3, name: 'Datenbank' });
    expect(ueberschrift.tagName).toBe('H3');
    expect(ueberschrift).toHaveAttribute('id', 'datenbank');
    expect(ueberschrift).toHaveAttribute('tabindex', '-1');
  });

  it('setzt Fliesstext als Absatz in der Lesebreite, mit allen Inline-Formen', () => {
    renderMitTheme(
      <Dokumenttext
        bloecke={[
          {
            art: 'text',
            inhalt: [
              { art: 'klartext', text: 'Erst ' },
              { art: 'betont', text: 'sichern' },
              { art: 'klartext', text: ', dann ' },
              { art: 'schnipsel', text: 'docker compose up' },
              { art: 'klartext', text: ', siehe ' },
              { art: 'verweis', text: 'Betrieb', ziel: '#betrieb' },
            ],
          },
        ]}
      />,
    );

    const absatz = screen.getByText(/Erst/u, { selector: 'p' });
    expect(absatz).toHaveTextContent('Erst sichern, dann docker compose up, siehe Betrieb');
    expect(within(absatz).getByText('sichern').tagName).toBe('STRONG');
    expect(within(absatz).getByText('docker compose up').tagName).toBe('CODE');
    expect(getComputedStyle(screen.getByTestId('dokumenttext')).maxWidth).toBe('70ch');
  });

  it('setzt einen Verweis als Link mit seinem Ziel', () => {
    renderMitTheme(
      <Dokumenttext
        bloecke={[
          {
            art: 'text',
            inhalt: [{ art: 'verweis', text: 'zur Sicherung', ziel: '#sicherung' }],
          },
        ]}
      />,
    );

    expect(screen.getByRole('link', { name: 'zur Sicherung' })).toHaveAttribute(
      'href',
      '#sicherung',
    );
  });

  it('setzt eine einfache Aufzaehlung als ul mit li je Eintrag', () => {
    renderMitTheme(
      <Dokumenttext
        bloecke={[
          {
            art: 'liste',
            numeriert: false,
            eintraege: [
              [{ art: 'klartext', text: 'Datenbank' }],
              [
                { art: 'klartext', text: 'Dokumente in ' },
                { art: 'betont', text: 'MinIO' },
              ],
            ],
          },
        ]}
      />,
    );

    const liste = screen.getByRole('list');
    expect(liste.tagName).toBe('UL');
    expect(within(liste).getAllByRole('listitem').map((eintrag) => eintrag.textContent)).toEqual([
      'Datenbank',
      'Dokumente in MinIO',
    ]);
  });

  it('setzt eine numerierte Aufzaehlung als ol', () => {
    renderMitTheme(
      <Dokumenttext
        bloecke={[
          {
            art: 'liste',
            numeriert: true,
            eintraege: [[{ art: 'klartext', text: 'Sichern' }], [{ art: 'klartext', text: 'Aktualisieren' }]],
          },
        ]}
      />,
    );

    const liste = screen.getByRole('list');
    expect(liste.tagName).toBe('OL');
    expect(within(liste).getAllByRole('listitem')).toHaveLength(2);
  });

  it('behaelt im Befehlsblock die Zeilenumbrueche (E5)', () => {
    const befehl = 'cd fb-crm\ngit fetch --tags\ngit checkout v1.2.0';
    renderMitTheme(<Dokumenttext bloecke={[{ art: 'befehl', inhalt: befehl }]} />);

    const block = screen.getByTestId('dokumenttext-befehl');
    expect(block.textContent).toBe(befehl);
    expect(getComputedStyle(block).whiteSpace).toBe('pre-wrap');
  });

  it('zeigt <script> im Fliesstext als Wortlaut und legt keinen Skriptknoten an', () => {
    const bloecke: Block[] = [
      { art: 'text', inhalt: [{ art: 'klartext', text: '<script>alert(1)</script>' }] },
    ];
    renderMitTheme(<Dokumenttext bloecke={bloecke} />);

    expect(screen.getByText('<script>alert(1)</script>')).toBeInTheDocument();
    // Ein Skriptknoten hat keine Rolle und keinen Text, den Testing Library finden koennte — die
    // Abwesenheit laesst sich nur am Dokument selbst pruefen (CLAUDE-security.md).
    // eslint-disable-next-line testing-library/no-node-access -- Nachweis: kein `script` im Baum
    expect(document.querySelector('script')).toBeNull();
  });

  it('setzt die Bloecke in der Reihenfolge der Eingabe', () => {
    renderMitTheme(
      <Dokumenttext
        bloecke={[
          { art: 'ueberschrift', ebene: 2, text: 'Eins', anker: 'eins' },
          { art: 'text', inhalt: [{ art: 'klartext', text: 'Zwei' }] },
          { art: 'befehl', inhalt: 'drei' },
        ]}
      />,
    );

    expect(screen.getByTestId('dokumenttext').textContent).toBe('EinsZweidrei');
  });
});
