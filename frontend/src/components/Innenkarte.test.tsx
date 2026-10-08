import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { renderMitTheme } from '../test/render';
import Innenkarte, { HinzufuegenKachel, InnenkartenRaster } from './Innenkarte';
import ZustandsChip from './ZustandsChip';

describe('Innenkarte', () => {
  it('zeigt Name, Unterzeile und Kontaktwege', () => {
    renderMitTheme(
      <Innenkarte name="Gabi Rosenbaum" unterzeile="Führungskraft">
        <a href="mailto:gabi.rosenbaum@it-bildungshaus.de">gabi.rosenbaum@it-bildungshaus.de</a>
        <span>0421 123 45 67</span>
      </Innenkarte>,
    );

    expect(screen.getByText('Gabi Rosenbaum')).toBeInTheDocument();
    expect(screen.getByText('Führungskraft')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'gabi.rosenbaum@it-bildungshaus.de' })).toHaveAttribute(
      'href',
      'mailto:gabi.rosenbaum@it-bildungshaus.de',
    );
    expect(screen.getByText('0421 123 45 67')).toBeInTheDocument();
    // Das Kuerzel steht neben dem Namen und doppelte ihn nur.
    expect(screen.getByTestId('mal')).toHaveAttribute('aria-hidden', 'true');
  });

  it('haelt die Aktion im DOM, benannt und per Tabulator erreichbar', async () => {
    const nutzer = userEvent.setup();
    renderMitTheme(
      <Innenkarte
        name="Gabi Rosenbaum"
        toenung="salbei"
        aktion={
          <button type="button" aria-label="Aktionen für Gabi Rosenbaum">
            ⋯
          </button>
        }
      />,
    );

    // Die Aktion erscheint erst im Hover — sie darf darum nicht aus dem DOM verschwinden, sonst
    // waere sie ohne Maus nicht erreichbar (CLAUDE-design.md, „Tasten"; E12).
    const aktion = screen.getByRole('button', { name: 'Aktionen für Gabi Rosenbaum' });
    await nutzer.tab();
    expect(aktion).toHaveFocus();
  });

  it('bleibt ohne Unterzeile, Kontaktwege und Aktion eine Innenkarte mit Namen', () => {
    renderMitTheme(<Innenkarte name="Konstanze Wilschewski" />);

    expect(screen.getByText('Konstanze Wilschewski')).toBeInTheDocument();
    expect(screen.queryByTestId('innenkarte-unterzeile')).not.toBeInTheDocument();
    expect(screen.queryByTestId('innenkarte-zustand')).not.toBeInTheDocument();
    expect(screen.queryByTestId('innenkarte-kontakt')).not.toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('stellt den Zustand als Chip unter den Namen', () => {
    renderMitTheme(
      <Innenkarte
        name="Konstanze Wilschewski"
        unterzeile="Ansprechpartnerin"
        zustand={<ZustandsChip wort="Stillgelegt" toenung="rose" />}
        matt
      />,
    );

    // Der Zustand steht als Wort im Dokument — nicht nur als matte Schrift am Namen
    // (CLAUDE-design.md, „Zustandsformen").
    const zustand = screen.getByTestId('innenkarte-zustand');
    expect(within(zustand).getByText('Stillgelegt')).toBeInTheDocument();
    // Unter dem Namen und ueber der Unterzeile — die Reihenfolge im DOM ist die gelesene.
    const teile = screen
      .getAllByTestId(/^innenkarte-/)
      .map((element) => element.dataset.testid);
    expect(teile).toEqual(['innenkarte-zustand', 'innenkarte-unterzeile']);
  });
});

describe('InnenkartenRaster', () => {
  it('traegt die Innenkarten in uebergebener Reihenfolge', () => {
    renderMitTheme(
      <InnenkartenRaster>
        <Innenkarte name="Gabi Rosenbaum" />
        <Innenkarte name="Konstanze Wilschewski" />
      </InnenkartenRaster>,
    );

    const raster = screen.getByTestId('innenkarten-raster');
    expect(within(raster).getByText('Gabi Rosenbaum')).toBeInTheDocument();
    expect(within(raster).getByText('Konstanze Wilschewski')).toBeInTheDocument();
  });
});

describe('HinzufuegenKachel', () => {
  it('ist ein echter Link mit Text', () => {
    renderMitTheme(
      <MemoryRouter>
        <HinzufuegenKachel to="/firmen/1/ansprechpartner/neu" symbol={<span>＋</span>}>
          Ansprechpartner hinzufügen
        </HinzufuegenKachel>
      </MemoryRouter>,
    );

    // Ein Weg gehoert in ein `a` mit `href` — sonst faellt er aus dem Tabulatorweg der Links.
    const kachel = screen.getByRole('link', { name: 'Ansprechpartner hinzufügen' });
    expect(kachel).toHaveAttribute('href', '/firmen/1/ansprechpartner/neu');
  });

  it('kommt auch ohne Symbol aus', () => {
    renderMitTheme(
      <MemoryRouter>
        <HinzufuegenKachel to="/firmen/neu">Firma hinzufügen</HinzufuegenKachel>
      </MemoryRouter>,
    );

    expect(screen.getByRole('link', { name: 'Firma hinzufügen' })).toBeInTheDocument();
    expect(screen.queryByTestId('taste-symbol')).not.toBeInTheDocument();
  });
});
