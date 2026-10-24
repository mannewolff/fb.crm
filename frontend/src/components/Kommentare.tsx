import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { IconPencil, IconTrash } from '@tabler/icons-react';
import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';

import {
  kommentarAendern,
  kommentarLoeschen,
  kommentarSchreiben,
  kommentareLesen,
} from '../api/kommentare';
import type { Kommentar } from '../api/kommentare';
import { useRechte } from '../auth/rechte';
import { feldMeldungen, istBearbeitungskonflikt } from '../lib/apifehler';
import { meldungAm } from '../lib/feldmeldung';
import { zeitpunktWort } from '../lib/zeitpunkt';
import { RADIUS_KLEIN, ZAHLEN_KLASSE } from '../theme';
import AktionsMenue from './AktionsMenue';
import EigeneEingaben from './EigeneEingaben';
import type { EigeneEingabe } from './EigeneEingaben';
import Karte from './Karte';
import Konfliktmeldung from './Konfliktmeldung';
import WeicheTaste from './WeicheTaste';

/**
 * Der Bereich „Kommentare" am Angebot (Issue #140, Kriterien 1, 2, 4 bis 7, 9 bis 12; Plan #141,
 * E10).
 *
 * <b>Er laedt selbst.</b> `AngebotResponse` traegt die Kommentare nicht (E4), und ein Statuswechsel
 * der Angebotsansicht soll den Bereich nicht neu laden — laege das Laden in der Seite, truege die
 * Seite den Zustand und die Fehlerpfade zweier unabhaengiger Wege. Damit ist dies der zweite
 * Baustein unter `components/`, der fachliche Daten selbst holt; er bleibt die Ausnahme mit Grund
 * (Muster sonst: „Seite laedt, Baustein zeigt", etwa {@link Angebotsliste}).
 *
 * <b>Keine Kupfertaste.</b> Gespeichert wird mit einer weichen Taste: Die eine Kupfertaste der
 * Angebotsansicht ist „Status weiter" (CLAUDE-design.md, „Tasten").
 *
 * <b>Loeschen steht im ⋯-Menue</b> und fragt vorher nach — folgenreiches steht nie gleichrangig
 * neben dem Harmlosen (Kriterium 10). Bearbeitet wird an Ort und Stelle: ein Feld an der Stelle des
 * Textes, mit „Speichern" und „Abbrechen". Der Zeitpunkt bleibt dabei stehen, ein Hinweis
 * „bearbeitet" gibt es nicht (Kriterium 9).
 *
 * <b>Der Text wird vor dem Senden am Rand abgeschnitten</b>, und gemessen wird der abgeschnittene
 * Text (E7): Fuer den Nutzer zaehlt, was gespeichert wird. Scheitert die Pruefung, steht die
 * Meldung am Feld und der Text bleibt stehen (Kriterien 6, 7). Eine Feldmeldung des Servers landet
 * an derselben Stelle; jeder andere Fehlschlag steht als Meldung im Bereich.
 *
 * <b>Jeder Eintrag nennt seinen Verfasser</b> (Kriterium 11 von #409) — den Namen, den der Server
 * mitschickt. <b>Ohne Schreibrecht</b> fehlen Formular und ⋯-Menue ganz (Kriterium 10 von #409,
 * Plan #414, A10): Wer nur liest, sieht die Kommentare und keine Taste, die ins Leere fuehrte.
 *
 * <b>Aendern und Loeschen beruhen auf dem gelesenen Stand</b> (#411, Kriterien 1 bis 3 und 6; Plan
 * #415, E8, E12). Jeder Kommentar traegt seine eigene Version, und der Bereich legt sie beim
 * Aendern und Loeschen vor; ein neuer Kommentar legt keine vor (Kriterium 7). Weist der Server ab,
 * steht die {@link Konfliktmeldung} ueber dem Formular, und der eigene Text bleibt im Feld. „Neu
 * laden" holt die Kommentare samt neuer Versionen, setzt den Stand des Servers ins offene Feld und
 * stellt den eigenen Text daneben in {@link EigeneEingaben}, bis „Verwerfen" gewaehlt wird. Ist
 * der Kommentar inzwischen geloescht, gibt es nichts neu zu laden: „Zur Übersicht" fuehrt zur
 * Liste der Angebote, und der eigene Text bleibt im Feld zum Kopieren.
 */

export interface KommentareProps {
  /** Das Angebot, an dem die Kommentare haengen. */
  readonly angebotId: number;
}

