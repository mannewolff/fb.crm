import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { IconDownload, IconEye, IconTrash, IconUpload } from '@tabler/icons-react';
import { useEffect, useState } from 'react';
import type { ChangeEvent, ReactNode } from 'react';

import { anlageHochladen, anlageInhaltPfad, anlageLoeschen, anlagenLesen } from '../api/anlagen';
import type { Anlage } from '../api/anlagen';
import { feldMeldungen } from '../lib/apifehler';
import { dateigroesse, MAX_UPLOAD_BYTE } from '../lib/dateigroesse';
import { ersteDatei } from '../lib/dateiwahl';
import { meldungAm } from '../lib/feldmeldung';
import { zeitpunktWort } from '../lib/zeitpunkt';
import { ICONTASTE, RADIUS_KLEIN, RADIUS_RUND, ZAHLEN_KLASSE } from '../theme';
import AktionsMenue from './AktionsMenue';
import AnlageVorschau from './AnlageVorschau';
import type { AnlageMitVorschau } from './AnlageVorschau';
import Karte from './Karte';
import WeicheTaste from './WeicheTaste';

/**
 * Der Bereich „Anlagen" am Angebot (Issue #148, Kriterien 1 bis 14; Plan #150, E11).
 *
 * <b>Er laedt selbst</b> — wie {@link Kommentare} und aus demselben Grund: `AngebotResponse` traegt
 * die Anlagen nicht, und ein Statuswechsel der Angebotsansicht soll den Bereich nicht neu laden.
 *
 * <b>Eine Datei je Hochladen.</b> Das Dateifeld traegt kein `multiple`, weil kein Weg dieser
 * Anwendung mehrere Teile entgegennimmt (`AngebotAnlagenController`). Die gewaehlte Datei geht
 * sofort hinaus; es gibt keinen zweiten Schritt, in dem sie noch daliegt.
 *
 * <b>Keine Kupfertaste.</b> Hochgeladen wird mit einer weichen Taste: Die eine Kupfertaste der
 * Angebotsansicht ist „Status weiter" (CLAUDE-design.md, „Tasten").
 *
 * <b>Die Taste oeffnet das Feld, das Feld traegt den Namen.</b> Ein `<input type="file">` laesst
 * sich nicht als Kupferwolken-Taste gestalten, ohne seine eigene Gestalt zu verstecken — also
 * liegt es aus dem Blickfeld und ausserhalb der Tab-Folge, und die Taste ruft `click()` darauf.
 * Damit gibt es genau einen Haltepunkt fuer die Tastatur, und er traegt eine sichtbare Aufschrift.
 *
 * <b>Vorgeprueft wird vor dem Senden</b> (Kriterien 5, 6): Eine Datei ohne Byte und eine ueber der
 * Grenze ergeben eine Meldung, und es geht nichts hinaus. Die Schranke bleibt trotzdem im Backend
 * — die Oberflaeche fuehrt, sie sperrt nicht ({@link MAX_UPLOAD_BYTE}).
 *
 * <b>Meldungen stehen im Bereich</b>, nicht an einem Feld: Das Dateifeld ist unsichtbar, eine
 * Feldmeldung daran laese niemand. Auch die Feldmeldung des Servers zu `datei` — der leere Name,
 * die zu grosse Datei am Riegel dahinter — landet deshalb hier.
 *
 * <b>Loeschen steht im ⋯-Menue</b> und fragt vorher nach (Kriterium 10). Der Dateiname geht als
 * Text in die Zeile; er kommt von aussen und wird nie als HTML gesetzt.
 *
 * <b>„Anzeigen" gibt es nur zu einer Anlage mit Vorschauart</b> (Kriterium 15). Woran die Anwendung
 * sie erkennt, entscheidet allein das Backend am Inhalt; die Oberflaeche liest das Ergebnis und
 * raet nicht am Dateinamen nach. Zu einer Tabelle steht darum nur „Herunterladen" — eine Taste, die
 * ein Fenster mit einer leeren Flaeche oeffnete, waere ein Versprechen ohne Deckung.
 *
 * <b>Das Fenster wird beim Schliessen abgebaut</b> und nicht bloss zugeklappt: Daran haengt die
 * Freigabe der Objekt-URL ({@link AnlageVorschau}).
 */

