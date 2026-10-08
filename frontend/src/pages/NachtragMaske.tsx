import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';
import FormHelperText from '@mui/material/FormHelperText';
import Link from '@mui/material/Link';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { IconFileUpload } from '@tabler/icons-react';
import { useEffect, useId, useState } from 'react';
import type { ChangeEvent, Dispatch, FormEvent, ReactNode, SetStateAction } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';

import type { FieldErrors } from '../api/client';
import { firmenUebersicht } from '../api/firmen';
import type { FirmaZeile } from '../api/firmen';
import {
  nachtragAendern,
  nachtragAnlegen,
  nachtragDokumentAblegen,
  nachtragDokumentEntfernen,
  nachtragLesen,
} from '../api/nachtraege';
import type { Nachtrag, NachtragEingabe } from '../api/nachtraege';
import Karte from '../components/Karte';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import KupferTaste from '../components/KupferTaste';
import WeicheTaste from '../components/WeicheTaste';
import { feldMeldungen, nichtGefunden } from '../lib/apifehler';
import { dateigroesse, MAX_UPLOAD_BYTE } from '../lib/dateigroesse';
import { ersteDatei } from '../lib/dateiwahl';
import { meldungAm } from '../lib/feldmeldung';
import { dezimal, hundertstel } from '../lib/geld';
import { kennungAus } from '../lib/kennung';
import { ABSTAND_BUEHNE } from '../theme';

/**
 * Die Maske der nachgetragenen Rechnung — Anlegen unter `/rechnungen/nachtragen`, Aendern unter
 * `/rechnungen/nachgetragen/:id/bearbeiten` (#254, Kriterien 2 und 10; Plan #259, E11, E12, E15,
 * E21).
 *
 * Eine Komponente fuer beide Wege, wie {@link FirmaMaske}: Felder, Meldungen und Verhalten sind
 * dieselben; verschieden ist nur, woher die Werte kommen und welcher Weg speichert.
 *
 * <b>Der Kunde kommt aus der Firmenwahl samt stillgelegten</b> (Kriterium 2): Eine Rechnung, die
 * schon geschrieben ist, ging an eine Firma, die heute stillgelegt sein darf. Eine stillgelegte
 * steht waehlbar in der Wahl und traegt „(stillgelegt)" im Wort — eine native Wahl nimmt nur Text,
 * dieselbe Form wie beim Ansprechpartner der Angebotsmaske. Die Suche fragt den Server mit dem
 * Suchtext; die vorige Suche bricht ab, sobald eine juengere unterwegs ist. Ein schon gewaehlter
 * Kunde bleibt in der Wahl, auch wenn der Suchtext ihn nicht mehr trifft — sonst verloere die Maske
 * still eine Angabe.
 *
 * <b>Angaben und Original gehen in zwei Aufrufen</b> (E11). Scheitert der zweite, steht die
 * Rechnung ohne PDF da: Die Maske bleibt stehen, meldet es am Feld der Datei und merkt sich die
 * Kennung — der naechste Versuch aendert dieselbe Rechnung, statt eine zweite anzulegen. Die
 * nachgetragene Rechnung ist in jedem Zustand aenderbar, also ist das nachzuholen.
 *
 * <b>Groesse und Art der Datei prueft die Maske vor dem Senden</b> (E12, E15) — als Fuehrung, nicht
 * als Sperre: Ob die Datei ein PDF ist, entscheidet der Server am Inhalt. Die Maske liest die vom
 * Browser gemeldete Art; eine HTML-Datei, die `rechnung.pdf` heisst, kommt hier durch und wird am
 * Server abgewiesen, und dessen Meldung steht dann am selben Feld.
 *
 * <b>Das Dateifeld liegt aus dem Blickfeld</b>, wie in {@link Anlagen}: Die weiche Taste „PDF
 * wählen" oeffnet es, und die Meldung zur Datei steht sichtbar darunter und ist ueber
 * `aria-describedby` mit dem Feld verknuepft.
 *
 * Das Formular traegt `noValidate`; die Pflichtangaben stehen als `required` am Feld, die Meldungen
 * dazu schreibt diese Maske — in derselben Sprache und an derselben Stelle wie die des Servers.
 */

