import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import FormControlLabel from '@mui/material/FormControlLabel';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { IconFileDownload, IconTrash, IconUpload } from '@tabler/icons-react';
import { useEffect, useState } from 'react';
import type { ChangeEvent, ReactNode } from 'react';

import {
  briefbogenLesen,
  briefbogenPflegen,
  briefpapierEntfernen,
  briefpapierHochladen,
  probedruckHolen,
} from '../api/briefbogen';
import type { Briefbogen, BriefbogenEingabe } from '../api/briefbogen';
import type { FieldErrors } from '../api/client';
import { useRechte } from '../auth/rechte';
import { feldMeldungen, istBearbeitungskonflikt } from '../lib/apifehler';
import { dateigroesse } from '../lib/dateigroesse';
import { ersteDatei } from '../lib/dateiwahl';
import { meldungAm } from '../lib/feldmeldung';
import { RADIUS_KLEIN, ZAHLEN_KLASSE } from '../theme';
import EigeneEingaben from './EigeneEingaben';
import type { EigeneEingabe } from './EigeneEingaben';
import Karte from './Karte';
import Konfliktmeldung from './Konfliktmeldung';
import WeicheTaste from './WeicheTaste';

/**
 * Die Karte „Briefpapier" bei den eigenen Angaben (fachliche Quelle Issue 361, Kriterien 1, 2,
 * 6 bis 9, 13 bis 16; Plan Issue 363, E8, E16, E17).
 *
 * <b>Sie laedt selbst</b> — wie {@link Anlagen}: Der Briefbogen hat seinen eigenen Weg
 * (`/api/briefpapier`, im Modul `rechnung`), und die Maske der eigenen Angaben soll ihn nicht
 * mittragen.
 *
 * <b>Zwei Staende.</b> Der gelesene Stand des Servers sagt, ob ein Briefpapier hinterlegt ist; die
 * Feldwerte sagen, was der Betreiber gerade an Schaltern und Raendern einstellt. Hochladen,
 * Ersetzen und Entfernen erneuern nur den ersten — was gerade eingegeben, aber noch nicht
 * gespeichert ist, nehmen sie nicht mit (Kriterium 16). Erst „Einstellungen speichern" schreibt
 * die Feldwerte fort und uebernimmt die Antwort in beide.
 *
 * <b>Millimeter aussen, Zehntel-Millimeter innen</b> (E8): Die Felder zeigen und nehmen Millimeter
 * mit hoechstens einer Nachkommastelle, Komma oder Punkt; hinaus gehen ganze Zehntel. Was sich so
 * nicht lesen laesst, meldet die Karte am Feld und schickt nichts.
 *
 * <b>Die 5-MB-Grenze prueft die Karte vor dem Senden</b> (E17): Oberhalb der Containergrenze
 * antwortete sonst der allgemeine Riegel mit seiner 25-MB-Meldung. Die Schranke bleibt am Server.
 *
 * <b>Der Hinweis nach dem Hochladen kommt aus der Oberflaeche</b> (E16, Kriterium 14): Er steht
 * genau dann, wenn die Antwort die Steuer- und Bankzeile als eingeschaltet fuehrt, und nennt den
 * Schalter. Als Information, nicht als Fehler.
 *
 * <b>Keine Kupfertaste</b>: Die eine der Ansicht ist „Speichern" der eigenen Angaben
 * (CLAUDE-design.md, „Eine Hauptsache je Ansicht").
 *
 * <b>Ohne das Recht, die Einstellungen zu aendern</b>, fehlen Hochladen, Ersetzen, Entfernen und
 * „Einstellungen speichern" samt Dateifeld (Kriterium 10 von #409, Plan #414, A10, E1); der
 * Probedruck bleibt, denn er liest nur (E4).
 *
 * <b>Jeder schreibende Weg legt den Stand vor</b> (#411, Kriterien 1 bis 3; Plan #415, E2, E11,
 * E12): Hochladen, Entfernen und „Einstellungen speichern" schicken die Version des zuletzt
 * gelesenen Stands, und jede Antwort bringt die neue mit. Der Briefbogen traegt <b>eine</b> Version
 * fuer die ganze Zeile: Hat ein anderer inzwischen hochgeladen, wird auch das Speichern der Raender
 * abgewiesen. Dann sagt {@link Konfliktmeldung}, wer wann; die Feldwerte bleiben stehen. „Neu
 * laden" holt den Stand des Servers und stellt die eigenen Werte — samt dem Namen einer gewaehlten,
 * nicht hochgeladenen Datei — daneben in {@link EigeneEingaben}. Ein Briefbogen wird nie geloescht;
 * der Weg zur Uebersicht fuehrt darum auf diese Seite zurueck.
 */

