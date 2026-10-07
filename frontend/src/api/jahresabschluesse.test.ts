import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  jahresabschluss,
  jahresuebersicht,
  parseJahresabschluss,
  parseJahresuebersicht,
} from './jahresabschluesse';
import { fetchNachPfad, json } from '../test/fetchNachPfad';

const UEBERSICHT = [
  { jahr: '2026', laeuftNoch: true, netto: 12500.5, anzahl: 4, annahmequote: null },
  { jahr: '2025', laeuftNoch: false, netto: 40000, anzahl: 12, annahmequote: 66.7 },
];

const ABSCHLUSS = {
  jahr: '2025',
  laeuftNoch: false,
  einnahmen: { netto: 1000, brutto: 1170, umsatzsteuer: 170 },
  rechnungsstand: {
    anzahl: 3,
    offenAnzahl: 1,
    offenNetto: 250.25,
    abgeschriebenAnzahl: 1,
    abgeschriebenNetto: 50,
  },
  steuerzeilen: [
    { satz: 19, netto: 800, umsatzsteuer: 152 },
    { satz: 7, netto: 100, umsatzsteuer: 7 },
    { satz: null, netto: 100, umsatzsteuer: 11 },
  ],
  angebotsbilanz: {
    abgegeben: 4,
    angenommen: 3,
    offen: 1,
    annahmequote: 75,
    volumenAbgegeben: 2000,
    volumenAngenommen: 1500.5,
  },
  kunden: [
    { firmaName: 'Muster GmbH', netto: 900, anteil: 90 },
    { firmaName: 'Beispiel AG', netto: 100, anteil: 10 },
  ],
  arbeitszeit: { kundenStunden: 10.5, interneStunden: 2.25, erloesJeStunde: 95.24 },
};

describe('parseJahresuebersicht', () => {
  it('liest zwei Jahre vollstaendig und laesst die Annahmequote als null durch', () => {
    expect(parseJahresuebersicht(UEBERSICHT)).toEqual([
      {
        jahr: '2026',
        laeuftNoch: true,
        nettoInCent: 1250050,
        anzahl: 4,
        annahmequoteInHundertstelProzent: null,
      },
      {
        jahr: '2025',
        laeuftNoch: false,
        nettoInCent: 4000000,
        anzahl: 12,
        annahmequoteInHundertstelProzent: 6670,
      },
    ]);
  });

  it('macht aus einer leeren Jahresliste ein leeres Feld', () => {
    expect(parseJahresuebersicht([])).toEqual([]);
  });

  it.each([
    ['keine Liste', { jahre: [] }],
    ['ein fehlendes Feld', [{ jahr: '2025', laeuftNoch: false, anzahl: 1, annahmequote: null }]],
    ['ein Jahr als Zahl', [{ ...UEBERSICHT[1], jahr: 2025 }]],
    ['eine Zahl in Anfuehrungszeichen', [{ ...UEBERSICHT[1], netto: '40000' }]],
    ['eine Quote in Anfuehrungszeichen', [{ ...UEBERSICHT[1], annahmequote: '66.7' }]],
  ])('weist %s ab', (_fall, wert) => {
    expect(() => parseJahresuebersicht(wert)).toThrow(TypeError);
  });
});