const LAEDT = 'Die Angaben werden geladen …';
const NICHT_GEFUNDEN = 'Diese nachgetragene Rechnung gibt es nicht.';
const AUSFALL_LESEN = 'Die Angaben sind gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_SPEICHERN = 'Die Rechnung wurde nicht gespeichert. Bitte später erneut versuchen.';
const AUSFALL_SUCHE = 'Die Suche ist gerade nicht möglich. Bitte später erneut versuchen.';
const NICHTS_GEFUNDEN = 'Zu diesem Suchtext wurde keine Firma gefunden.';
const KUNDE_FEHLT = 'Bitte einen Kunden wählen.';
const NUMMER_FEHLT = 'Bitte eine Rechnungsnummer angeben.';
const DATUM_FEHLT = 'Bitte ein Rechnungsdatum angeben.';
const BETRAG_FALSCH =
  'Bitte einen Betrag mit höchstens zwei Nachkommastellen angeben, etwa 1190,50.';
const BETRAG_HINWEIS = 'In Euro, so wie auf der Rechnung.';
const KEIN_PDF = 'Die Datei ist kein PDF.';
const OHNE_INHALT = 'Die Datei ist leer.';
/** Die Meldung nennt die Grenze in derselben Schreibweise wie die Anlagen am Angebot. */
const ZU_GROSS = `Die Datei darf höchstens ${dateigroesse(MAX_UPLOAD_BYTE)} groß sein.`;
const AUSFALL_DATEI = 'Das PDF wurde nicht abgelegt. Bitte später erneut versuchen.';
const UEBRIGE_GESPEICHERT = 'Die übrigen Angaben sind gespeichert.';
const KEINE_DATEI = 'Keine Datei gewählt.';
const OHNE_ORIGINAL = 'Optional; bisher ist kein PDF hinterlegt.';
const MIT_ORIGINAL = 'Ein PDF ist hinterlegt; eine neue Datei ersetzt es.';

/** Die Art, die der Browser einer PDF-Datei gibt — Gegenstueck zur Inhaltspruefung am Server. */
const PDF_ART = 'application/pdf';

/** Der Wert der Wahl, solange kein Kunde gewaehlt ist. */
const KEIN_KUNDE = '';

/** Das Feld, unter dem die Schnittstelle die Datei fuehrt (`DokumentNichtAnnehmbar`). */
const DATEI = 'datei';

const SYMBOL = 16;

/** Ueber der Maske steht die Rechnungsliste — der fachliche Ort des Nachtragens (Kriterium 1). */
const ZU_RECHNUNGEN: readonly PfadVerweis[] = [{ titel: 'Rechnungen', ziel: '/rechnungen' }];

/**
 * Das Dateifeld aus dem Blickfeld, aber nicht aus dem Baum — dieselbe Form wie in {@link Anlagen}:
 * `display: none` naehme in manchen Browsern keinen Klick mehr entgegen.
 */
const VERSTECKT = {
  position: 'absolute',
  width: '1px',
  height: '1px',
  padding: 0,
  margin: '-1px',
  overflow: 'hidden',
  clipPath: 'inset(50%)',
  whiteSpace: 'nowrap',
  border: 0,
} as const;

/** Die Textfelder der Maske, jedes als Zeichenkette — leer heisst „noch nichts eingegeben". */
interface Werte {
  readonly nummer: string;
  readonly rechnungDatum: string;
  readonly netto: string;
  readonly brutto: string;
}

const NEUE_WERTE: Werte = { nummer: '', rechnungDatum: '', netto: '', brutto: '' };

/** Ein Eintrag der Kundenwahl: die Kennung und das Wort, unter dem sie dort steht. */
interface Wahlzeile {
  readonly id: number;
  readonly wort: string;
}

/** Was die Maske gerade weiss. */
type Stand = 'laedt' | 'bereit' | 'leer' | 'unbekannt' | 'ausfall';

/** Das Ergebnis des ersten Lesens: Firmenwahl und, beim Aendern, die Rechnung — oder ein Ausfall. */
type Geladen =
  | {
      readonly art: 'gelesen';
      readonly firmen: readonly FirmaZeile[];
      readonly gesamt: number;
      readonly nachtrag: Nachtrag | null;
    }
  | { readonly art: 'unbekannt' | 'ausfall' };

