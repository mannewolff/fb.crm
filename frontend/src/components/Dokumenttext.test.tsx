import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import type { Block } from '../lib/markdown';
import { renderMitTheme } from '../test/render';
import Dokumenttext from './Dokumenttext';

describe('Dokumenttext', () => {
  it('setzt jeden ##-Abschnitt in eine eigene Karte mit h2 als Sprungziel (F1, E8)', () => {
    renderMitTheme(
      <Dokumenttext
        bloecke={[
          { schluessel: 's1', art: 'ueberschrift', ebene: 2, text: 'Sicherung', anker: 'sicherung' },
          { schluessel: 's2', art: 'text', inhalt: [{ schluessel: 's3', art: 'klartext', text: 'Erst sichern.' }] },
          { schluessel: 's4', art: 'ueberschrift', ebene: 2, text: 'Wiederherstellung', anker: 'wiederherstellung' },
          { schluessel: 's5', art: 'text', inhalt: [{ schluessel: 's6', art: 'klartext', text: 'Dann zurueckspielen.' }] },
        ]}
      />,
    );

    const karten = screen.getAllByTestId('dokumenttext-abschnitt');
    expect(karten).toHaveLength(2);
    const ueberschrift = within(karten[0]).getByRole('heading', { level: 2, name: 'Sicherung' });
    expect(ueberschrift.tagName).toBe('H2');
    expect(ueberschrift).toHaveAttribute('id', 'sicherung');
    expect(ueberschrift).toHaveAttribute('tabindex', '-1');
    expect(within(karten[0]).getByText('Erst sichern.')).toBeInTheDocument();
    expect(within(karten[1]).getByRole('heading', { level: 2 })).toHaveAttribute(
      'id',
      'wiederherstellung',
    );
    expect(within(karten[1]).getByText('Dann zurueckspielen.')).toBeInTheDocument();
    // Die Karte ist als Abschnitt nach ihrer Ueberschrift benannt.
    expect(screen.getByRole('region', { name: 'Sicherung' })).toBe(karten[0]);
  });

  it('setzt ### als h3 mit Anker und tabIndex -1 in die Karte seines ## (F1)', () => {
    renderMitTheme(
      <Dokumenttext
        bloecke={[
          { schluessel: 's7', art: 'ueberschrift', ebene: 2, text: 'Sicherung', anker: 'sicherung' },
          { schluessel: 's8', art: 'ueberschrift', ebene: 3, text: 'Datenbank', anker: 'datenbank' },
          { schluessel: 's9', art: 'ueberschrift', ebene: 2, text: 'Wiederherstellung', anker: 'wiederherstellung' },
        ]}
      />,
    );

    const [erste, zweite] = screen.getAllByTestId('dokumenttext-abschnitt');
    const ueberschrift = within(erste).getByRole('heading', { level: 3, name: 'Datenbank' });
    expect(ueberschrift.tagName).toBe('H3');
    expect(ueberschrift).toHaveAttribute('id', 'datenbank');
    expect(ueberschrift).toHaveAttribute('tabindex', '-1');
    expect(within(zweite).queryByRole('heading', { level: 3 })).not.toBeInTheDocument();
  });

  it('gibt ### die Gestalt „Name in Liste": 15 px, 700, oben mehr Abstand als unten (F1)', () => {
    renderMitTheme(
      <Dokumenttext
        bloecke={[
          { schluessel: 's10', art: 'text', inhalt: [{ schluessel: 's11', art: 'klartext', text: 'Vorab.' }] },
          { schluessel: 's12', art: 'ueberschrift', ebene: 3, text: 'Datenbank', anker: 'datenbank' },
        ]}
      />,
    );

    const stil = getComputedStyle(screen.getByRole('heading', { level: 3 }));
    expect(stil.fontSize).toBe('15px');
    expect(stil.fontWeight).toBe('700');
    expect(stil.marginTop).toBe('28px');
    expect(stil.marginBottom).toBe('6px');
  });

  it('setzt die Bloecke vor dem ersten ## in eine Karte ohne Titel', () => {
    renderMitTheme(
      <Dokumenttext
        bloecke={[
          { schluessel: 's13', art: 'text', inhalt: [{ schluessel: 's14', art: 'klartext', text: 'Vorab.' }] },
          { schluessel: 's15', art: 'ueberschrift', ebene: 2, text: 'Sicherung', anker: 'sicherung' },
        ]}
      />,
    );

    const [vorab, sicherung] = screen.getAllByTestId('dokumenttext-abschnitt');
    expect(within(vorab).queryByRole('heading')).not.toBeInTheDocument();
    expect(within(vorab).getByText('Vorab.')).toBeInTheDocument();
    expect(within(sicherung).getByRole('heading', { level: 2 })).toHaveTextContent('Sicherung');
  });

  it('legt ohne Bloecke vor dem ersten ## keine leere Karte an', () => {
    renderMitTheme(
      <Dokumenttext
        bloecke={[{ schluessel: 's16', art: 'ueberschrift', ebene: 2, text: 'Sicherung', anker: 'sicherung' }]}
      />,
    );

    expect(screen.getAllByTestId('dokumenttext-abschnitt')).toHaveLength(1);
  });

  it('setzt Fliesstext als Absatz in der Lesebreite, mit allen Inline-Formen', () => {
    renderMitTheme(
      <Dokumenttext
        bloecke={[
          {
            schluessel: 's36',
            art: 'text',
            inhalt: [
              { schluessel: 's17', art: 'klartext', text: 'Erst ' },
              { schluessel: 's18', art: 'betont', text: 'sichern' },
              { schluessel: 's19', art: 'klartext', text: ', dann ' },
              { schluessel: 's20', art: 'schnipsel', text: 'docker compose up' },
              { schluessel: 's21', art: 'klartext', text: ', siehe ' },
              { schluessel: 's22', art: 'verweis', text: 'Betrieb', ziel: '#betrieb' },
            ],
          },
        ]}
      />,
    );

    const absatz = screen.getByText(/Erst/u, { selector: 'p' });
    expect(absatz).toHaveTextContent('Erst sichern, dann docker compose up, siehe Betrieb');
    expect(within(absatz).getByText('sichern').tagName).toBe('STRONG');
    expect(within(absatz).getByText('docker compose up').tagName).toBe('CODE');
    expect(getComputedStyle(screen.getByTestId('dokumenttext-lesespalte')).maxWidth).toBe('70ch');
  });

  it('setzt einen Verweis als Link mit seinem Ziel', () => {
    renderMitTheme(
      <Dokumenttext
        bloecke={[
          {
            schluessel: 's37',
            art: 'text',
            inhalt: [{ schluessel: 's23', art: 'verweis', text: 'zur Sicherung', ziel: '#sicherung' }],
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
            schluessel: 's38',
            art: 'liste',
            numeriert: false,
            eintraege: [
              { schluessel: 'e1', inhalt: [{ schluessel: 's24', art: 'klartext', text: 'Datenbank' }] },
              {
                schluessel: 'e2',
                inhalt: [
                  { schluessel: 's25', art: 'klartext', text: 'Dokumente in ' },
                  { schluessel: 's26', art: 'betont', text: 'MinIO' },
                ],
              },
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
            schluessel: 's39',
            art: 'liste',
            numeriert: true,
            eintraege: [
              { schluessel: 'e3', inhalt: [{ schluessel: 's27', art: 'klartext', text: 'Sichern' }] },
              { schluessel: 'e4', inhalt: [{ schluessel: 's28', art: 'klartext', text: 'Aktualisieren' }] },
            ],
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
    renderMitTheme(<Dokumenttext bloecke={[{ schluessel: 's29', art: 'befehl', inhalt: befehl }]} />);

    const block = screen.getByTestId('dokumenttext-befehl');
    expect(block.textContent).toBe(befehl);
    expect(getComputedStyle(block).whiteSpace).toBe('pre-wrap');
  });

  it('zeigt <script> im Fliesstext als Wortlaut und legt keinen Skriptknoten an', () => {
    const bloecke: Block[] = [
      { schluessel: 's30', art: 'text', inhalt: [{ schluessel: 's31', art: 'klartext', text: '<script>alert(1)</script>' }] },
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
          { schluessel: 's32', art: 'ueberschrift', ebene: 2, text: 'Eins', anker: 'eins' },
          { schluessel: 's33', art: 'text', inhalt: [{ schluessel: 's34', art: 'klartext', text: 'Zwei' }] },
          { schluessel: 's35', art: 'befehl', inhalt: 'drei' },
        ]}
      />,
    );

    expect(screen.getByTestId('dokumenttext').textContent).toBe('EinsZweidrei');
  });
});
