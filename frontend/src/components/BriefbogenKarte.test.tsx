import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterAll, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import BriefbogenKarte from './BriefbogenKarte';
import { alsJson, fetchNachPfad, formularWeg, json, leer, problem } from '../test/fetchNachPfad';
import { alsRolle } from '../test/rechteDoppel';
import { renderMitTheme } from '../test/render';

vi.mock('../auth/rechte', () => import('../test/rechteDoppel'));

/**
 * Die Karte „Briefpapier" bei den eigenen Angaben (fachliche Quelle #361; Plan #363, E8, E16,
 * E17).
 *
 * Die Karte laedt selbst — jede Probe legt darum den Leseweg mit fest.
 *
 * Jeder schreibende Weg legt den Stand vor (#411, Kriterien 1 bis 3; Plan #415, E2, E11, E12).
 */

const WEG = '/api/briefpapier';
const LESEN = `GET ${WEG}`;
const PFLEGEN = `PUT ${WEG}`;
const HOCHLADEN = `POST ${WEG}/datei`;
const ENTFERNEN = `DELETE ${WEG}/datei`;
const PROBEDRUCK = `GET ${WEG}/probedruck`;

const OHNE_PAPIER = {
  briefpapier: false,
  dateiname: null,
  groesse: null,
  seiten: null,
  absenderzeile: true,
  steuerBankZeile: true,
  links: 250,
  rechts: 200,
  inhaltOben: 985,
  unten: 200,
  folgeOben: 200,
  folgeUnten: 200,
  version: 3,
};

const MIT_PAPIER = {
  ...OHNE_PAPIER,
  briefpapier: true,
  dateiname: 'Briefbogen.pdf',
  groesse: 2048,
  seiten: 2,
};

const LAEDT = 'Das Briefpapier wird geladen …';
const AUSFALL_LESEN =
  'Das Briefpapier ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const OHNE = 'Noch kein Briefpapier hinterlegt. Rechnungen tragen die Gestaltung von fb.crm.';
const ZU_GROSS = 'Das Briefpapier darf höchstens 5 MB groß sein.';
const AUSFALL_HOCHLADEN =
  'Das Briefpapier wurde nicht hochgeladen. Bitte später erneut versuchen.';
const AUSFALL_ENTFERNEN =
  'Das Briefpapier wurde nicht entfernt. Bitte später erneut versuchen.';
const AUSFALL_SPEICHERN =
  'Die Einstellungen wurden nicht gespeichert. Bitte später erneut versuchen.';
const AUSFALL_PROBEDRUCK =
  'Der Probedruck ist gerade nicht zu erzeugen. Bitte später erneut versuchen.';
const GESPEICHERT = 'Die Einstellungen sind gespeichert.';
const KEIN_MASS = 'Bitte einen Wert in Millimetern mit höchstens einer Nachkommastelle angeben.';
const SCHALTER_STEUER = 'Steuer- und Bankzeile eindrucken';
const SCHALTER_ABSENDER = 'Absenderzeile eindrucken';

/** 5 MB, binaer gerechnet (E17). */
const GRENZE = 5242880;

/** Heute um die genannte Uhrzeit in der Ortszeit — die Meldung nennt dann nur die Uhrzeit. */
function heuteUm(stunde: number, minute: number): string {
  const zeitpunkt = new Date();
  zeitpunkt.setHours(stunde, minute, 0, 0);
  return zeitpunkt.toISOString();
}

/** Die Abweisung des Servers, wie `GlobalExceptionHandler` sie schreibt (Plan #415, E4). */
function abweisung(geaendertVon: string): () => Response {
  return json(409, {
    title: 'Conflict',
    status: 409,
    detail: 'Dieser Eintrag wurde inzwischen geändert.',
    konflikt: { art: 'veraltet', geaendertAm: heuteUm(10, 42), eigenesFenster: false, geaendertVon },
  });
}

/** Antwortet der Reihe nach mit den Antworten — beim ersten Aufruf mit der ersten. */
function nacheinander(...antworten: readonly (() => Response)[]): () => Response {
  let aufruf = 0;
  return () => {
    const antwort = antworten[Math.min(aufruf, antworten.length - 1)];
    aufruf += 1;
    return antwort();
  };
}