/** Das Ergebnis der Vorpruefung: die Eingabe fuer den Server oder die Meldungen je Feld. */
type Pruefung =
  | { readonly eingabe: NachtragEingabe; readonly fehler: null }
  | { readonly eingabe: null; readonly fehler: FieldErrors };

/**
 * Liest Firmenwahl und Rechnung in einem Zug und macht auch aus dem Fehlschlag ein Ergebnis — so
 * gibt es im Effekt genau eine Stelle, die nach dem Ausbau nichts mehr schreibt.
 */
async function laden(kennung: number | null): Promise<Geladen> {
  try {
    const [uebersicht, nachtrag] = await Promise.all([
      firmenUebersicht('', true),
      kennung === null ? null : nachtragLesen(kennung),
    ]);
    return { art: 'gelesen', firmen: uebersicht.firmen, gesamt: uebersicht.gesamt, nachtrag };
  } catch (ursache: unknown) {
    return { art: nichtGefunden(ursache) ? 'unbekannt' : 'ausfall' };
  }
}

/** Die Firmen zum Suchtext, oder `null` fuer einen Ausfall. */
async function suchen(
  suche: string,
  signal: AbortSignal,
): Promise<readonly FirmaZeile[] | null> {
  try {
    return (await firmenUebersicht(suche, true, signal)).firmen;
  } catch {
    return null;
  }
}

/** Die gespeicherten Angaben als Feldinhalte — Geld mit Komma, wie man es eintippt. */
function werteAus(nachtrag: Nachtrag): Werte {
  return {
    nummer: nachtrag.nummer,
    rechnungDatum: nachtrag.rechnungDatum,
    netto: dezimal(nachtrag.nettoInCent, ','),
    brutto: dezimal(nachtrag.bruttoInCent, ','),
  };
}

/** Die Eintraege der Kundenwahl: die Treffer, und davor der gewaehlte, wenn er keiner ist. */
function wahlzeilen(
  treffer: readonly FirmaZeile[],
  gewaehlt: Wahlzeile | null,
): readonly Wahlzeile[] {
  const zeilen = treffer.map((firma) => ({
    id: firma.id,
    wort: firma.aktiv ? firma.name : `${firma.name} (stillgelegt)`,
  }));
  if (gewaehlt === null || zeilen.some((zeile) => zeile.id === gewaehlt.id)) {
    return zeilen;
  }
  return [gewaehlt, ...zeilen];
}

/** Der Satz unter der Kundenwahl: die Feldmeldung vor dem Ausfall der Suche vor der leeren Suche. */
function kundenhinweis(
  meldung: string | undefined,
  sucheAusfall: boolean,
  ohneTreffer: boolean,
): string | undefined {
  if (meldung !== undefined) {
    return meldung;
  }
  if (sucheAusfall) {
    return AUSFALL_SUCHE;
  }
  return ohneTreffer ? NICHTS_GEFUNDEN : undefined;
}

/** Die Meldung zur gewaehlten Datei, wenn sie so nicht hinausgehen darf (E12, E15). */
function dateiPruefen(datei: File): string | null {
  if (datei.type !== PDF_ART) {
    return KEIN_PDF;
  }
  if (datei.size === 0) {
    return OHNE_INHALT;
  }
  return datei.size > MAX_UPLOAD_BYTE ? ZU_GROSS : null;
}

/**
 * Prueft die Angaben vor dem Senden und baut daraus die Eingabe.
 *
 * Jede Pflichtangabe meldet sich an ihrem Feld, alle auf einmal. Ein Betrag geht als Dezimaltext mit
 * Punkt hinaus; gelesen wird er ueber {@link hundertstel} und damit ueber die Ziffern — eine dritte
 * Nachkommastelle wird gemeldet und nicht gerundet (Kriterium 3).
 */
