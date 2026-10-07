import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { renderMitTheme } from '../test/render';
import Kennzahlkachel from './Kennzahlkachel';

describe('Kennzahlkachel', () => {
  it('zeigt Beschriftung und Zahl', () => {
    renderMitTheme(
      <Kennzahlkachel
        toenung="salbei"
        symbol={<span>€</span>}
        beschriftung="Abgerechnet"
        zahl="1.800,00 €"
      />,
    );

    expect(screen.getByText('Abgerechnet')).toBeInTheDocument();
    expect(screen.getByText('1.800,00 €')).toBeInTheDocument();
  });

  it('haelt das Symbol aus dem Baum der Hilfsmittel heraus', () => {
    renderMitTheme(
      <Kennzahlkachel
        toenung="flieder"
        symbol={<span>€</span>}
        beschriftung="Abgerechnet"
        zahl="2"
      />,
    );

    // Das Symbol stuetzt die Beschriftung und ersetzt sie nie — vorgelesen haengte es der
    // Kachel einen zweiten, stummen Namensteil an (wie `TastenSymbol`).
    expect(screen.getByTestId('kennzahlkachel-symbol')).toHaveAttribute('aria-hidden', 'true');
  });

  it('zeigt die Zweitzeile, wo sie uebergeben ist', () => {
    renderMitTheme(
      <Kennzahlkachel
        toenung="himmel"
        symbol={<span>€</span>}
        beschriftung="Abgerechnet"
        zahl="1.800,00 €"
        zweitzeile="2.142,00 € brutto · 2 Rechnungen"
      />,
    );

    expect(screen.getByTestId('kennzahlkachel-zweitzeile')).toHaveTextContent(
      '2.142,00 € brutto · 2 Rechnungen',
    );
  });

  it('laesst die Zweitzeile weg, wo keine uebergeben ist', () => {
    renderMitTheme(
      <Kennzahlkachel
        toenung="bernstein"
        symbol={<span>€</span>}
        beschriftung="Noch nicht abgerechnet"
        zahl="4.280,50 €"
      />,
    );

    expect(screen.queryByTestId('kennzahlkachel-zweitzeile')).not.toBeInTheDocument();
  });
});
