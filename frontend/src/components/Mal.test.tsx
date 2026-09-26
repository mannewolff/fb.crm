import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { RADIUS_MAL, RADIUS_RUND, theme } from '../theme';
import { ohneRueckfall } from '../test/cssvar';
import { renderMitTheme } from '../test/render';
import Mal from './Mal';

describe('Mal', () => {
  it('zeigt das Kuerzel des Namens', () => {
    renderMitTheme(<Mal name="Gabi Rosenbaum" groesse={48} toenung="flieder" />);

    expect(screen.getByTestId('mal')).toHaveTextContent('GR');
  });

  it('nimmt Groesse und Toenung aus den Props', () => {
    renderMitTheme(<Mal name="IT Bildungshaus" groesse={84} toenung="pfirsich" form="quadrat" />);

    const mal = screen.getByTestId('mal');
    // Geprueft wird die Schrift der Toenung: Flaeche und Schrift kommen aus **einem** Zugriff auf
    // `toenung[name]`, und `background-color` mit `var(…)` laesst jsdom nicht durch seinen
    // Farbpruefer — die Flaeche waere dort immer leer.
    expect(mal).toHaveStyle({
      width: '84px',
      height: '84px',
      color: ohneRueckfall(theme.vars.palette.kupferwolke.toenung.pfirsich.schrift),
    });
  });

  it('ist fuer Personen ein Kreis und fuer Firmen ein abgerundetes Quadrat', () => {
    const { unmount } = renderMitTheme(
      <Mal name="Gabi Rosenbaum" groesse={48} toenung="flieder" />,
    );
    expect(screen.getByTestId('mal')).toHaveStyle({ borderRadius: `${RADIUS_RUND}px` });
    unmount();

    renderMitTheme(<Mal name="IT Bildungshaus" groesse={84} toenung="pfirsich" form="quadrat" />);
    expect(screen.getByTestId('mal')).toHaveStyle({ borderRadius: `${RADIUS_MAL}px` });
  });

  it('ist dekorativ, solange der Name daneben steht', () => {
    renderMitTheme(
      <>
        <Mal name="Gabi Rosenbaum" groesse={48} toenung="flieder" />
        <span>Gabi Rosenbaum</span>
      </>,
    );

    // Das Kuerzel ist eine zweite Schreibweise desselben Namens. Vorgelesen wuerde es den Namen
    // doppeln — darum bleibt es aus dem Baum der Hilfsmittel heraus.
    expect(screen.getByTestId('mal')).toHaveAttribute('aria-hidden', 'true');
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  it('nennt den Namen, wo es allein steht', () => {
    renderMitTheme(<Mal name="Gabi Rosenbaum" groesse={38} toenung="flieder" dekorativ={false} />);

    expect(screen.getByRole('img', { name: 'Gabi Rosenbaum' })).toBeInTheDocument();
    expect(screen.getByTestId('mal')).not.toHaveAttribute('aria-hidden');
  });
});