function pruefen(werte: Werte, gewaehlt: Wahlzeile | null, datei: File | null): Pruefung {
  const netto = hundertstel(werte.netto);
  const brutto = hundertstel(werte.brutto);
  const dateiMeldung = datei === null ? null : dateiPruefen(datei);
  const fehler: Record<string, readonly string[]> = {};
  if (gewaehlt === null) {
    fehler.firmaId = [KUNDE_FEHLT];
  }
  if (werte.nummer.trim() === '') {
    fehler.nummer = [NUMMER_FEHLT];
  }
  if (werte.rechnungDatum === '') {
    fehler.rechnungDatum = [DATUM_FEHLT];
  }
  if (netto === null) {
    fehler.netto = [BETRAG_FALSCH];
  }
  if (brutto === null) {
    fehler.brutto = [BETRAG_FALSCH];
  }
  if (dateiMeldung !== null) {
    fehler.datei = [dateiMeldung];
  }
  if (gewaehlt === null || netto === null || brutto === null || Object.keys(fehler).length > 0) {
    return { eingabe: null, fehler };
  }
  return {
    eingabe: {
      firmaId: gewaehlt.id,
      nummer: werte.nummer,
      rechnungDatum: werte.rechnungDatum,
      netto: dezimal(netto, '.'),
      brutto: dezimal(brutto, '.'),
    },
    fehler: null,
  };
}

/** Der zweite Aufruf (E11): das Original ablegen, ersetzen oder entfernen — oder nichts. */
async function originalNachziehen(id: number, datei: File | null, entfernen: boolean) {
  if (datei !== null) {
    await nachtragDokumentAblegen(id, datei);
    return;
  }
  if (entfernen) {
    await nachtragDokumentEntfernen(id);
  }
}

interface FeldProps {
  readonly label: string;
  readonly feld: keyof Werte;
  readonly werte: Werte;
  readonly setzeWerte: Dispatch<SetStateAction<Werte>>;
  readonly meldung: string | undefined;
  readonly hinweis?: string;
  readonly datum?: boolean;
}

/** Ein Pflichtfeld der Maske samt Meldung — viermal derselbe Bau, einmal aufgeschrieben. */
function Feld({ label, feld, werte, setzeWerte, meldung, hinweis, datum = false }: FeldProps) {
  return (
    <TextField
      label={label}
      type={datum ? 'date' : 'text'}
      value={werte[feld]}
      onChange={(ereignis) => {
        setzeWerte((alt) => ({ ...alt, [feld]: ereignis.target.value }));
      }}
      error={meldung !== undefined}
      helperText={meldung ?? hinweis}
      required
      fullWidth
      // Ein `date`-Feld zeigt immer seine Maske, also darf die Beschriftung nie im Feld liegen.
      slotProps={datum ? { inputLabel: { shrink: true } } : undefined}
    />
  );
}

/** Was die Karte zeigt, solange das Formular nicht bereitsteht. */
function nichtBereit(stand: Exclude<Stand, 'bereit'>): ReactNode {
  if (stand === 'laedt') {
    return (
      <Typography
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
      >
        {LAEDT}
      </Typography>
    );
  }
  if (stand === 'leer') {
    return (
      <Typography
        role="status"
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textMatt })}
      >
        Es ist noch keine Firma angelegt.{' '}
        <Link component={RouterLink} to="/firmen/neu" underline="hover">
          Erste Firma anlegen
        </Link>
      </Typography>
    );
  }
  return <Alert severity="error">{stand === 'unbekannt' ? NICHT_GEFUNDEN : AUSFALL_LESEN}</Alert>;
}