/** Die Staende, die die schreibenden Aufrufe als `If-Match` vorgelegt haben, in ihrer Reihenfolge. */
function vorgelegteStaende(fetchMock: ReturnType<typeof fetchNachPfad>): readonly unknown[] {
  return fetchMock.mock.calls
    .filter(([, init]) => init?.method !== undefined && init.method !== 'GET')
    .map(([, init]) => (init?.headers as Record<string, string> | undefined)?.['If-Match']);
}

function renderKarte() {
  return renderMitTheme(<BriefbogenKarte />);
}

function dateifeld() {
  return screen.getByLabelText('Briefpapier-PDF auswählen');
}

function rand(name: string) {
  return screen.getByRole('textbox', { name });
}

function hinweis() {
  return screen.queryByText(/lässt sich die Zeile mit dem Schalter/);
}

/** Eine Datei mit vorgegebener Groesse — die Vorpruefung liest allein `size`. */
function dateiMitGroesse(name: string, groesse: number): File {
  const datei = new File(['%PDF'], name, { type: 'application/pdf' });
  Object.defineProperty(datei, 'size', { value: groesse });
  return datei;
}

function objektUrlDoppel() {
  const erzeuge = vi.fn<(objekt: Blob) => string>(() => 'blob:fbcrm/probedruck');
  const gebeFrei = vi.fn<(url: string) => void>();
  Object.defineProperty(URL, 'createObjectURL', { value: erzeuge, configurable: true });
  Object.defineProperty(URL, 'revokeObjectURL', { value: gebeFrei, configurable: true });
  return { erzeuge, gebeFrei };
}

let objektUrl: ReturnType<typeof objektUrlDoppel>;

beforeEach(() => {
  objektUrl = objektUrlDoppel();
});

afterEach(() => {
  vi.restoreAllMocks();
});

afterAll(() => {
  Reflect.deleteProperty(URL, 'createObjectURL');
  Reflect.deleteProperty(URL, 'revokeObjectURL');
});

