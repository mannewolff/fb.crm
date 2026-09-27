import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import Kennzahl from './Kennzahl';
import type { ToenungName } from '../theme';
import { KENNZAHL_TYPOGRAFIE, ZAHLEN_KLASSE, theme } from '../theme';
import { ohneRueckfall } from '../test/cssvar';
import { renderMitTheme } from '../test/render';

const TOENUNGEN: readonly ToenungName[] = [
  'pfirsich',
  'salbei',
  'himmel',
  'bernstein',
  'rose',
  'flieder',
];

describe('Kennzahl', () => {
  it('zeigt Beschriftung, Zahl und Symbol', () => {
    renderMitTheme(
      <Kennzahl
        beschriftung="Pipeline netto"
        zahl="18.450,00 €"
        toenung="salbei"
        symbol={<svg data-testid="zeichen" />}
      />,
    );

    expect(screen.getByText('Pipeline netto')).toBeInTheDocument();
    expect(screen.getByText('18.450,00 €')).toBeInTheDocument();
    expect(screen.getByTestId('zeichen')).toBeInTheDocument();
  });

  it('haelt das Symbolfeld aus dem Vorgelesenen heraus', () => {
    renderMitTheme(
      <Kennzahl beschriftung="Pipeline netto" zahl="0,00 €" toenung="salbei" symbol={<svg />} />,
    );

    // Das Symbol stuetzt die Beschriftung, es traegt keine eigene Aussage
    // (CLAUDE-design.md, „Zustandsformen").
    expect(screen.getByTestId('kennzahl-symbol')).toHaveAttribute('aria-hidden', 'true');
  });

  it('setzt die Zahl in Tabellenziffern und in der Kennzahl-Typografie', () => {
    renderMitTheme(
      <Kennzahl beschriftung="Gewichtete Pipeline" zahl="9.225,00 €" toenung="flieder" symbol={<svg />} />,
    );

    const zahl = screen.getByTestId('kennzahl-zahl');
    // Zwei Kacheln stehen nebeneinander; ihre Betraege stehen damit untereinander
    // (Vorlage `.zahl-karte b` Z. 74).
    expect(zahl).toHaveClass(ZAHLEN_KLASSE);
    expect(zahl).toHaveStyle({
      fontSize: `${String(KENNZAHL_TYPOGRAFIE.fontSize)}px`,
      fontWeight: String(KENNZAHL_TYPOGRAFIE.fontWeight),
      letterSpacing: KENNZAHL_TYPOGRAFIE.letterSpacing,
    });
  });

  it.each(TOENUNGEN)('nimmt die Toenung %s als Eigenschaft an', (name) => {
    renderMitTheme(
      <Kennzahl beschriftung="Kennzahl" zahl="1,00 €" toenung={name} symbol={<svg />} />,
    );

    // Die Kachel faerbt nichts selbst: Flaeche und Schrift kommen aus **einem** Zugriff auf
    // `toenung[name]`. Geprueft wird die Schrift — `background-color` mit `var(…)` laesst jsdom
    // nicht durch seinen Farbpruefer.
    expect(screen.getByTestId('kennzahl')).toHaveStyle({
      color: ohneRueckfall(theme.vars.palette.kupferwolke.toenung[name].schrift),
    });
  });

  it('ist kein Tastaturziel — die Kachel hat kein Ziel', () => {
    renderMitTheme(
      <Kennzahl beschriftung="Pipeline netto" zahl="0,00 €" toenung="salbei" symbol={<svg />} />,
    );

    // Eine Kachel, die sich anfassen laesst, ohne irgendwohin zu fuehren, ist eine leere
    // Verheissung im Tabulatorweg (CLAUDE-react.md, Accessibility).
    expect(screen.getByTestId('kennzahl')).not.toHaveAttribute('tabindex');
    expect(screen.queryAllByRole('link')).toHaveLength(0);
    expect(screen.queryAllByRole('button')).toHaveLength(0);
  });
});
