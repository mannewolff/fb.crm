import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { renderMitTheme } from '../test/render';
import Karte from './Karte';

describe('Karte', () => {
  it('traegt Titel, Zaehler, Notiz und Werkzeugbereich im Kartenkopf', () => {
    renderMitTheme(
      <Karte
        titel="Ansprechpartner"
        anzahl={2}
        notiz="12 aktiv"
        werkzeug={<button type="button">Filtern</button>}
      >
        <p>Inhalt</p>
      </Karte>,
    );

    const kopf = screen.getByTestId('karte-kopf');
    // Der Zaehler steht *neben* dem Titel und nicht in ihm: Sonst hiesse die Ueberschrift
    // „Ansprechpartner 2", und jeder Aufrufer muesste seinen Namen um die Zahl erweitern.
    expect(screen.getByRole('heading', { name: 'Ansprechpartner' })).toBeInTheDocument();
    expect(within(kopf).getByText('2')).toBeInTheDocument();
    expect(within(kopf).getByText('12 aktiv')).toBeInTheDocument();
    expect(within(kopf).getByRole('button', { name: 'Filtern' })).toBeInTheDocument();
    expect(screen.getByText('Inhalt')).toBeInTheDocument();
  });

  it('bleibt ohne Titel eine Karte ohne Kopf', () => {
    renderMitTheme(
      <Karte>
        <p>Inhalt</p>
      </Karte>,
    );

    expect(screen.queryByTestId('karte-kopf')).not.toBeInTheDocument();
    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
    expect(screen.getByText('Inhalt')).toBeInTheDocument();
  });

  it('laesst Zaehler, Notiz und Werkzeugbereich weg, wenn es sie nicht gibt', () => {
    renderMitTheme(
      <Karte titel="Firmen">
        <p>Inhalt</p>
      </Karte>,
    );

    const kopf = screen.getByTestId('karte-kopf');
    expect(screen.getByRole('heading', { name: 'Firmen' })).toBeInTheDocument();
    expect(within(kopf).queryByTestId('karte-anzahl')).not.toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('zeigt den Zaehler auch bei null Eintraegen', () => {
    renderMitTheme(
      <Karte titel="Ansprechpartner" anzahl={0}>
        <p>Inhalt</p>
      </Karte>,
    );

    // `anzahl={0}` ist eine Angabe wie jede andere. Eine Pruefung auf Wahrheitswert liesse die
    // Null verschwinden — und eine leere Karte saehe aus wie eine ohne Zaehler.
    expect(screen.getByTestId('karte-anzahl')).toHaveTextContent('0');
  });

  it('schreibt den Titel als Ueberschrift der zweiten Ebene', () => {
    renderMitTheme(
      <Karte titel="Firmen">
        <p>Inhalt</p>
      </Karte>,
    );

    expect(screen.getByRole('heading', { level: 2, name: 'Firmen' })).toBeInTheDocument();
  });

  it('hebt den Titel auf die erste Ebene, wo die Karte die ganze Ansicht ist', () => {
    // Eine Ansicht ohne Kopfkarte hat sonst keine h1 — dann traegt der Kartenkopf sie.
    renderMitTheme(
      <Karte titel="Eigene Angaben" titelEbene={1}>
        <p>Inhalt</p>
      </Karte>,
    );

    expect(screen.getByRole('heading', { level: 1, name: 'Eigene Angaben' })).toBeInTheDocument();
    // Die Gestalt bleibt die des Kartenkopfes: 18 px, 700 — nur die Ebene wechselt.
    expect(getComputedStyle(screen.getByRole('heading', { level: 1 })).fontSize).toBe('18px');
  });

  it('gliedert ohne Linie unter dem Kopf', () => {
    renderMitTheme(
      <Karte titel="Firmen">
        <p>Inhalt</p>
      </Karte>,
    );

    // Karten statt Linien (CLAUDE-design.md, Leitgedanke): Der Abstand traegt die Gliederung.
    expect(screen.getByTestId('karte-kopf')).not.toHaveStyle({ borderBottomStyle: 'solid' });
  });
});