export default function NachtragMaske() {
  const { id } = useParams();
  const bearbeiten = id !== undefined;
  const kennung = kennungAus(id);
  const navigate = useNavigate();
  const dateiMeldungId = useId();
  const [stand, setzeStand] = useState<Stand>('laedt');
  const [treffer, setzeTreffer] = useState<readonly FirmaZeile[]>([]);
  // `null` heisst „noch nicht gesucht": Die erste Wahl kommt aus dem Laden, nicht aus der Suche.
  const [suche, setzeSuche] = useState<string | null>(null);
  const [sucheAusfall, setzeSucheAusfall] = useState(false);
  const [gewaehlt, setzeGewaehlt] = useState<Wahlzeile | null>(null);
  const [werte, setzeWerte] = useState<Werte>(NEUE_WERTE);
  const [hinterlegt, setzeHinterlegt] = useState(false);
  const [entfernen, setzeEntfernen] = useState(false);
  const [datei, setzeDatei] = useState<File | null>(null);
  const [feldFehler, setzeFeldFehler] = useState<FieldErrors>({});
  const [fehler, setzeFehler] = useState<string | null>(null);
  const [laeuft, setzeLaeuft] = useState(false);
  // Die Kennung einer Rechnung, die diese Maske schon angelegt hat, deren Original aber scheiterte.
  const [gespeichert, setzeGespeichert] = useState<number | null>(null);
  // Das Dateifeld als Zustand und nicht als `useRef`: Die Taste steht erst, wenn es das Feld gibt,
  // auf das sie klickt — dasselbe Muster wie in {@link Anlagen}.
  const [feld, setzeFeld] = useState<HTMLInputElement | null>(null);

  useEffect(() => {
    if (bearbeiten && kennung === null) {
      // Eine Kennung, die keine ist, geht gar nicht erst ans Netz (kennung.ts).
      setzeStand('unbekannt');
      return;
    }
    let aktuell = true;
    void laden(kennung).then((geladen) => {
      if (!aktuell) {
        return;
      }
      if (geladen.art !== 'gelesen') {
        setzeStand(geladen.art);
        return;
      }
      setzeTreffer(geladen.firmen);
      const { nachtrag } = geladen;
      if (nachtrag !== null) {
        setzeGewaehlt({ id: nachtrag.firmaId, wort: nachtrag.firmaName });
        setzeWerte(werteAus(nachtrag));
        setzeHinterlegt(nachtrag.dokument);
      }
      setzeStand(geladen.gesamt === 0 ? 'leer' : 'bereit');
    });
    return () => {
      aktuell = false;
    };
  }, [bearbeiten, kennung]);

  useEffect(() => {
    if (suche === null) {
      return;
    }
    const steuerung = new AbortController();
    void suchen(suche, steuerung.signal).then((gefunden) => {
      // Abgebrochen heisst: Es gibt bereits eine juengere Suche, und ihre Antwort gilt.
      if (steuerung.signal.aborted) {
        return;
      }
      setzeSucheAusfall(gefunden === null);
      if (gefunden !== null) {
        setzeTreffer(gefunden);
      }
    });
    return () => {
      steuerung.abort();
    };
  }, [suche]);

  const vorhanden = gespeichert ?? kennung;

  const absenden = async () => {
    setzeFehler(null);
    const pruefung = pruefen(werte, gewaehlt, datei);
    setzeFeldFehler(pruefung.fehler ?? {});
    if (pruefung.eingabe === null) {
      return;
    }
    setzeLaeuft(true);
    let gesichert: number;
    try {
      const antwort =
        vorhanden === null
          ? await nachtragAnlegen(pruefung.eingabe)
          : await nachtragAendern(vorhanden, pruefung.eingabe);
      gesichert = antwort.id;
    } catch (ursache: unknown) {
      setzeLaeuft(false);
      const felder = feldMeldungen(ursache);
      if (Object.keys(felder).length > 0) {
        setzeFeldFehler(felder);
      } else {
        setzeFehler(AUSFALL_SPEICHERN);
      }
      return;
    }
    setzeGespeichert(gesichert);
    try {
      await originalNachziehen(gesichert, datei, entfernen);
    } catch (ursache: unknown) {
      // Die Rechnung steht; nur das Original fehlt. Die Meldung gehoert an die Datei (E11).
      setzeLaeuft(false);
      const grund = meldungAm(feldMeldungen(ursache), DATEI) ?? AUSFALL_DATEI;
      setzeFeldFehler({ [DATEI]: [`${grund} ${UEBRIGE_GESPEICHERT}`] });
      return;
    }
    navigate(`/rechnungen/nachgetragen/${String(gesichert)}`, { replace: true });
  };

  const titel = bearbeiten ? 'Nachgetragene Rechnung bearbeiten' : 'Rechnung nachtragen';
  useKopfPfad(ZU_RECHNUNGEN, titel);
  const zurueck = kennung === null ? '/rechnungen' : `/rechnungen/nachgetragen/${String(kennung)}`;
  const zeilen = wahlzeilen(treffer, gewaehlt);
  const kundenMeldung = meldungAm(feldFehler, 'firmaId');
  const dateiMeldung = meldungAm(feldFehler, DATEI);

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        // Aussenabstand und Spalt bringt der Rahmen mit (CLAUDE-design.md, „Rahmen").
        gap: ABSTAND_BUEHNE,
      }}
    >
      <Karte titel={titel} titelEbene={1}>
        {stand === 'bereit' ? (
          <Box
            component="form"
            noValidate
            onSubmit={(ereignis: FormEvent<HTMLFormElement>) => {
              ereignis.preventDefault();
              void absenden();
            }}
            sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}
          >
            {fehler === null ? null : <Alert severity="error">{fehler}</Alert>}
            <TextField
              type="search"
              size="small"
              label="Kunde suchen"
              value={suche ?? ''}
              onChange={(ereignis) => {
                setzeSuche(ereignis.target.value);
              }}
              fullWidth
            />
            <TextField
              select
              label="Kunde"
              value={gewaehlt === null ? KEIN_KUNDE : String(gewaehlt.id)}
              onChange={(ereignis) => {
                const wert = ereignis.target.value;
                setzeGewaehlt(zeilen.find((zeile) => String(zeile.id) === wert) ?? null);
              }}
              error={kundenMeldung !== undefined}
              helperText={kundenhinweis(kundenMeldung, sucheAusfall, treffer.length === 0)}
              required
              fullWidth
              slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
            >
              <option value={KEIN_KUNDE}>— bitte wählen —</option>
              {zeilen.map((zeile) => (
                <option key={zeile.id} value={zeile.id}>
                  {zeile.wort}
                </option>
              ))}
            </TextField>
            <Feld
              label="Rechnungsnummer"
              feld="nummer"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'nummer')}
            />
            <Feld
              label="Rechnungsdatum"
              feld="rechnungDatum"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'rechnungDatum')}
              datum
            />
            <Feld
              label="Nettobetrag"
              feld="netto"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'netto')}
              hinweis={BETRAG_HINWEIS}
            />
            <Feld
              label="Bruttobetrag"
              feld="brutto"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'brutto')}
              hinweis={BETRAG_HINWEIS}
            />
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.75 }}>
              <Typography
                sx={(theme) => ({ fontSize: 13, color: theme.vars.palette.kupferwolke.textMatt })}
              >
                Originalrechnung (PDF)
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, flexWrap: 'wrap' }}>
                <Box
                  component="input"
                  type="file"
                  ref={setzeFeld}
                  accept={`${PDF_ART},.pdf`}
                  aria-label="Originalrechnung (PDF)"
                  aria-describedby={dateiMeldung === undefined ? undefined : dateiMeldungId}
                  tabIndex={-1}
                  onChange={(ereignis: ChangeEvent<HTMLInputElement>) => {
                    setzeDatei(ersteDatei(ereignis.target.files));
                  }}
                  sx={VERSTECKT}
                />
                {feld === null ? null : (
                  <WeicheTaste
                    onClick={() => {
                      feld.click();
                    }}
                    disabled={laeuft}
                    symbol={<IconFileUpload size={SYMBOL} stroke={1.8} />}
                  >
                    PDF wählen
                  </WeicheTaste>
                )}
                <Typography sx={{ fontSize: 13 }}>{datei?.name ?? KEINE_DATEI}</Typography>
              </Box>
              <Typography
                sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textMatt })}
              >
                {hinterlegt ? MIT_ORIGINAL : OHNE_ORIGINAL}
              </Typography>
              {hinterlegt ? (
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={entfernen}
                      onChange={(ereignis) => {
                        setzeEntfernen(ereignis.target.checked);
                      }}
                    />
                  }
                  label="Hinterlegtes PDF entfernen"
                />
              ) : null}
              {dateiMeldung === undefined ? null : (
                <FormHelperText error id={dateiMeldungId}>
                  {dateiMeldung}
                </FormHelperText>
              )}
            </Box>
            {/* Das Tastenpaar der Vorlage: die weiche Taste links vor der Kupfertaste
                (`.aktionen`, Z. 144–148). */}
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, flexWrap: 'wrap' }}>
              <WeicheTaste to={zurueck}>Abbrechen</WeicheTaste>
              <KupferTaste disabled={laeuft}>
                {vorhanden === null ? 'Anlegen' : 'Speichern'}
              </KupferTaste>
            </Box>
          </Box>
        ) : (
          nichtBereit(stand)
        )}
      </Karte>
    </Box>
  );
}