describe('parseJahresabschluss', () => {
  it('liest alle Teile vollstaendig', () => {
    expect(parseJahresabschluss(ABSCHLUSS)).toEqual({
      jahr: '2025',
      laeuftNoch: false,
      einnahmen: { nettoInCent: 100000, bruttoInCent: 117000, umsatzsteuerInCent: 17000 },
      rechnungsstand: {
        anzahl: 3,
        offenAnzahl: 1,
        offenNettoInCent: 25025,
        abgeschriebenAnzahl: 1,
        abgeschriebenNettoInCent: 5000,
      },
      steuerzeilen: [
        { satzInHundertstelProzent: 1900, nettoInCent: 80000, umsatzsteuerInCent: 15200 },
        { satzInHundertstelProzent: 700, nettoInCent: 10000, umsatzsteuerInCent: 700 },
        { satzInHundertstelProzent: null, nettoInCent: 10000, umsatzsteuerInCent: 1100 },
      ],
      angebotsbilanz: {
        abgegeben: 4,
        angenommen: 3,
        offen: 1,
        annahmequoteInHundertstelProzent: 7500,
        volumenAbgegebenInCent: 200000,
        volumenAngenommenInCent: 150050,
      },
      kunden: [
        { firmaName: 'Muster GmbH', nettoInCent: 90000, anteilInHundertstelProzent: 9000 },
        { firmaName: 'Beispiel AG', nettoInCent: 10000, anteilInHundertstelProzent: 1000 },
      ],
      arbeitszeit: {
        kundenStundenInHundertsteln: 1050,
        interneStundenInHundertsteln: 225,
        erloesJeStundeInCent: 9524,
      },
    });
  });

  it('laesst jede Kennzahl, die fehlen darf, als null durch', () => {
    const gelesen = parseJahresabschluss({
      ...ABSCHLUSS,
      steuerzeilen: [{ satz: null, netto: 0, umsatzsteuer: 0 }],
      angebotsbilanz: { ...ABSCHLUSS.angebotsbilanz, annahmequote: null },
      kunden: [{ firmaName: 'Muster GmbH', netto: 0, anteil: null }],
      arbeitszeit: { kundenStunden: 0, interneStunden: 0, erloesJeStunde: null },
    });

    expect(gelesen.steuerzeilen[0].satzInHundertstelProzent).toBeNull();
    expect(gelesen.angebotsbilanz.annahmequoteInHundertstelProzent).toBeNull();
    expect(gelesen.kunden[0].anteilInHundertstelProzent).toBeNull();
    expect(gelesen.arbeitszeit.erloesJeStundeInCent).toBeNull();
  });

  it('liest leere Listen als leere Felder', () => {
    const gelesen = parseJahresabschluss({ ...ABSCHLUSS, steuerzeilen: [], kunden: [] });

    expect(gelesen.steuerzeilen).toEqual([]);
    expect(gelesen.kunden).toEqual([]);
  });

  it.each([
    ['null', null],
    ['ein fehlender Teil', { ...ABSCHLUSS, arbeitszeit: undefined }],
    ['ein fehlendes Feld', { ...ABSCHLUSS, einnahmen: { netto: 1000, brutto: 1170 } }],
    ['eine Anzahl als Wort', { ...ABSCHLUSS, rechnungsstand: { ...ABSCHLUSS.rechnungsstand, anzahl: 'drei' } }],
    ['laeuftNoch als Wort', { ...ABSCHLUSS, laeuftNoch: 'nein' }],
    ['Steuerzeilen ohne Liste', { ...ABSCHLUSS, steuerzeilen: {} }],
    ['einen Satz in Anfuehrungszeichen', { ...ABSCHLUSS, steuerzeilen: [{ satz: '19', netto: 1, umsatzsteuer: 0.19 }] }],
    ['einen Anteil in Anfuehrungszeichen', { ...ABSCHLUSS, kunden: [{ firmaName: 'X', netto: 1, anteil: '100.0' }] }],
    ['einen Firmennamen als Zahl', { ...ABSCHLUSS, kunden: [{ firmaName: 1, netto: 1, anteil: 100 }] }],
    ['Stunden mit drei Nachkommastellen', { ...ABSCHLUSS, arbeitszeit: { ...ABSCHLUSS.arbeitszeit, kundenStunden: 1.005 } }],
    ['eine Quote in Anfuehrungszeichen', { ...ABSCHLUSS, angebotsbilanz: { ...ABSCHLUSS.angebotsbilanz, annahmequote: '75.0' } }],
  ])('weist %s ab', (_fall, wert) => {
    expect(() => parseJahresabschluss(wert)).toThrow(TypeError);
  });
});

describe('Abruf', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('holt die Uebersicht ohne Parameter', async () => {
    const aufruf = fetchNachPfad({ 'GET /api/jahresabschluesse': json(200, UEBERSICHT) });

    const gelesen = await jahresuebersicht();

    expect(gelesen).toHaveLength(2);
    expect(aufruf.mock.calls.map(([ziel]) => ziel)).toEqual(['/api/jahresabschluesse']);
  });

  it('holt den Abschluss eines Jahres', async () => {
    const aufruf = fetchNachPfad({ 'GET /api/jahresabschluesse/2025': json(200, ABSCHLUSS) });

    const gelesen = await jahresabschluss('2025');

    expect(gelesen.jahr).toBe('2025');
    expect(aufruf.mock.calls.map(([ziel]) => ziel)).toEqual(['/api/jahresabschluesse/2025']);
  });
});
