import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { renderMitTheme } from '../test/render';
import Platte from './Platte';

describe('Platte', () => {
  it('traegt Titel, Notiz und Werkzeugbereich im Kopf', () => {
    renderMitTheme(
      <Platte titel="Firmen" notiz="12 aktiv" werkzeug={<button type="button">Filtern</button>}>
        <p>Inhalt</p>
      </Platte>,
    );

    expect(screen.getByRole('heading', { name: 'Firmen' })).toBeInTheDocument();
    expect(screen.getByText('12 aktiv')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Filtern' })).toBeInTheDocument();
    expect(screen.getByText('Inhalt')).toBeInTheDocument();
  });

  it('bleibt ohne Titel eine Platte ohne Kopf', () => {
    renderMitTheme(
      <Platte>
        <p>Inhalt</p>
      </Platte>,
    );

    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
    expect(screen.getByText('Inhalt')).toBeInTheDocument();
  });

  it('laesst Notiz und Werkzeugbereich weg, wenn es sie nicht gibt', () => {
    renderMitTheme(
      <Platte titel="Firmen">
        <p>Inhalt</p>
      </Platte>,
    );

    expect(screen.getByRole('heading', { name: 'Firmen' })).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});