/** 5 MB, binaer gerechnet — das Gegenstueck zu `Briefpapiergrenze.MAX_BYTE` (E17). */
const MAX_BRIEFPAPIER_BYTE = 5242880;

const LAEDT = 'Das Briefpapier wird geladen …';
const AUSFALL_LESEN =
  'Das Briefpapier ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const OHNE = 'Noch kein Briefpapier hinterlegt. Rechnungen tragen die Gestaltung von fb.crm.';
const ZU_GROSS = `Das Briefpapier darf höchstens ${dateigroesse(MAX_BRIEFPAPIER_BYTE)} groß sein.`;
const AUSFALL_HOCHLADEN =
  'Das Briefpapier wurde nicht hochgeladen. Bitte später erneut versuchen.';
const AUSFALL_ENTFERNEN =
  'Das Briefpapier wurde nicht entfernt. Bitte später erneut versuchen.';
const AUSFALL_SPEICHERN =
  'Die Einstellungen wurden nicht gespeichert. Bitte später erneut versuchen.';
const AUSFALL_PROBEDRUCK =
  'Der Probedruck ist gerade nicht zu erzeugen. Bitte später erneut versuchen.';
const GESPEICHERT = 'Die Einstellungen sind gespeichert.';
const AUSFALL_NEU_LADEN =
  'Der aktuelle Stand ist gerade nicht zu erreichen. Bitte später erneut versuchen.';

/** Wohin die Konfliktmeldung fuehrt, wenn es nichts neu zu laden gibt — auf diese Seite. */
const UEBERSICHT = '/eigene-angaben';

/** Die Zeile unter „Deine Eingaben" fuer eine gewaehlte, nicht hochgeladene Datei. */
const GEWAEHLTE_DATEI = 'Gewähltes Briefpapier';
const KEIN_MASS = 'Bitte einen Wert in Millimetern mit höchstens einer Nachkommastelle angeben.';

const SCHALTER_ABSENDER = 'Absenderzeile eindrucken';
const SCHALTER_STEUER = 'Steuer- und Bankzeile eindrucken';
const HINWEIS_STEUER = `Trägt das Briefpapier Steuer- und Bankangaben schon selbst, lässt sich die Zeile mit dem Schalter „${SCHALTER_STEUER}“ abschalten.`;

/** Der zugaengliche Name des Dateifeldes; die sichtbare Taste davor oeffnet es. */
const FELD_NAME = 'Briefpapier-PDF auswählen';

/** Das Feld, unter dem die Schnittstelle die Datei fuehrt (`BriefpapierNichtAnnehmbar.FELD`). */
const FELD_DATEI = 'datei';

/** Der Name, unter dem der Probedruck gesichert wird — wie am Server. */
const PROBEDRUCK_DATEINAME = 'Probedruck.pdf';

const SYMBOL = 16;

/** Die sechs einstellbaren Raender (Kriterium 15). */
type Randfeld = 'links' | 'rechts' | 'inhaltOben' | 'unten' | 'folgeOben' | 'folgeUnten';

const RAENDER: readonly { readonly feld: Randfeld; readonly label: string }[] = [
  { feld: 'links', label: 'Linker Rand (mm)' },
  { feld: 'rechts', label: 'Rechter Rand (mm)' },
  { feld: 'inhaltOben', label: 'Beginn des Inhalts auf Seite 1 (mm)' },
  { feld: 'unten', label: 'Unterer Rand auf Seite 1 (mm)' },
  { feld: 'folgeOben', label: 'Oberer Rand auf Folgeseiten (mm)' },
  { feld: 'folgeUnten', label: 'Unterer Rand auf Folgeseiten (mm)' },
];