export interface AnlagenProps {
  /** Das Angebot, an dem die Anlagen haengen. */
  readonly angebotId: number;
}

const LAEDT = 'Die Anlagen werden geladen …';
const LEER = 'Noch keine Anlage.';
const AUSFALL_LESEN = 'Die Anlagen sind gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_HOCHLADEN = 'Die Datei wurde nicht hochgeladen. Bitte später erneut versuchen.';
const AUSFALL_LOESCHEN = 'Die Anlage wurde nicht gelöscht. Bitte später erneut versuchen.';
const OHNE_INHALT = 'Die Datei ist leer.';
const LOESCHEN_FRAGE = 'Die Anlage wird gelöscht. Das lässt sich nicht zurücknehmen.';

/** Die Meldung nennt die Grenze in derselben Schreibweise, in der die Liste Groessen zeigt (E12). */
const ZU_GROSS = `Die Datei darf höchstens ${dateigroesse(MAX_UPLOAD_BYTE)} groß sein.`;

/** Der zugaengliche Name des Dateifeldes — die Taste darueber heisst „Datei hochladen". */
const FELD_NAME = 'Datei auswählen';

/** Das Feld, unter dem die Schnittstelle die Datei fuehrt (`AnlageZuGross.FELD`). */
const FELD = 'datei';

/** Die Symbolgroesse in Tasten und Menueeintraegen (wie in {@link Kommentare}). */
const SYMBOL = 16;

/** Was der Bereich ueber seinen Bestand weiss. */
type Stand = 'laedt' | 'gelesen' | 'ausfall';

/**
 * Das Dateifeld aus dem Blickfeld, aber nicht aus dem Baum.
 *
 * `display: none` waere der kuerzere Weg und der falsche: Ein so ausgeblendetes Feld nimmt in
 * manchen Browsern keinen Klick mehr entgegen. Gemass wird es darum weggeschnitten, und aus der
 * Tab-Folge nimmt es `tabIndex={-1}` — erreichbar ist es ueber die Taste.
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

/**
 * Der Leseversuch als Ergebnis: die Anlagen oder `null` fuer einen Ausfall.
 *
 * Ein Ergebnis und kein Setzen von Zustand — so gibt es im Effekt genau eine Stelle, die nach dem
 * Ausbau nichts mehr schreibt (CLAUDE-react.md, Race-Conditions).
 */
async function laden(angebotId: number): Promise<readonly Anlage[] | null> {
  try {
    const gelesen = await anlagenLesen(angebotId);
    return gelesen.anlagen;
  } catch {
    return null;
  }
}

/**
 * Die Anlage mit belegter Vorschauart, oder `null`, wenn es zu ihr keine Vorschau gibt.
 *
 * Die Verengung steht hier und nicht im Fenster: So gibt es „Anzeigen" und das Fenster genau zu
 * denselben Anlagen, und {@link AnlageVorschau} braucht keinen Zweig fuer einen Fall, den es nicht
 * gibt (Kriterium 15).
 */
function mitVorschau(anlage: Anlage): AnlageMitVorschau | null {
  const { vorschauArt } = anlage;
  return vorschauArt === null ? null : { ...anlage, vorschauArt };
}

/** Die Meldung zur gewaehlten Datei, wenn sie so nicht hinausgehen darf (Kriterien 5, 6). */
function vorpruefung(datei: File): string | null {
  if (datei.size === 0) {
    return OHNE_INHALT;
  }
  return datei.size > MAX_UPLOAD_BYTE ? ZU_GROSS : null;
}