const LAEDT = 'Die Kommentare werden geladen …';
const LEER = 'Noch kein Kommentar.';
const AUSFALL_LESEN =
  'Die Kommentare sind gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_SPEICHERN = 'Der Kommentar wurde nicht gespeichert. Bitte später erneut versuchen.';
const AUSFALL_LOESCHEN = 'Der Kommentar wurde nicht gelöscht. Bitte später erneut versuchen.';
const TEXT_FEHLT = 'Bitte einen Text eingeben.';
const ZU_LANG = 'Ein Kommentar hat höchstens 2.000 Zeichen.';
const LOESCHEN_FRAGE = 'Der Kommentar wird gelöscht. Das lässt sich nicht zurücknehmen.';

/** Wohin „Zur Übersicht" fuehrt, wenn der Kommentar inzwischen geloescht ist (#411, Kriterium 6). */
const UEBERSICHT = '/angebote';

/** Die Beschriftung des eigenen Textes in {@link EigeneEingaben}. */
const EIGENER_TEXT = 'Kommentar';

/** Die Grenze aus Kriterium 7 — dieselbe Zahl traegt `@Size` am `AngebotKommentarRequest`. */
const GRENZE = 2000;

/** Die Symbolgroesse in den Menueeintraegen (wie in {@link FirmaPage}). */
const SYMBOL = 16;

/** Was der Bereich ueber seinen Bestand weiss. */
type Stand = 'laedt' | 'gelesen' | 'ausfall';

/** Der offene Bearbeitungsplatz: welcher Kommentar, welcher Text, welche Meldung am Feld. */
interface Bearbeitung {
  readonly id: number;
  readonly text: string;
  readonly fehler?: string;
}

/** Der bereinigte Text und, wenn er so nicht geht, die Meldung dazu (Kriterien 6, 7). */
function pruefe(roh: string): { readonly text: string; readonly fehler?: string } {
  const text = roh.trim();
  if (text === '') {
    return { text, fehler: TEXT_FEHLT };
  }
  if (text.length > GRENZE) {
    return { text, fehler: ZU_LANG };
  }
  return { text };
}

/**
 * Der Leseversuch als Ergebnis: die Kommentare oder `null` fuer einen Ausfall.
 *
 * Ein Ergebnis und kein Setzen von Zustand — so gibt es im Effekt genau eine Stelle, die nach dem
 * Ausbau nichts mehr schreibt (CLAUDE-react.md, Race-Conditions).
 */
async function laden(angebotId: number): Promise<readonly Kommentar[] | null> {
  try {
    const gelesen = await kommentareLesen(angebotId);
    return gelesen.kommentare;
  } catch {
    return null;
  }
}

/** Die Feldmeldung des Servers zum Text, oder nichts. */
function feldMeldung(ursache: unknown): string | undefined {
  return meldungAm(feldMeldungen(ursache), 'text');
}