/** Die Feldwerte: Schalter als Wahrheitswert, Raender als Text in Millimetern. */
interface Werte {
  readonly absenderzeile: boolean;
  readonly steuerBankZeile: boolean;
  readonly raender: Readonly<Record<Randfeld, string>>;
}

/** Was die Karte ueber ihren Stand weiss. */
type Lesestand = 'laedt' | 'bereit' | 'ausfall';

/** Millimeter mit genau einer Nachkommastelle und Komma — 985 wird „98,5", 250 „25,0". */
function alsMillimeter(zehntel: number): string {
  return `${String(Math.floor(zehntel / 10))},${String(zehntel % 10)}`;
}

/** Ein Mass in Millimetern als ganze Zehntel, oder `null`, wenn es keines ist. */
function ausMillimeter(eingabe: string): number | null {
  const treffer = /^(\d{1,4})(?:[.,](\d))?$/.exec(eingabe.trim());
  if (treffer === null) {
    return null;
  }
  return Number(treffer[1]) * 10 + Number(treffer[2] ?? '0');
}

function alsWerte(stand: Briefbogen): Werte {
  return {
    absenderzeile: stand.absenderzeile,
    steuerBankZeile: stand.steuerBankZeile,
    raender: {
      links: alsMillimeter(stand.links),
      rechts: alsMillimeter(stand.rechts),
      inhaltOben: alsMillimeter(stand.inhaltOben),
      unten: alsMillimeter(stand.unten),
      folgeOben: alsMillimeter(stand.folgeOben),
      folgeUnten: alsMillimeter(stand.folgeUnten),
    },
  };
}

/**
 * Die Feldwerte als Eingabe — oder die Meldungen der Felder, die sich nicht lesen lassen.
 *
 * Gelesen werden alle sechs Felder, auch nach dem ersten Fehler: Der Betreiber soll jede Meldung
 * auf einmal sehen und nicht eine nach der anderen.
 */
function alsEingabe(
  werte: Werte,
): { readonly eingabe: BriefbogenEingabe } | { readonly fehler: FieldErrors } {
  const fehler: Record<string, readonly string[]> = {};
  const lies = (feld: Randfeld): number => {
    const gelesen = ausMillimeter(werte.raender[feld]);
    if (gelesen === null) {
      fehler[feld] = [KEIN_MASS];
      return 0;
    }
    return gelesen;
  };
  const eingabe: BriefbogenEingabe = {
    absenderzeile: werte.absenderzeile,
    steuerBankZeile: werte.steuerBankZeile,
    links: lies('links'),
    rechts: lies('rechts'),
    inhaltOben: lies('inhaltOben'),
    unten: lies('unten'),
    folgeOben: lies('folgeOben'),
    folgeUnten: lies('folgeUnten'),
  };
  return Object.keys(fehler).length > 0 ? { fehler } : { eingabe };
}

/**
 * Was gerade in der Karte steht, als eigene Eingaben zum Kopieren (#411, Kriterium 3) — mit den
 * Beschriftungen der Felder und, wenn das Hochladen abgewiesen wurde, dem Namen der Datei.
 */
function eigeneEingabenAus(werte: Werte, datei: string | null): readonly EigeneEingabe[] {
  const schalter = (an: boolean) => (an ? 'an' : 'aus');
  const zeilen: EigeneEingabe[] = [
    { bezeichnung: SCHALTER_ABSENDER, wert: schalter(werte.absenderzeile) },
    { bezeichnung: SCHALTER_STEUER, wert: schalter(werte.steuerBankZeile) },
    ...RAENDER.map(({ feld, label }) => ({ bezeichnung: label, wert: werte.raender[feld] })),
  ];
  return datei === null ? zeilen : [...zeilen, { bezeichnung: GEWAEHLTE_DATEI, wert: datei }];
}

function seitenWort(seiten: number): string {
  return seiten === 1 ? '1 Seite' : `${String(seiten)} Seiten`;
}

