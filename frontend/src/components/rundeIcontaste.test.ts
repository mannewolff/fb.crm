import { describe, expect, it } from 'vitest';

import { RADIUS_RUND, theme } from '../theme';
import { ICONTASTE, rundeIcontaste } from './rundeIcontaste';

describe('rundeIcontaste', () => {
  const stil = rundeIcontaste(theme);
  const farben = theme.vars.palette.kupferwolke;

  it('ist ein Kreis von 40 px', () => {
    expect(ICONTASTE).toBe(40);
    expect(stil).toMatchObject({
      width: ICONTASTE,
      height: ICONTASTE,
      borderRadius: `${String(RADIUS_RUND)}px`,
    });
  });

  it('liegt auf „Flaeche weich" mit dem Symbol in „Text matt"', () => {
    expect(stil).toMatchObject({ color: farben.textMatt, background: farben.flaecheWeich });
  });

  it('wechselt beim Hover in die Toenung Pfirsich', () => {
    expect(stil['&:hover']).toEqual({
      background: farben.toenung.pfirsich.flaeche,
      color: farben.toenung.pfirsich.schrift,
    });
  });
});