describe('BriefbogenKarte — der Stand', () => {
  it('zeigt waehrend des Ladens einen Hinweis', () => {
    fetchNachPfad({ [LESEN]: json(200, OHNE_PAPIER) });

    renderKarte();

    expect(screen.getByRole('heading', { name: 'Briefpapier', level: 2 })).toBeInTheDocument();
    expect(screen.getByText(LAEDT)).toBeInTheDocument();
  });

  it('schreibt nach dem Ausbau nichts mehr, ob die Antwort ankommt oder ausfaellt', async () => {
    let liefere!: (antwort: Response) => void;
    const angehalten = new Promise<Response>((aufloesen) => {
      liefere = aufloesen;
    });
    const fetchMock = fetchNachPfad({ [LESEN]: () => angehalten });
    renderKarte().unmount();
    liefere(json(200, OHNE_PAPIER)());
    await vi.waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    let scheitere!: (grund: Error) => void;
    const abgebrochen = new Promise<Response>((_aufloesen, ablehnen) => {
      scheitere = ablehnen;
    });
    fetchMock.mockImplementation(() => abgebrochen);
    renderKarte().unmount();
    scheitere(new Error('Netz weg'));
    await expect(abgebrochen).rejects.toThrow('Netz weg');
    expect(screen.queryByText(AUSFALL_LESEN)).not.toBeInTheDocument();
  });

  it('meldet den Ausfall des Ladens', async () => {
    fetchNachPfad({ [LESEN]: leer(503) });

    renderKarte();

    expect(await screen.findByText(AUSFALL_LESEN)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Briefpapier hochladen' })).not.toBeInTheDocument();
  });

  it('zeigt ohne Briefpapier den Hinweis darauf, keine Entfernen-Taste und keinen Probedruck', async () => {
    fetchNachPfad({ [LESEN]: json(200, OHNE_PAPIER) });

    renderKarte();

    expect(await screen.findByText(OHNE)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Briefpapier hochladen' })).toBeEnabled();
    expect(screen.queryByRole('button', { name: 'Probedruck' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Briefpapier entfernen' })).not.toBeInTheDocument();
  });

  it('zeigt mit Briefpapier Dateiname, Groesse und Seitenzahl und bietet Ersetzen, Entfernen und Probedruck', async () => {
    fetchNachPfad({ [LESEN]: json(200, MIT_PAPIER) });

    renderKarte();

    const papier = await screen.findByTestId('briefpapier');
    expect(within(papier).getByText('Briefbogen.pdf')).toBeInTheDocument();
    expect(within(papier).getByText('2 KB')).toBeInTheDocument();
    expect(within(papier).getByText('2 Seiten')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Briefpapier ersetzen' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Briefpapier entfernen' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Probedruck' })).toBeInTheDocument();
  });

  it('sagt „1 Seite" bei einem einseitigen Briefpapier', async () => {
    fetchNachPfad({ [LESEN]: json(200, { ...MIT_PAPIER, seiten: 1 }) });

    renderKarte();

    expect(await screen.findByText('1 Seite')).toBeInTheDocument();
  });

  it('zeigt die Schalter und die Raender in Millimetern mit einer Nachkommastelle — 985 als 98,5', async () => {
    fetchNachPfad({ [LESEN]: json(200, { ...OHNE_PAPIER, absenderzeile: false }) });

    renderKarte();

    expect(await screen.findByRole('checkbox', { name: SCHALTER_ABSENDER })).not.toBeChecked();
    expect(screen.getByRole('checkbox', { name: SCHALTER_STEUER })).toBeChecked();
    expect(rand('Linker Rand (mm)')).toHaveValue('25,0');
    expect(rand('Rechter Rand (mm)')).toHaveValue('20,0');
    expect(rand('Beginn des Inhalts auf Seite 1 (mm)')).toHaveValue('98,5');
    expect(rand('Unterer Rand auf Seite 1 (mm)')).toHaveValue('20,0');
    expect(rand('Oberer Rand auf Folgeseiten (mm)')).toHaveValue('20,0');
    expect(rand('Unterer Rand auf Folgeseiten (mm)')).toHaveValue('20,0');
  });
});

describe('BriefbogenKarte — Hochladen, Ersetzen, Entfernen', () => {
  it('laedt hoch, zeigt den neuen Stand und bei eingeschalteter Steuer- und Bankzeile den Hinweis mit dem Schalter (E16)', async () => {
    const formulare: FormData[] = [];
    fetchNachPfad({
      [LESEN]: json(200, OHNE_PAPIER),
      [HOCHLADEN]: formularWeg((f) => formulare.push(f), json(200, MIT_PAPIER)),
    });
    const nutzer = userEvent.setup();
    renderKarte();
    await screen.findByText(OHNE);
    expect(hinweis()).not.toBeInTheDocument();

    await nutzer.upload(dateifeld(), dateiMitGroesse('Briefbogen.pdf', 2048));

    expect(await screen.findByTestId('briefpapier')).toHaveTextContent('Briefbogen.pdf');
    expect(formulare).toHaveLength(1);
    expect(hinweis()).toBeInTheDocument();
    expect(hinweis()).toHaveTextContent(`„${SCHALTER_STEUER}“`);
    expect(screen.getByRole('button', { name: 'Probedruck' })).toBeInTheDocument();
  });

  it('zeigt den Hinweis nach dem Hochladen nicht, wenn die Steuer- und Bankzeile aus ist', async () => {
    const aus = { ...OHNE_PAPIER, steuerBankZeile: false };
    fetchNachPfad({
      [LESEN]: json(200, aus),
      [HOCHLADEN]: json(200, { ...MIT_PAPIER, steuerBankZeile: false }),
    });
    const nutzer = userEvent.setup();
    renderKarte();
    await screen.findByText(OHNE);

    await nutzer.upload(dateifeld(), dateiMitGroesse('Briefbogen.pdf', 2048));

    expect(await screen.findByTestId('briefpapier')).toBeInTheDocument();
    expect(hinweis()).not.toBeInTheDocument();
  });

  it('weist eine Datei ueber 5 MB ohne Netzaufruf mit der eigenen Meldung ab (E17)', async () => {
    const fetchMock = fetchNachPfad({ [LESEN]: json(200, OHNE_PAPIER) });
    const nutzer = userEvent.setup();
    renderKarte();
    await screen.findByText(OHNE);

    await nutzer.upload(dateifeld(), dateiMitGroesse('gross.pdf', GRENZE + 1));

    expect(await screen.findByText(ZU_GROSS)).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toBe(WEG);
  });

  it('laesst eine Datei von genau 5 MB hinaus', async () => {
    const fetchMock = fetchNachPfad({
      [LESEN]: json(200, OHNE_PAPIER),
      [HOCHLADEN]: json(200, MIT_PAPIER),
    });
    const nutzer = userEvent.setup();
    renderKarte();
    await screen.findByText(OHNE);

    await nutzer.upload(dateifeld(), dateiMitGroesse('grenze.pdf', GRENZE));

    expect(await screen.findByTestId('briefpapier')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(screen.queryByText(ZU_GROSS)).not.toBeInTheDocument();
  });

  it('zeigt die Meldung des Servers zu „datei" und laesst das bisherige Briefpapier stehen', async () => {
    fetchNachPfad({
      [LESEN]: json(200, MIT_PAPIER),
      [HOCHLADEN]: problem(422, 'Ungültige Eingabe', {
        datei: ['Das Briefpapier muss A4 hochkant sein.'],
      }),
    });
    const nutzer = userEvent.setup();
    renderKarte();
    await screen.findByTestId('briefpapier');

    await nutzer.upload(dateifeld(), dateiMitGroesse('quer.pdf', 2048));

    expect(await screen.findByText('Das Briefpapier muss A4 hochkant sein.')).toBeInTheDocument();
    expect(screen.getByTestId('briefpapier')).toHaveTextContent('Briefbogen.pdf');
    expect(hinweis()).not.toBeInTheDocument();
  });

  it('meldet einen Ausfall beim Hochladen ohne Feldmeldung mit dem eigenen Satz', async () => {
    fetchNachPfad({ [LESEN]: json(200, OHNE_PAPIER), [HOCHLADEN]: leer(503) });
    const nutzer = userEvent.setup();
    renderKarte();
    await screen.findByText(OHNE);

    await nutzer.upload(dateifeld(), dateiMitGroesse('a.pdf', 2048));

    expect(await screen.findByText(AUSFALL_HOCHLADEN)).toBeInTheDocument();
  });

  it('ersetzt ein hinterlegtes Briefpapier ueber die Taste „Briefpapier ersetzen"', async () => {
    fetchNachPfad({
      [LESEN]: json(200, MIT_PAPIER),
      [HOCHLADEN]: json(200, { ...MIT_PAPIER, dateiname: 'Neu.pdf', seiten: 1 }),
    });
    const nutzer = userEvent.setup();
    renderKarte();
    await screen.findByTestId('briefpapier');
    const klick = vi.spyOn(HTMLInputElement.prototype, 'click');

    await nutzer.click(screen.getByRole('button', { name: 'Briefpapier ersetzen' }));
    expect(klick).toHaveBeenCalled();
    await nutzer.upload(dateifeld(), dateiMitGroesse('Neu.pdf', 1024));

    expect(await screen.findByText('Neu.pdf')).toBeInTheDocument();
    expect(screen.getByText('1 Seite')).toBeInTheDocument();
  });

  it('tut nichts, wenn die Dateiwahl ohne Datei endet', async () => {
    const fetchMock = fetchNachPfad({ [LESEN]: json(200, OHNE_PAPIER) });
    renderKarte();
    await screen.findByText(OHNE);

    // So sieht der Wechsel aus, wenn der Dateidialog ohne Auswahl geschlossen wird.
    fireEvent.change(dateifeld(), { target: { files: null } });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(screen.getByText(OHNE)).toBeInTheDocument();
  });

  it('entfernt das Briefpapier; Schalter und Raender bleiben, der Probedruck verschwindet', async () => {
    fetchNachPfad({
      [LESEN]: json(200, MIT_PAPIER),
      [ENTFERNEN]: json(200, OHNE_PAPIER),
    });
    const nutzer = userEvent.setup();
    renderKarte();
    await screen.findByTestId('briefpapier');
    await nutzer.clear(rand('Linker Rand (mm)'));
    await nutzer.type(rand('Linker Rand (mm)'), '30');

    await nutzer.click(screen.getByRole('button', { name: 'Briefpapier entfernen' }));

    expect(await screen.findByText(OHNE)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Probedruck' })).not.toBeInTheDocument();
    // Was der Betreiber gerade eingibt, nimmt das Entfernen nicht mit.
    expect(rand('Linker Rand (mm)')).toHaveValue('30');
  });

  it('meldet einen Ausfall beim Entfernen und laesst das Briefpapier stehen', async () => {
    fetchNachPfad({ [LESEN]: json(200, MIT_PAPIER), [ENTFERNEN]: leer(503) });
    const nutzer = userEvent.setup();
    renderKarte();
    await screen.findByTestId('briefpapier');

    await nutzer.click(screen.getByRole('button', { name: 'Briefpapier entfernen' }));

    expect(await screen.findByText(AUSFALL_ENTFERNEN)).toBeInTheDocument();
    expect(screen.getByTestId('briefpapier')).toBeInTheDocument();
  });
});

describe('BriefbogenKarte — Schalter und Raender', () => {
  it('schickt beide umgelegten Schalter und 98,5 mm als 985 hinaus', async () => {
    const gesendet: unknown[] = [];
    fetchNachPfad({
      [LESEN]: json(200, { ...OHNE_PAPIER, inhaltOben: 1000 }),
      [PFLEGEN]: (rumpf) => {
        gesendet.push(alsJson(rumpf));
        return json(200, {
          ...OHNE_PAPIER,
          absenderzeile: false,
          steuerBankZeile: false,
          links: 300,
        })();
      },
    });
    const nutzer = userEvent.setup();
    renderKarte();
    await screen.findByText(OHNE);

    await nutzer.click(screen.getByRole('checkbox', { name: SCHALTER_ABSENDER }));
    await nutzer.click(screen.getByRole('checkbox', { name: SCHALTER_STEUER }));
    await nutzer.clear(rand('Beginn des Inhalts auf Seite 1 (mm)'));
    await nutzer.type(rand('Beginn des Inhalts auf Seite 1 (mm)'), '98,5');
    await nutzer.clear(rand('Linker Rand (mm)'));
    await nutzer.type(rand('Linker Rand (mm)'), '30');
    await nutzer.clear(rand('Rechter Rand (mm)'));
    await nutzer.type(rand('Rechter Rand (mm)'), ' 20.0 ');
    await nutzer.click(screen.getByRole('button', { name: 'Einstellungen speichern' }));

    expect(await screen.findByText(GESPEICHERT)).toBeInTheDocument();
    expect(gesendet).toEqual([
      {
        absenderzeile: false,
        steuerBankZeile: false,
        links: 300,
        rechts: 200,
        inhaltOben: 985,
        unten: 200,
        folgeOben: 200,
        folgeUnten: 200,
      },
    ]);
    // Der gespeicherte Stand steht danach in den Feldern, in Millimetern.
    expect(rand('Linker Rand (mm)')).toHaveValue('30,0');
    expect(rand('Rechter Rand (mm)')).toHaveValue('20,0');
    expect(screen.getByRole('checkbox', { name: SCHALTER_STEUER })).not.toBeChecked();
  });

  it('zeigt eine 422-Meldung zum Rand an ihrem Feld und laesst die uebrigen Felder unveraendert', async () => {
    const meldung =
      'Bei den übrigen eingestellten Rändern darf dieser Rand höchstens 120,0 mm betragen.';
    fetchNachPfad({
      [LESEN]: json(200, OHNE_PAPIER),
      [PFLEGEN]: problem(422, 'Ungültige Eingabe', { inhaltOben: [meldung] }),
    });
    const nutzer = userEvent.setup();
    renderKarte();
    await screen.findByText(OHNE);
    await nutzer.clear(rand('Beginn des Inhalts auf Seite 1 (mm)'));
    await nutzer.type(rand('Beginn des Inhalts auf Seite 1 (mm)'), '250');

    await nutzer.click(screen.getByRole('button', { name: 'Einstellungen speichern' }));

    const feld = rand('Beginn des Inhalts auf Seite 1 (mm)');
    expect(await screen.findByText(meldung)).toBeInTheDocument();
    expect(feld).toHaveAccessibleDescription(meldung);
    expect(feld).toHaveAttribute('aria-invalid', 'true');
    expect(feld).toHaveValue('250');
    expect(rand('Linker Rand (mm)')).toHaveValue('25,0');
    expect(rand('Linker Rand (mm)')).toHaveAttribute('aria-invalid', 'false');
    expect(rand('Unterer Rand auf Folgeseiten (mm)')).toHaveValue('20,0');
    expect(screen.queryByText(GESPEICHERT)).not.toBeInTheDocument();
  });

  it('weist einen Wert, der kein Mass in Millimetern ist, ohne Netzaufruf am Feld ab', async () => {
    const fetchMock = fetchNachPfad({ [LESEN]: json(200, OHNE_PAPIER) });
    const nutzer = userEvent.setup();
    renderKarte();
    await screen.findByText(OHNE);
    await nutzer.clear(rand('Unterer Rand auf Seite 1 (mm)'));
    await nutzer.type(rand('Unterer Rand auf Seite 1 (mm)'), '12,34');
    await nutzer.clear(rand('Oberer Rand auf Folgeseiten (mm)'));

    await nutzer.click(screen.getByRole('button', { name: 'Einstellungen speichern' }));

    expect(rand('Unterer Rand auf Seite 1 (mm)')).toHaveAccessibleDescription(KEIN_MASS);
    expect(rand('Oberer Rand auf Folgeseiten (mm)')).toHaveAccessibleDescription(KEIN_MASS);
    expect(rand('Linker Rand (mm)')).toHaveAttribute('aria-invalid', 'false');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('meldet einen Ausfall beim Speichern mit dem eigenen Satz', async () => {
    fetchNachPfad({ [LESEN]: json(200, OHNE_PAPIER), [PFLEGEN]: leer(503) });
    const nutzer = userEvent.setup();
    renderKarte();
    await screen.findByText(OHNE);

    await nutzer.click(screen.getByRole('button', { name: 'Einstellungen speichern' }));

    expect(await screen.findByText(AUSFALL_SPEICHERN)).toBeInTheDocument();
  });
});

describe('BriefbogenKarte — Probedruck', () => {
  it('holt den Probedruck und gibt ihn als Probedruck.pdf zum Sichern heraus', async () => {
    fetchNachPfad({
      [LESEN]: json(200, MIT_PAPIER),
      [PROBEDRUCK]: () => new Response('%PDF-probe', { status: 200 }),
    });
    const geklickt: HTMLAnchorElement[] = [];
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      geklickt.push(this);
    });
    const nutzer = userEvent.setup();
    renderKarte();
    await screen.findByTestId('briefpapier');

    await nutzer.click(screen.getByRole('button', { name: 'Probedruck' }));

    await vi.waitFor(() => {
      expect(objektUrl.gebeFrei).toHaveBeenCalledWith('blob:fbcrm/probedruck');
    });
    expect(objektUrl.erzeuge).toHaveBeenCalledTimes(1);
    expect(geklickt).toHaveLength(1);
    expect(geklickt[0].download).toBe('Probedruck.pdf');
    expect(geklickt[0].getAttribute('href')).toBe('blob:fbcrm/probedruck');
  });

  it('meldet einen Ausfall des Probedrucks', async () => {
    fetchNachPfad({ [LESEN]: json(200, MIT_PAPIER), [PROBEDRUCK]: leer(503) });
    const nutzer = userEvent.setup();
    renderKarte();
    await screen.findByTestId('briefpapier');

    await nutzer.click(screen.getByRole('button', { name: 'Probedruck' }));

    expect(await screen.findByText(AUSFALL_PROBEDRUCK)).toBeInTheDocument();
    expect(objektUrl.erzeuge).not.toHaveBeenCalled();
  });
});

describe('BriefbogenKarte — je Rolle (Kriterien 9 und 10 von #409, Plan #414, A10, E1)', () => {
  it('zeigt ReadOnly das hinterlegte Briefpapier nur mit „Probedruck" (E4)', async () => {
    alsRolle('READ_ONLY');
    fetchNachPfad({ [LESEN]: json(200, MIT_PAPIER) });

    renderKarte();

    const papier = within(await screen.findByTestId('briefpapier'));
    expect(papier.getByText('Briefbogen.pdf')).toBeInTheDocument();
    expect(papier.getByRole('button', { name: 'Probedruck' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Briefpapier entfernen' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Briefpapier ersetzen' })).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Einstellungen speichern' }),
    ).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Briefpapier-PDF auswählen')).not.toBeInTheDocument();
  });

  it('bietet ReadOnly ohne Briefpapier kein „Briefpapier hochladen" an', async () => {
    alsRolle('READ_ONLY');
    fetchNachPfad({ [LESEN]: json(200, OHNE_PAPIER) });

    renderKarte();

    expect(await screen.findByText(OHNE)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Briefpapier hochladen' })).not.toBeInTheDocument();
  });

  it('zeigt einem C-Level Ersetzen, Entfernen und „Einstellungen speichern"', async () => {
    alsRolle('C_LEVEL');
    fetchNachPfad({ [LESEN]: json(200, MIT_PAPIER) });

    renderKarte();

    expect(await screen.findByRole('button', { name: 'Briefpapier ersetzen' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Briefpapier entfernen' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Einstellungen speichern' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Probedruck' })).toBeInTheDocument();
  });

  it('zeigt einem C-Level ohne Briefpapier „Briefpapier hochladen"', async () => {
    alsRolle('C_LEVEL');
    fetchNachPfad({ [LESEN]: json(200, OHNE_PAPIER) });

    renderKarte();

    expect(await screen.findByRole('button', { name: 'Briefpapier hochladen' })).toBeInTheDocument();
  });
});

describe('BriefbogenKarte — Stand vorlegen (#411, Plan #415)', () => {
  it('legt beim Speichern nach dem eigenen Hochladen den Stand aus dessen Antwort vor', async () => {
    const fetchMock = fetchNachPfad({
      [LESEN]: json(200, OHNE_PAPIER),
      [HOCHLADEN]: formularWeg(() => undefined, json(200, { ...MIT_PAPIER, version: 4 })),
      [PFLEGEN]: json(200, { ...MIT_PAPIER, links: 300, version: 5 }),
    });
    const nutzer = userEvent.setup();
    renderKarte();
    await screen.findByText(OHNE);

    await nutzer.upload(dateifeld(), dateiMitGroesse('Briefbogen.pdf', 2048));
    await screen.findByTestId('briefpapier');
    await nutzer.clear(rand('Linker Rand (mm)'));
    await nutzer.type(rand('Linker Rand (mm)'), '30');
    await nutzer.click(screen.getByRole('button', { name: 'Einstellungen speichern' }));

    // E11: Das eigene Hochladen erhoeht den Stand; das folgende Speichern legt den neuen vor und
    // wird darum nicht abgewiesen (#411, Kriterium 8).
    expect(await screen.findByText(GESPEICHERT)).toBeInTheDocument();
    expect(vorgelegteStaende(fetchMock)).toEqual(['"3"', '"4"']);
  });

  it('Szenario: abgewiesenes Speichern — die Werte bleiben, Neu laden stellt sie daneben', async () => {
    const annasStand = { ...MIT_PAPIER, links: 280, version: 4 };
    const fetchMock = fetchNachPfad({
      [LESEN]: nacheinander(json(200, OHNE_PAPIER), json(200, annasStand)),
      [PFLEGEN]: nacheinander(abweisung('Anna'), json(200, { ...annasStand, version: 5 })),
    });
    const nutzer = userEvent.setup();
    renderKarte();
    await screen.findByText(OHNE);
    await nutzer.clear(rand('Linker Rand (mm)'));
    await nutzer.type(rand('Linker Rand (mm)'), '30');
    await nutzer.click(screen.getByRole('checkbox', { name: SCHALTER_ABSENDER }));
    await nutzer.click(screen.getByRole('button', { name: 'Einstellungen speichern' }));

    // Abgewiesen: Die Meldung nennt Anna, und die eigenen Werte stehen weiter in den Feldern.
    const meldung = await screen.findByRole('alert');
    expect(meldung).toHaveTextContent('Nicht ausgeführt');
    expect(meldung).toHaveTextContent('Anna hat diesen Eintrag inzwischen geändert (10:42).');
    expect(screen.queryByText(AUSFALL_SPEICHERN)).not.toBeInTheDocument();
    expect(rand('Linker Rand (mm)')).toHaveValue('30');

    // Neu laden: Annas Stand steht in der Karte — samt ihrem Briefpapier —, die eigenen Werte daneben.
    await nutzer.click(screen.getByRole('button', { name: 'Neu laden' }));
    expect(await screen.findByTestId('briefpapier')).toHaveTextContent('Briefbogen.pdf');
    expect(rand('Linker Rand (mm)')).toHaveValue('28,0');
    expect(screen.queryByText('Nicht ausgeführt')).not.toBeInTheDocument();
    const eigene = screen.getByRole('region', { name: 'Deine Eingaben' });
    expect(eigene).toHaveTextContent('Linker Rand (mm)');
    expect(eigene).toHaveTextContent('30');
    // Der umgelegte Schalter steht als Wort neben seiner Beschriftung.
    expect(eigene).toHaveTextContent(new RegExp(`${SCHALTER_ABSENDER}\\s*aus`, 'u'));

    // Auf Annas Stand geht das Speichern durch; vorgelegt wird der neu geladene Stand.
    await nutzer.click(screen.getByRole('button', { name: 'Einstellungen speichern' }));
    expect(await screen.findByText(GESPEICHERT)).toBeInTheDocument();
    expect(vorgelegteStaende(fetchMock)).toEqual(['"3"', '"4"']);

    await nutzer.click(screen.getByRole('button', { name: 'Verwerfen' }));
    expect(screen.queryByRole('region', { name: 'Deine Eingaben' })).not.toBeInTheDocument();
  });

  it('nennt nach einem abgewiesenen Hochladen die gewaehlte Datei bei den eigenen Eingaben', async () => {
    fetchNachPfad({
      [LESEN]: nacheinander(json(200, OHNE_PAPIER), json(200, { ...MIT_PAPIER, version: 4 })),
      [HOCHLADEN]: formularWeg(() => undefined, abweisung('Anna')),
    });
    const nutzer = userEvent.setup();
    renderKarte();
    await screen.findByText(OHNE);

    await nutzer.upload(dateifeld(), dateiMitGroesse('neu.pdf', 2048));

    expect(await screen.findByText(/Anna hat diesen Eintrag inzwischen geändert/u)).toBeInTheDocument();
    expect(screen.queryByText(AUSFALL_HOCHLADEN)).not.toBeInTheDocument();

    await nutzer.click(screen.getByRole('button', { name: 'Neu laden' }));
    const eigene = await screen.findByRole('region', { name: 'Deine Eingaben' });
    expect(eigene).toHaveTextContent('Gewähltes Briefpapier');
    expect(eigene).toHaveTextContent('neu.pdf');
  });

  it('weist ein Entfernen auf altem Stand mit der Meldung ab und laesst das Briefpapier stehen', async () => {
    const fetchMock = fetchNachPfad({
      [LESEN]: json(200, MIT_PAPIER),
      [ENTFERNEN]: abweisung('Anna'),
    });
    const nutzer = userEvent.setup();
    renderKarte();
    await screen.findByTestId('briefpapier');

    await nutzer.click(screen.getByRole('button', { name: 'Briefpapier entfernen' }));

    expect(await screen.findByText(/Anna hat diesen Eintrag inzwischen geändert/u)).toBeInTheDocument();
    expect(screen.queryByText(AUSFALL_ENTFERNEN)).not.toBeInTheDocument();
    expect(screen.getByTestId('briefpapier')).toBeInTheDocument();
    expect(vorgelegteStaende(fetchMock)).toEqual(['"3"']);
  });

  it('meldet einen Ausfall beim Neu laden und laesst die Konfliktmeldung stehen', async () => {
    fetchNachPfad({
      [LESEN]: nacheinander(json(200, OHNE_PAPIER), leer(503)),
      [PFLEGEN]: abweisung('Anna'),
    });
    const nutzer = userEvent.setup();
    renderKarte();
    await screen.findByText(OHNE);
    await nutzer.click(screen.getByRole('button', { name: 'Einstellungen speichern' }));
    await nutzer.click(await screen.findByRole('button', { name: 'Neu laden' }));

    expect(
      await screen.findByText(
        'Der aktuelle Stand ist gerade nicht zu erreichen. Bitte später erneut versuchen.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Neu laden' })).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Deine Eingaben' })).not.toBeInTheDocument();
  });
});