export default function Anlagen({ angebotId }: AnlagenProps) {
  const [stand, setzeStand] = useState<Stand>('laedt');
  const [anlagen, setzeAnlagen] = useState<readonly Anlage[]>([]);
  const [meldung, setzeMeldung] = useState<string | null>(null);
  const [laeuft, setzeLaeuft] = useState(false);
  // Das Dateifeld als Zustand und nicht als `useRef`: Die Taste steht erst, wenn es das Feld gibt,
  // auf das sie klickt — dasselbe Muster wie der Anker in {@link AktionsMenue}.
  const [feld, setzeFeld] = useState<HTMLInputElement | null>(null);
  const [vorschau, setzeVorschau] = useState<AnlageMitVorschau | null>(null);

  useEffect(() => {
    let aktuell = true;
    setzeStand('laedt');
    void laden(angebotId).then((gelesen) => {
      // Nach dem Ausbau — und nach einem Wechsel des Angebots — schreibt dieser Lauf nichts mehr.
      if (!aktuell) {
        return;
      }
      if (gelesen === null) {
        setzeStand('ausfall');
        return;
      }
      setzeAnlagen(gelesen);
      setzeStand('gelesen');
    });
    return () => {
      aktuell = false;
    };
  }, [angebotId]);

  /** Laedt die Datei hoch und stellt die Antwort zuoberst (Kriterien 2, 4, 14). */
  const hochladen = async (datei: File) => {
    setzeLaeuft(true);
    try {
      const neu = await anlageHochladen(angebotId, datei);
      setzeAnlagen([neu, ...anlagen]);
    } catch (ursache: unknown) {
      // Die Feldmeldung des Servers zu `datei` ist der genauere Satz; ohne sie bleibt der Ausfall.
      setzeMeldung(meldungAm(feldMeldungen(ursache), FELD) ?? AUSFALL_HOCHLADEN);
    } finally {
      setzeLaeuft(false);
    }
  };

  /** Die Wahl im Dateifeld: vorpruefen, senden, das Feld leeren. */
  const beiWahl = (ereignis: ChangeEvent<HTMLInputElement>) => {
    const datei = ersteDatei(ereignis.target.files);
    // Geleert, damit dieselbe Datei ein zweites Mal gewaehlt werden kann: Ohne das Leeren meldet
    // das Feld keinen Wechsel, und der zweite Versuch geschaehe still gar nicht.
    ereignis.target.value = '';
    setzeMeldung(null);
    if (datei === null) {
      return;
    }
    const fehler = vorpruefung(datei);
    if (fehler !== null) {
      setzeMeldung(fehler);
      return;
    }
    void hochladen(datei);
  };

  /** Loescht die Anlage (Kriterium 10) — die Rueckfrage stellt das ⋯-Menue. */
  const loeschen = async (anlage: Anlage) => {
    setzeMeldung(null);
    setzeLaeuft(true);
    try {
      await anlageLoeschen(angebotId, anlage.id);
      setzeAnlagen(anlagen.filter((alte) => alte.id !== anlage.id));
    } catch {
      setzeMeldung(AUSFALL_LOESCHEN);
    } finally {
      setzeLaeuft(false);
    }
  };

  /**
   * Eine Zeile: Name, Groesse, Zeitpunkt, Anzeigen, der Weg zum Inhalt und das ⋯-Menue
   * (Kriterien 1, 4, 9, 15).
   */
  function zeileZu(anlage: Anlage): ReactNode {
    const zeigbar = mitVorschau(anlage);
    return (
      <Box
        key={anlage.id}
        data-testid="anlage"
        sx={(theme) => ({
          display: 'flex',
          alignItems: 'center',
          gap: '12px',
          flexWrap: 'wrap',
          padding: '8px 12px 8px 16px',
          borderRadius: `${RADIUS_KLEIN}px`,
          background: theme.vars.palette.kupferwolke.flaecheWeich,
        })}
      >
        <Typography
          data-testid="anlage-name"
          sx={{ fontSize: 13.5, fontWeight: 500, flex: '1 1 160px', wordBreak: 'break-word' }}
        >
          {anlage.dateiName}
        </Typography>
        <Typography
          data-testid="anlage-groesse"
          className={ZAHLEN_KLASSE}
          sx={(theme) => ({
            fontSize: 12.5,
            whiteSpace: 'nowrap',
            color: theme.vars.palette.kupferwolke.textSchwach,
          })}
        >
          {dateigroesse(anlage.groesse)}
        </Typography>
        <Typography
          data-testid="anlage-zeit"
          className={ZAHLEN_KLASSE}
          sx={(theme) => ({
            fontSize: 12.5,
            whiteSpace: 'nowrap',
            color: theme.vars.palette.kupferwolke.textSchwach,
          })}
        >
          {zeitpunktWort(anlage.createdAt)}
        </Typography>
        {zeigbar === null ? null : (
          <Box
            component="button"
            type="button"
            aria-label={`Anzeigen: ${anlage.dateiName}`}
            onClick={() => {
              setzeVorschau(zeigbar);
            }}
            sx={(theme) => ({
              width: ICONTASTE,
              height: ICONTASTE,
              flex: 'none',
              borderRadius: `${RADIUS_RUND}px`,
              border: 0,
              cursor: 'pointer',
              display: 'grid',
              placeItems: 'center',
              color: theme.vars.palette.kupferwolke.textMatt,
              background: theme.vars.palette.kupferwolke.flaeche,
              transition: 'background .15s ease, color .15s ease',
              '&:hover': {
                background: theme.vars.palette.kupferwolke.toenung.pfirsich.flaeche,
                color: theme.vars.palette.kupferwolke.toenung.pfirsich.schrift,
              },
            })}
          >
            <IconEye size={18} stroke={1.8} aria-hidden />
          </Box>
        )}
        <Box
          component="a"
          href={anlageInhaltPfad(angebotId, anlage.id)}
          download
          aria-label={`Herunterladen: ${anlage.dateiName}`}
          sx={(theme) => ({
            width: ICONTASTE,
            height: ICONTASTE,
            flex: 'none',
            borderRadius: `${RADIUS_RUND}px`,
            display: 'grid',
            placeItems: 'center',
            color: theme.vars.palette.kupferwolke.textMatt,
            background: theme.vars.palette.kupferwolke.flaeche,
            transition: 'background .15s ease, color .15s ease',
            '&:hover': {
              background: theme.vars.palette.kupferwolke.toenung.pfirsich.flaeche,
              color: theme.vars.palette.kupferwolke.toenung.pfirsich.schrift,
            },
          })}
        >
          <IconDownload size={18} stroke={1.8} aria-hidden />
        </Box>
        <AktionsMenue
          name={`Aktionen für ${anlage.dateiName}`}
          objekt={anlage.dateiName}
          eintraege={[
            {
              titel: 'Löschen',
              symbol: <IconTrash size={SYMBOL} stroke={1.8} />,
              rueckfrage: LOESCHEN_FRAGE,
              onAuswahl: () => {
                void loeschen(anlage);
              },
            },
          ]}
        />
      </Box>
    );
  }

  let bestand: ReactNode;
  if (stand === 'laedt') {
    bestand = (
      <Typography
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
      >
        {LAEDT}
      </Typography>
    );
  } else if (stand === 'ausfall') {
    bestand = <Alert severity="error">{AUSFALL_LESEN}</Alert>;
  } else if (anlagen.length === 0) {
    bestand = (
      <Typography
        role="status"
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textMatt })}
      >
        {LEER}
      </Typography>
    );
  } else {
    bestand = (
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        {anlagen.map(zeileZu)}
      </Box>
    );
  }

  const werkzeug = (
    <>
      <Box
        component="input"
        type="file"
        ref={setzeFeld}
        aria-label={FELD_NAME}
        tabIndex={-1}
        onChange={beiWahl}
        sx={VERSTECKT}
      />
      {feld === null ? null : (
        <WeicheTaste
          onClick={() => {
            feld.click();
          }}
          // Solange der Bestand nicht gelesen ist, gibt es keine Liste, an deren Spitze die neue
          // Anlage treten koennte — sie waere die einzige sichtbare und damit ein falsches Bild.
          disabled={laeuft || stand !== 'gelesen'}
          symbol={<IconUpload size={SYMBOL} stroke={1.8} />}
        >
          Datei hochladen
        </WeicheTaste>
      )}
    </>
  );

  return (
    <Karte
      titel="Anlagen"
      anzahl={stand === 'gelesen' ? anlagen.length : undefined}
      werkzeug={werkzeug}
    >
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
        {meldung === null ? null : <Alert severity="error">{meldung}</Alert>}
        {bestand}
      </Box>
      {vorschau === null ? null : (
        <AnlageVorschau
          angebotId={angebotId}
          anlage={vorschau}
          onSchliessen={() => {
            setzeVorschau(null);
          }}
        />
      )}
    </Karte>
  );
}