export default function Kommentare({ angebotId }: KommentareProps) {
  const { darfFachlichSchreiben } = useRechte();
  const [stand, setzeStand] = useState<Stand>('laedt');
  const [kommentare, setzeKommentare] = useState<readonly Kommentar[]>([]);
  const [entwurf, setzeEntwurf] = useState('');
  const [entwurfFehler, setzeEntwurfFehler] = useState<string | undefined>(undefined);
  const [bearbeitung, setzeBearbeitung] = useState<Bearbeitung | null>(null);
  const [meldung, setzeMeldung] = useState<string | null>(null);
  const [laeuft, setzeLaeuft] = useState(false);
  // Die Abweisung eines Schritts auf altem Stand, ungeprueft — die Konfliktmeldung liest sie selbst.
  const [abweisung, setzeAbweisung] = useState<unknown>(null);
  const [eigene, setzeEigene] = useState<readonly EigeneEingabe[] | null>(null);

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
      setzeKommentare(gelesen);
      setzeStand('gelesen');
    });
    return () => {
      aktuell = false;
    };
  }, [angebotId]);

  /**
   * Nach einem Fehlschlag am Text: Die Feldmeldung des Servers geht ans Feld, alles andere in den
   * Bereich.
   *
   * Eine Stelle fuer beide Wege — Schreiben und Aendern melden denselben Fehlschlag gleich, und die
   * Entscheidung „Feld oder Bereich" steht nur hier.
   */
  const textFehlschlag = (
    ursache: unknown,
    ausfall: string,
    amFeld: (feld: string | undefined) => void,
  ) => {
    const feld = feldMeldung(ursache);
    amFeld(feld);
    if (feld === undefined) {
      setzeMeldung(ausfall);
    }
  };

  /** Schreibt den Entwurf und stellt die Antwort zuoberst (Kriterien 2, 4). */
  const schreiben = async () => {
    const geprueft = pruefe(entwurf);
    if (geprueft.fehler !== undefined) {
      setzeEntwurfFehler(geprueft.fehler);
      return;
    }
    setzeEntwurfFehler(undefined);
    setzeMeldung(null);
    setzeAbweisung(null);
    setzeLaeuft(true);
    try {
      const neu = await kommentarSchreiben(angebotId, geprueft.text);
      setzeKommentare([neu, ...kommentare]);
      setzeEntwurf('');
    } catch (ursache: unknown) {
      textFehlschlag(ursache, AUSFALL_SPEICHERN, setzeEntwurfFehler);
    } finally {
      setzeLaeuft(false);
    }
  };

  /**
   * Aendert den Text eines Kommentars auf seinem gelesenen Stand; der Zeitpunkt kommt aus der
   * Antwort (Kriterium 9). Bei einer Abweisung bleibt das Feld mit dem eigenen Text offen.
   */
  const aendern = async (offen: Bearbeitung, kommentar: Kommentar) => {
    const geprueft = pruefe(offen.text);
    if (geprueft.fehler !== undefined) {
      setzeBearbeitung({ ...offen, fehler: geprueft.fehler });
      return;
    }
    setzeMeldung(null);
    setzeAbweisung(null);
    setzeLaeuft(true);
    try {
      const neu = await kommentarAendern(angebotId, offen.id, kommentar.version, geprueft.text);
      setzeKommentare(kommentare.map((alt) => (alt.id === neu.id ? neu : alt)));
      setzeBearbeitung(null);
    } catch (ursache: unknown) {
      if (istBearbeitungskonflikt(ursache)) {
        setzeAbweisung(ursache);
        return;
      }
      textFehlschlag(ursache, AUSFALL_SPEICHERN, (feld) => {
        setzeBearbeitung({ ...offen, fehler: feld });
      });
    } finally {
      setzeLaeuft(false);
    }
  };

  /** Loescht den Kommentar auf seinem gelesenen Stand (Kriterium 10); nachgefragt hat das ⋯-Menue. */
  const loeschen = async (kommentar: Kommentar) => {
    setzeMeldung(null);
    setzeAbweisung(null);
    setzeLaeuft(true);
    try {
      await kommentarLoeschen(angebotId, kommentar.id, kommentar.version);
      setzeKommentare(kommentare.filter((alt) => alt.id !== kommentar.id));
    } catch (ursache: unknown) {
      if (istBearbeitungskonflikt(ursache)) {
        setzeAbweisung(ursache);
      } else {
        setzeMeldung(AUSFALL_LOESCHEN);
      }
    } finally {
      setzeLaeuft(false);
    }
  };

  /**
   * „Neu laden" nach einer Abweisung: die Kommentare des Servers samt neuer Versionen, der Stand
   * des Servers ins offene Feld und der eigene Text daneben (#411, Kriterium 3; Plan #415, E12).
   *
   * Der eigene Text wird genommen, wie er in diesem Augenblick im Feld steht. Gibt es den
   * Kommentar nicht mehr, schliesst das Feld; der eigene Text bleibt trotzdem daneben stehen.
   */
  const neuLaden = async () => {
    const offen = bearbeitung;
    setzeLaeuft(true);
    const gelesen = await laden(angebotId);
    setzeLaeuft(false);
    if (gelesen === null) {
      setzeMeldung(AUSFALL_LESEN);
      return;
    }
    setzeKommentare(gelesen);
    setzeAbweisung(null);
    setzeMeldung(null);
    if (offen === null) {
      return;
    }
    setzeEigene([{ bezeichnung: EIGENER_TEXT, wert: offen.text }]);
    const aktuell = gelesen.find((kommentar) => kommentar.id === offen.id);
    setzeBearbeitung(aktuell === undefined ? null : { id: aktuell.id, text: aktuell.text });
  };

  /** Ein Eintrag: Verfasser und Zeitpunkt mit ⋯-Menue, darunter der Text oder das Feld zum Aendern. */
  function eintragZu(kommentar: Kommentar): ReactNode {
    const wort = zeitpunktWort(kommentar.createdAt);
    // Der offene Bearbeitungsplatz, wenn er diesem Kommentar gehoert — als Wert und nicht als
    // Schalter, damit die Felder darunter ohne zweite Pruefung auf ihn zugreifen.
    const offen =
      bearbeitung !== null && bearbeitung.id === kommentar.id ? bearbeitung : null;
    return (
      <Box
        key={kommentar.id}
        data-testid="kommentar"
        sx={(theme) => ({
          display: 'flex',
          flexDirection: 'column',
          gap: '8px',
          padding: '14px 16px',
          borderRadius: `${RADIUS_KLEIN}px`,
          background: theme.vars.palette.kupferwolke.flaecheWeich,
        })}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <Typography data-testid="kommentar-verfasser" sx={{ fontSize: 12.5, fontWeight: 600 }}>
            {kommentar.verfasser}
          </Typography>
          <Typography
            data-testid="kommentar-zeit"
            className={ZAHLEN_KLASSE}
            sx={(theme) => ({
              fontSize: 12.5,
              color: theme.vars.palette.kupferwolke.textSchwach,
            })}
          >
            {wort}
          </Typography>
          {darfFachlichSchreiben ? (
            <Box sx={{ marginLeft: 'auto', flex: 'none' }}>
              <AktionsMenue
                name={`Aktionen für Kommentar vom ${wort}`}
                objekt={`Kommentar vom ${wort}`}
                eintraege={[
                  {
                    titel: 'Bearbeiten',
                    symbol: <IconPencil size={SYMBOL} stroke={1.8} />,
                    onAuswahl: () => {
                      setzeBearbeitung({ id: kommentar.id, text: kommentar.text });
                    },
                  },
                  {
                    titel: 'Löschen',
                    symbol: <IconTrash size={SYMBOL} stroke={1.8} />,
                    rueckfrage: LOESCHEN_FRAGE,
                    onAuswahl: () => {
                      void loeschen(kommentar);
                    },
                  },
                ]}
              />
            </Box>
          ) : null}
        </Box>
        {offen === null ? (
          <Typography
            data-testid="kommentar-text"
            sx={{ fontSize: 13.5, whiteSpace: 'pre-wrap' }}
          >
            {kommentar.text}
          </Typography>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <TextField
              label="Kommentar bearbeiten"
              value={offen.text}
              onChange={(ereignis) => {
                setzeBearbeitung({ ...offen, text: ereignis.target.value, fehler: undefined });
              }}
              error={offen.fehler !== undefined}
              helperText={offen.fehler}
              multiline
              minRows={3}
              fullWidth
            />
            <Box sx={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
              <WeicheTaste
                onClick={() => {
                  void aendern(offen, kommentar);
                }}
                disabled={laeuft}
              >
                Speichern
              </WeicheTaste>
              <WeicheTaste
                onClick={() => {
                  setzeBearbeitung(null);
                }}
                disabled={laeuft}
              >
                Abbrechen
              </WeicheTaste>
            </Box>
          </Box>
        )}
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
  } else if (kommentare.length === 0) {
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
        {kommentare.map(eintragZu)}
      </Box>
    );
  }

  return (
    <Karte titel="Kommentare" anzahl={stand === 'gelesen' ? kommentare.length : undefined}>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
        {meldung === null ? null : <Alert severity="error">{meldung}</Alert>}
        <Konfliktmeldung
          ursache={abweisung}
          onNeuLaden={() => {
            void neuLaden();
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
        {darfFachlichSchreiben ? (
          <Box
            sx={{ display: 'flex', flexDirection: 'column', gap: '10px', alignItems: 'flex-start' }}
          >
            <TextField
              label="Neuer Kommentar"
              value={entwurf}
              onChange={(ereignis) => {
                setzeEntwurf(ereignis.target.value);
              }}
              error={entwurfFehler !== undefined}
              helperText={entwurfFehler}
              multiline
              minRows={3}
              fullWidth
            />
            <WeicheTaste
              onClick={() => {
                void schreiben();
              }}
              // Solange der Bestand nicht gelesen ist, gibt es keine Liste, an deren Spitze der neue
              // Kommentar treten koennte — er waere der einzige sichtbare und damit ein falsches Bild.
              disabled={laeuft || stand !== 'gelesen'}
            >
              Kommentar speichern
            </WeicheTaste>
          </Box>
        ) : null}
        {bestand}
      </Box>
    </Karte>
  );
}
