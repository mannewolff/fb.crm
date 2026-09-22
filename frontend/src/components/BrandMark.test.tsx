import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { renderMitTheme } from '../test/render';
import BrandMark from './BrandMark';

describe('BrandMark', () => {
  it('traegt Mal und Namen', () => {
    renderMitTheme(<BrandMark />);

    expect(screen.getByTestId('marke-mal')).toBeInTheDocument();
    expect(screen.getByText('fb.crm')).toBeInTheDocument();
  });

  it('zeigt die Versionsnummer, wenn sie uebergeben wird', () => {
    renderMitTheme(<BrandMark version="1.4.0" />);

    expect(screen.getByText('1.4.0')).toBeInTheDocument();
  });

  it('bleibt ohne Versionsnummer stumm', () => {
    renderMitTheme(<BrandMark />);

    // Vor der Anmeldung gibt es keine Version: /api/instance verlangt eine Sitzung
    // (E10, Issue #26).
    expect(screen.queryByTestId('marke-zusatz')).not.toBeInTheDocument();
  });

  it('zeigt kompakt nur das Mal — fuer die eingeklappte Schiene', () => {
    renderMitTheme(<BrandMark version="1.4.0" kompakt />);

    expect(screen.getByTestId('marke-mal')).toBeInTheDocument();
    expect(screen.queryByText('fb.crm')).not.toBeInTheDocument();
    expect(screen.queryByTestId('marke-zusatz')).not.toBeInTheDocument();
  });
});
