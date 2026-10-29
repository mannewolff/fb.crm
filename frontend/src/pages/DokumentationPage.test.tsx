import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation, useNavigationType } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import DokumentationPage from './DokumentationPage';
import { KopfPfadProvider } from '../components/KopfPfad';
import KopfPfad from '../components/KopfPfad';
import type { Kennung, Teil } from '../lib/dokumentation';
import { teilNach } from '../lib/dokumentation';
import { leseMarkdown } from '../lib/markdown';
import { renderMitTheme } from '../test/render';

/**
 * Ein Ersatz fuer einen Teil, solange ein Fall ihn setzt: ein kleiner Text, an dem sich die
 * Gliederung (Issue #474, F2) Eintrag fuer Eintrag pruefen laesst. Ohne Ersatz liest die Seite die
 * echten Teile.
 */
const ersatz = vi.hoisted(() => ({ teil: null as Teil | null }));

vi.mock('../lib/dokumentation', async (importOriginal) => {
  const echt = await importOriginal<typeof import('../lib/dokumentation')>();
  return {
    ...echt,
    teilNach: (kennung: Kennung): Teil => ersatz.teil ?? echt.teilNach(kennung),
  };
});

/** Ein Teil mit beiden Ebenen; der erste Abschnitt ist eine `###` ohne `##` darueber. */
const MIT_UNTERABSCHNITTEN = `# Betrieb

### Vorab

Ein Satz.

## Sicherung

### Datenbank

### Dokumente

## Wiederherstellung
`;

/** Zeigt, wo der Verlauf steht und wie er dorthin kam. */
function Stelle() {
  const ort = useLocation();
  const art = useNavigationType();
  return <output data-testid="stelle">{`${art} ${ort.pathname}`}</output>;
}