/**
 * Das Dateifeld aus dem Blickfeld, aber nicht aus dem Baum — wie in {@link Anlagen}: Erreichbar
 * ist es ueber die sichtbare Taste.
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

/** Gibt den Probedruck als Datei zum Sichern heraus und gibt die Objekt-URL gleich wieder frei. */
function sichern(inhalt: Blob) {
  const url = URL.createObjectURL(inhalt);
  const anker = document.createElement('a');
  anker.href = url;
  anker.download = PROBEDRUCK_DATEINAME;
  anker.click();
  URL.revokeObjectURL(url);
}

export default function BriefbogenKarte() {
  const { darfEinstellungenAendern } = useRechte();
  const [lesestand, setzeLesestand] = useState<Lesestand>('laedt');
  const [stand, setzeStand] = useState<Briefbogen | null>(null);
  const [werte, setzeWerte] = useState<Werte | null>(null);
  const [feldFehler, setzeFeldFehler] = useState<FieldErrors>({});
  const [meldung, setzeMeldung] = useState<string | null>(null);
  const [erfolg, setzeErfolg] = useState(false);
  const [hinweis, setzeHinweis] = useState(false);
  const [laeuft, setzeLaeuft] = useState(false);
  // Das Dateifeld als Zustand und nicht als `useRef` — dasselbe Muster wie in {@link Anlagen}.
  const [feld, setzeFeld] = useState<HTMLInputElement | null>(null);
  // Die Abweisung eines Schritts auf altem Stand, ungeprueft — die Konfliktmeldung liest sie.
  const [abweisung, setzeAbweisung] = useState<unknown>(null);
  // Der Name einer Datei, deren Hochladen abgewiesen wurde — sie gehoert zu den eigenen Eingaben.
  const [abgewieseneDatei, setzeAbgewieseneDatei] = useState<string | null>(null);
  // Die eigenen Werte nach „Neu laden", bis „Verwerfen" gewaehlt wird (#411, Kriterium 3).
  const [eigene, setzeEigene] = useState<readonly EigeneEingabe[] | null>(null);

  useEffect(() => {
    let aktuell = true;
    briefbogenLesen()
      .then((gelesen) => {
        if (aktuell) {
          setzeStand(gelesen);
          setzeWerte(alsWerte(gelesen));
          setzeLesestand('bereit');
        }
      })
      .catch(() => {
        if (aktuell) {
          setzeLesestand('ausfall');
        }
      });
    return () => {
      aktuell = false;
    };
  }, []);

  /** Raeumt die Meldungen vor jeder Aktion ab. */
  const neuBeginnen = () => {
    setzeMeldung(null);
    setzeErfolg(false);
    setzeHinweis(false);
    setzeAbweisung(null);
    setzeAbgewieseneDatei(null);
  };

  const hochladen = async (version: number, datei: File) => {
    setzeLaeuft(true);
    try {
      const neu = await briefpapierHochladen(version, datei);
      setzeStand(neu);
      setzeHinweis(neu.steuerBankZeile);
    } catch (ursache: unknown) {
      if (istBearbeitungskonflikt(ursache)) {
        setzeAbweisung(ursache);
        setzeAbgewieseneDatei(datei.name);
      } else {
        setzeMeldung(meldungAm(feldMeldungen(ursache), FELD_DATEI) ?? AUSFALL_HOCHLADEN);
      }
    } finally {
      setzeLaeuft(false);
    }
  };

  const beiWahl = (version: number) => (ereignis: ChangeEvent<HTMLInputElement>) => {
    const datei = ersteDatei(ereignis.target.files);
    // Geleert, damit dieselbe Datei ein zweites Mal gewaehlt werden kann.
    ereignis.target.value = '';
    neuBeginnen();
    if (datei === null) {
      return;
    }
    if (datei.size > MAX_BRIEFPAPIER_BYTE) {
      setzeMeldung(ZU_GROSS);
      return;
    }
    void hochladen(version, datei);
  };

  const entfernen = (version: number) => async () => {
    neuBeginnen();
    setzeLaeuft(true);
    try {
      setzeStand(await briefpapierEntfernen(version));
    } catch (ursache: unknown) {
      if (istBearbeitungskonflikt(ursache)) {
        setzeAbweisung(ursache);
      } else {
        setzeMeldung(AUSFALL_ENTFERNEN);
      }
    } finally {
      setzeLaeuft(false);
    }
  };

  const probedruck = async () => {
    neuBeginnen();
    setzeLaeuft(true);
    try {
      sichern(await probedruckHolen());
    } catch {
      setzeMeldung(AUSFALL_PROBEDRUCK);
    } finally {
      setzeLaeuft(false);
    }
  };

  const speichern = async (version: number, aktuell: Werte) => {
    neuBeginnen();
    const gelesen = alsEingabe(aktuell);
    if ('fehler' in gelesen) {
      setzeFeldFehler(gelesen.fehler);
      return;
    }
    setzeFeldFehler({});
    setzeLaeuft(true);
    try {
      const neu = await briefbogenPflegen(version, gelesen.eingabe);
      setzeStand(neu);
      setzeWerte(alsWerte(neu));
      setzeErfolg(true);
    } catch (ursache: unknown) {
      const felder = feldMeldungen(ursache);
      if (istBearbeitungskonflikt(ursache)) {
        // Die Feldwerte bleiben stehen; den Weg weiter zeigt die Konfliktmeldung (Kriterium 3).
        setzeAbweisung(ursache);
      } else if (Object.keys(felder).length > 0) {
        setzeFeldFehler(felder);
      } else {
        setzeMeldung(AUSFALL_SPEICHERN);
      }
    } finally {
      setzeLaeuft(false);
    }
  };

  /**
   * „Neu laden" nach einer Abweisung: der Stand des Servers in die Karte, die eigenen Werte daneben
   * (#411, Kriterium 3; Plan #415, E12). Genommen wird, was in diesem Augenblick in den Feldern
   * steht — auch was nach der Abweisung noch eingestellt wurde.
   */
  const neuLaden = async (aktuell: Werte) => {
    const meine = eigeneEingabenAus(aktuell, abgewieseneDatei);
    setzeMeldung(null);
    setzeLaeuft(true);
    try {
      const gelesen = await briefbogenLesen();
      setzeStand(gelesen);
      setzeWerte(alsWerte(gelesen));
      setzeEigene(meine);
      setzeAbweisung(null);
      setzeAbgewieseneDatei(null);
      setzeFeldFehler({});
    } catch {
      setzeMeldung(AUSFALL_NEU_LADEN);
    } finally {
      setzeLaeuft(false);
    }
  };

  const bereit = lesestand === 'bereit' && stand !== null && werte !== null;

  const werkzeug = bereit && darfEinstellungenAendern ? (
    <>
      <Box
        component="input"
        type="file"
        accept="application/pdf"
        ref={setzeFeld}
        aria-label={FELD_NAME}
        tabIndex={-1}
        onChange={beiWahl(stand.version)}
        sx={VERSTECKT}
      />
      {feld === null ? null : (
        <WeicheTaste
          onClick={() => {
            feld.click();
          }}
          disabled={laeuft}
          symbol={<IconUpload size={SYMBOL} stroke={1.8} />}
        >
          {stand.papier === null ? 'Briefpapier hochladen' : 'Briefpapier ersetzen'}
        </WeicheTaste>
      )}
    </>
  ) : undefined;

  let inhalt: ReactNode;
  if (lesestand === 'ausfall') {
    inhalt = <Alert severity="error">{AUSFALL_LESEN}</Alert>;
  } else if (!bereit) {
    inhalt = (
      <Typography
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
      >
        {LAEDT}
      </Typography>
    );
  } else {
    inhalt = (
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {meldung === null ? null : <Alert severity="error">{meldung}</Alert>}
        {hinweis ? <Alert severity="info">{HINWEIS_STEUER}</Alert> : null}
        {erfolg ? <Alert severity="success">{GESPEICHERT}</Alert> : null}
        <Konfliktmeldung
          ursache={abweisung}
          onNeuLaden={() => {
            void neuLaden(werte);
          }}
          uebersicht={UEBERSICHT}
        />
        {eigene === null ? null : (
          <EigeneEingaben
            werte={eigene}
            onVerwerfen={() => {
              setzeEigene(null);
            }}
          />
        )}
        {papierZeile(
          stand,
          laeuft,
          darfEinstellungenAendern ? entfernen(stand.version) : null,
          probedruck,
        )}
        <Box sx={{ display: 'flex', flexWrap: 'wrap', columnGap: 3 }}>
          <FormControlLabel
            control={
              <Switch
                checked={werte.absenderzeile}
                onChange={(ereignis) => {
                  setzeWerte({ ...werte, absenderzeile: ereignis.target.checked });
                }}
              />
            }
            label={SCHALTER_ABSENDER}
          />
          <FormControlLabel
            control={
              <Switch
                checked={werte.steuerBankZeile}
                onChange={(ereignis) => {
                  setzeWerte({ ...werte, steuerBankZeile: ereignis.target.checked });
                }}
              />
            }
            label={SCHALTER_STEUER}
          />
        </Box>
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
            gap: 2,
          }}
        >
          {RAENDER.map(({ feld: randfeld, label }) => {
            const randMeldung = meldungAm(feldFehler, randfeld);
            return (
              <TextField
                key={randfeld}
                label={label}
                value={werte.raender[randfeld]}
                onChange={(ereignis) => {
                  setzeWerte({
                    ...werte,
                    raender: { ...werte.raender, [randfeld]: ereignis.target.value },
                  });
                }}
                error={randMeldung !== undefined}
                helperText={randMeldung}
                slotProps={{ htmlInput: { inputMode: 'decimal', className: ZAHLEN_KLASSE } }}
              />
            );
          })}
        </Box>
        {darfEinstellungenAendern ? (
          <Box>
            <WeicheTaste
              onClick={() => {
                void speichern(stand.version, werte);
              }}
              disabled={laeuft}
            >
              Einstellungen speichern
            </WeicheTaste>
          </Box>
        ) : null}
      </Box>
    );
  }

  return (
    <Karte titel="Briefpapier" werkzeug={werkzeug}>
      {inhalt}
    </Karte>
  );
}

