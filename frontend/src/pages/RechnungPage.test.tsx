import { screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import RechnungPage from './RechnungPage';
import { KopfPfadProvider } from '../components/KopfPfad';
import { fetchNachPfad, json, problem } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';

const WEG = 'GET /api/rechnungen/4';

const ENTWURF = {
  id: 4,
  angebotId: 9,
  firmaId: 5,
  firmaName: 'Adler AG',
  rechnungDatum: '2026-10-01',
  leistungszeitraum: 'Oktober 2026',
  zustand: 'ENTWURF',
  nummer: null,
  steuersatz: 19,
  netto: 9600,
  steuer: 1824,
  brutto: 11424,
  zahlungszielTage: null,
  empfaenger: null,
  absender: null,
  zeilen: [],
};

const GESTELLT = { ...ENTWURF, zustand: 'GESTELLT', nummer: '0001-2026', zahlungszielTage: 14 };

function renderSeite(adresse = '/rechnungen/4') {
  return renderMitTheme(
    <MemoryRouter initialEntries={[adresse]}>
      <KopfPfadProvider>
        <Routes>
          <Route path="/rechnungen/:rechnungId" element={<RechnungPage />} />
        </Routes>
      </KopfPfadProvider>
    </MemoryRouter>,
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('RechnungPage — die erste Fassung (Issue #184)', () => {
  it('zeigt waehrend des Ladens einen Hinweis', () => {
    fetchNachPfad({ [WEG]: json(200, ENTWURF) });

    renderSeite();

    expect(screen.getByText('Die Rechnung wird geladen …')).toBeInTheDocument();
  });

  it('nennt den Entwurf „Rechnung (Entwurf)" und verweist auf die Firma', async () => {
    fetchNachPfad({ [WEG]: json(200, ENTWURF) });

    renderSeite();

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Rechnung (Entwurf)' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Adler AG' })).toHaveAttribute('href', '/firmen/5');
  });

  it('nennt die gestellte Rechnung mit ihrer Nummer', async () => {
    fetchNachPfad({ [WEG]: json(200, GESTELLT) });

    renderSeite();

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Rechnung 0001-2026' }),
    ).toBeInTheDocument();
  });

  it('sagt, dass die Ansicht der Rechnung noch folgt', async () => {
    fetchNachPfad({ [WEG]: json(200, ENTWURF) });

    renderSeite();

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Die vollständige Ansicht dieser Rechnung folgt.',
    );
  });

  it('meldet eine Rechnung, die es nicht gibt (404)', async () => {
    fetchNachPfad({ [WEG]: problem(404, 'weg') });

    renderSeite();

    expect(await screen.findByRole('alert')).toHaveTextContent('Diese Rechnung gibt es nicht.');
  });

  it('meldet eine Kennung, die keine ist, ohne das Netz zu bemuehen', async () => {
    const fetchMock = fetchNachPfad({});

    renderSeite('/rechnungen/keine-zahl');

    expect(await screen.findByRole('alert')).toHaveTextContent('Diese Rechnung gibt es nicht.');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('meldet den Ausfall des Weges', async () => {
    fetchNachPfad({ [WEG]: problem(500, 'kaputt') });

    renderSeite();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Die Rechnung ist gerade nicht zu erreichen.',
    );
  });
});