function renderSeite(adresse = '/dokumentation') {
  return renderMitTheme(
    <MemoryRouter initialEntries={['/', adresse]} initialIndex={1}>
      <KopfPfadProvider>
        <KopfPfad />
        <Routes>
          <Route path="/" element={<p>Start</p>} />
          <Route path="/dokumentation" element={<DokumentationPage />} />
          <Route path="/dokumentation/:teil" element={<DokumentationPage />} />
        </Routes>
        <Stelle />
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

/** Die `##`-Ueberschriften des Dokuments — die Titel der Abschnittskarten, ohne „Inhalt". */
function abschnitte(): HTMLElement[] {
  return within(screen.getByTestId('dokumenttext')).queryAllByRole('heading', { level: 2 });
}

function inhalt() {
  return within(screen.getByRole('navigation', { name: 'Inhalt' }));
}

function umschalter() {
  return within(screen.getByRole('navigation', { name: 'Teile der Dokumentation' }));
}

/** Die `##`-Abschnittsnamen eines echten Teils, aus dem Leser — nichts abgeschrieben (E16). */
function abschnittsnamen(kennung: Kennung): string[] {
  return teilNach(kennung).dokument.bloecke.flatMap((block) =>
    block.art === 'ueberschrift' && block.ebene === 2 ? [block.text] : [],
  );
}

afterEach(() => {
  ersatz.teil = null;
});

describe('DokumentationPage', () => {
  it('Szenario: Dokumentation in fb.crm lesen', async () => {
    const nutzer = userEvent.setup();

    renderSeite('/dokumentation');

    // Der Teil „Bedienung" mit seinen Abschnitten …
    expect(screen.getByRole('heading', { level: 1, name: 'Bedienung' })).toBeInTheDocument();
    expect(abschnitte().map((element) => element.textContent)).toEqual(
      abschnittsnamen('bedienung'),
    );

    // … der Wechsel auf „Betrieb" …
    await nutzer.click(umschalter().getByRole('link', { name: 'Betrieb' }));

    expect(screen.getByRole('heading', { level: 1, name: 'Betrieb' })).toBeInTheDocument();
    expect(screen.getByTestId('stelle')).toHaveTextContent('PUSH /dokumentation/betrieb');

    // … und der Sprung zum Abschnitt „Sicherung".
    const verweis = inhalt().getByRole('link', { name: 'Sicherung' });
    const ziel = screen.getByRole('heading', { level: 2, name: 'Sicherung' });
    expect(verweis).toHaveAttribute('href', `#${ziel.id}`);
    expect(ziel.id).not.toBe('');
  });

  it('zeigt unter „/dokumentation" den Teil „Bedienung" (E6, Kriterium 1)', () => {
    renderSeite('/dokumentation');

    expect(screen.getByRole('heading', { level: 1, name: 'Bedienung' })).toBeInTheDocument();
    expect(screen.getByTestId('stelle')).toHaveTextContent('POP /dokumentation');
  });

  it('zeigt unter „/dokumentation/betrieb" den Teil „Betrieb" mit seinen Abschnitten (E6)', () => {
    renderSeite('/dokumentation/betrieb');

    expect(screen.getByRole('heading', { level: 1, name: 'Betrieb' })).toBeInTheDocument();
    expect(abschnitte().map((element) => element.textContent)).toEqual(abschnittsnamen('betrieb'));
  });

  it.each(['/dokumentation/unbekannt', '/dokumentation/bedienung'])(
    'fuehrt „%s" mit replace auf „/dokumentation" (E6)',
    (adresse) => {
      renderSeite(adresse);

      // `REPLACE`: Die Adresse ersetzt den Eintrag im Verlauf, statt einen hinzuzufuegen.
      expect(screen.getByTestId('stelle')).toHaveTextContent('REPLACE /dokumentation');
      expect(screen.getByRole('heading', { level: 1, name: 'Bedienung' })).toBeInTheDocument();
    },
  );

  it.each([
    ['/dokumentation', 'Bedienung'],
    ['/dokumentation/betrieb', 'Betrieb'],
  ])('setzt auf %s aria-current="page" an „%s" und nur dort (E17)', (adresse, name) => {
    renderSeite(adresse);

    const links = umschalter().getAllByRole('link');
    expect(links.map((link) => link.textContent)).toEqual(['Bedienung', 'Betrieb']);
    const aktiv = umschalter().getAllByRole('link', { current: 'page' });
    expect(aktiv).toHaveLength(1);
    expect(aktiv[0]).toHaveAccessibleName(name);
  });

  it('meldet „Dokumentation" als Pfad im Kopf', () => {
    renderSeite('/dokumentation/betrieb');

    expect(screen.getByRole('navigation', { name: 'Pfad' })).toHaveTextContent('Dokumentation');
  });

  it.each(['/dokumentation', '/dokumentation/betrieb'])(
    'fuehrt auf %s jede ##-Ueberschrift im Inhalt und jeden Verweis auf eine (E8, F2)',
    (adresse) => {
      renderSeite(adresse);

      const ziele = abschnitte().map((element) => `#${element.id}`);
      const verweise = inhalt()
        .getAllByRole('link')
        .map((link) => link.getAttribute('href'));
      // Beide Richtungen in einem: gleiche Folge, und kein Ziel ist leer oder doppelt.
      expect(verweise).toEqual(ziele);
      expect(ziele).not.toContain('#');
      expect(new Set(ziele).size).toBe(ziele.length);
    },
  );

  it('fuehrt im Inhalt nur die ##-Ebene, ohne ###-Eintraege (F2)', () => {
    ersatz.teil = { kennung: 'betrieb', name: 'Betrieb', dokument: leseMarkdown(MIT_UNTERABSCHNITTEN) };

    renderSeite('/dokumentation/betrieb');

    expect(inhalt().getAllByRole('list')).toHaveLength(1);
    expect(inhalt().getAllByRole('link').map((link) => link.getAttribute('href'))).toEqual([
      '#sicherung',
      '#wiederherstellung',
    ]);
    // Die `###` stehen trotzdem im Text: als h3 in der Karte ihres `##`, die vor dem ersten `##`
    // in einer Karte ohne Titel.
    const [vorab, sicherung] = screen.getAllByTestId('dokumenttext-abschnitt');
    expect(within(vorab).getByRole('heading', { level: 3, name: 'Vorab' })).toBeInTheDocument();
    expect(within(vorab).queryByRole('heading', { level: 2 })).not.toBeInTheDocument();
    expect(
      within(sicherung)
        .getAllByRole('heading', { level: 3 })
        .map((element) => element.id),
    ).toEqual(['datenbank', 'dokumente']);
  });

  it('springt mit einem Verweis im Inhalt auf die Ueberschrift der Abschnittskarte (E8)', () => {
    renderSeite('/dokumentation/betrieb');

    const verweis = inhalt().getByRole('link', { name: 'Sicherung' });
    const karte = screen.getByRole('region', { name: 'Sicherung' });
    const ziel = within(karte).getByRole('heading', { level: 2, name: 'Sicherung' });
    expect(verweis).toHaveAttribute('href', `#${ziel.id}`);
    expect(ziel).toHaveAttribute('tabindex', '-1');
  });

  it.each(['/dokumentation', '/dokumentation/betrieb'])(
    'setzt auf %s jeden ##-Abschnitt in eine eigene Karte (F1)',
    (adresse) => {
      renderSeite(adresse);

      const kennung = adresse.endsWith('betrieb') ? 'betrieb' : 'bedienung';
      // Der einleitende Text vor dem ersten `##` steht in einer Karte ohne Titel.
      const [vorab, ...karten] = screen.getAllByTestId('dokumenttext-abschnitt');
      expect(within(vorab).queryByRole('heading')).not.toBeInTheDocument();
      expect(karten).toHaveLength(abschnittsnamen(kennung).length);
      karten.forEach((karte, index) => {
        expect(within(karte).getAllByRole('heading', { level: 2 })).toHaveLength(1);
        expect(within(karte).getByRole('heading', { level: 2 })).toHaveTextContent(
          abschnittsnamen(kennung)[index],
        );
      });
    },
  );

  it.each(['/dokumentation', '/dokumentation/betrieb'])(
    'traegt auf %s genau eine h1 und springt in den Ebenen nicht',
    (adresse) => {
      renderSeite(adresse);

      const ebenen = screen
        .getAllByRole('heading')
        .map((element) => Number(element.tagName.slice(1)));
      expect(ebenen.filter((ebene) => ebene === 1)).toHaveLength(1);
      expect(ebenen[0]).toBe(1);
      ebenen.slice(1).forEach((ebene, index) => {
        expect(ebene).toBeLessThanOrEqual(ebenen[index] + 1);
      });
    },
  );
});