/**
 * Der Stand des hinterlegten PDF samt Entfernen und Probedruck — oder der Satz, dass es fehlt.
 * Ohne `entfernen` (kein Recht zum Aendern) steht nur der Probedruck da.
 */
function papierZeile(
  stand: Briefbogen,
  laeuft: boolean,
  entfernen: (() => Promise<void>) | null,
  probedruck: () => Promise<void>,
): ReactNode {
  const { papier } = stand;
  if (papier === null) {
    return (
      <Typography
        role="status"
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textMatt })}
      >
        {OHNE}
      </Typography>
    );
  }
  return (
    <Box
      data-testid="briefpapier"
      sx={(theme) => ({
        display: 'flex',
        alignItems: 'center',
        gap: '12px',
        flexWrap: 'wrap',
        padding: '8px 12px 8px 16px',
        borderRadius: `${String(RADIUS_KLEIN)}px`,
        background: theme.vars.palette.kupferwolke.flaecheWeich,
      })}
    >
      <Typography
        sx={{ fontSize: 13.5, fontWeight: 500, flex: '1 1 160px', wordBreak: 'break-word' }}
      >
        {papier.dateiname}
      </Typography>
      {[dateigroesse(papier.groesse), seitenWort(papier.seiten)].map((angabe) => (
        <Typography
          key={angabe}
          className={ZAHLEN_KLASSE}
          sx={(theme) => ({
            fontSize: 12.5,
            whiteSpace: 'nowrap',
            color: theme.vars.palette.kupferwolke.textSchwach,
          })}
        >
          {angabe}
        </Typography>
      ))}
      <WeicheTaste
        onClick={() => {
          void probedruck();
        }}
        disabled={laeuft}
        symbol={<IconFileDownload size={SYMBOL} stroke={1.8} />}
      >
        Probedruck
      </WeicheTaste>
      {entfernen === null ? null : (
        <WeicheTaste
          onClick={() => {
            void entfernen();
          }}
          disabled={laeuft}
          symbol={<IconTrash size={SYMBOL} stroke={1.8} />}
        >
          Briefpapier entfernen
        </WeicheTaste>
      )}
    </Box>
  );
}
