import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterAll, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import BriefbogenKarte from './BriefbogenKarte';
import { alsJson, fetchNachPfad, formularWeg, json, leer, problem } from '../test/fetchNachPfad';
import { renderMitTheme } from '../test/render';

/**
 * Die Karte „Briefpapier" bei den eigenen Angaben (fachliche Quelle #361; Plan #363, E8, E16,
 * E17).
 *
 * Die Karte laedt selbst — jede Probe legt darum den Leseweg mit fest.
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
