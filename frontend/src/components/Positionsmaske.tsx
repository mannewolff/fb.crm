import Box from '@mui/material/Box';
import IconButton from '@mui/material/IconButton';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { IconArrowDown, IconArrowUp, IconTrash } from '@tabler/icons-react';
import type { Abrechnungsmodus, Einheit } from '../api/angebote';
import { betrag, euro, hundertstel } from '../lib/geld';
import { RADIUS_MITTEL, RADIUS_RUND, ZAHLEN_KLASSE } from '../theme';

/**
 * Eine Position in der Maske des Angebots: die fuenf Felder, der mitrechnende Betrag und die drei
 * Griffe (Kriterien 4, 5, 6).
 *
 * <b>Gesteuert, nicht selbststaendig.</b> Der Zustand liegt in der Angebotsmaske: Die Liste wird
 * nach E8 als Ganzes geschickt, und eine Zeile, die ihre Eingaben selbst hielte, muesste sie beim
 * Absenden erst wieder herausgeben — oder beim Verschieben mitwandern lassen. Beides waere eine
 * zweite Wahrheit ueber dasselbe Angebot.
 *
 * <b>Kennung und Schluessel werden mitgefuehrt, nicht bedient</b> (Plan #169): Jede Aenderung
 * reicht die ganze Position weiter, und beide Angaben kommen unveraendert mit. Sie sind kein Feld
 * und stehen nirgends auf der Seite — die Kennung sagt dem Server, welche Position gemeint ist,
 * und der Schluessel sagt React, welche Zeile gemeint ist. Beides geht niemanden an, der ein
 * Angebot schreibt.
 *
 * <b>Menge und Preis stehen als Text.</b> Kein `type="number"`: Dessen Wert ist eine
 * Gleitkommazahl, und `lib/geld.ts` rechnet ausdruecklich nicht in Gleitkomma (E5). Der Text geht
 * durch {@link hundertstel} und damit durch dieselbe Pruefung, die auch das Absenden benutzt;
 * beide Trenner sind erlaubt, „2,5" wie „2.5".
 *
 * <b>Die Vorbelegung der Einheit ist ein Vorschlag</b> (E26, F7): Ein Wechsel auf „Festpreis"
 * setzt „Pauschal", einer auf „Aufwand" „Personentag" — aber nur, solange niemand die Einheit
 * selbst gewaehlt hat. Wer „Stunde" gewaehlt hat, hat entschieden; ein Vorschlag nimmt eine
 * Entscheidung nicht zurueck. Deshalb traegt die Position das Merkmal {@link
 * Maskenposition.einheitVonHand} — ohne es waere „hat der Mensch gewaehlt?" aus dem Wert allein
 * nicht zu beantworten.
 *
 * Die Felder liegen in einer Gruppe mit dem Namen „Position <n>". Mehrere Zeilen tragen dieselben
 * Beschriftungen; ohne die Gruppe waere „Menge" auf der Seite mehrfach da, und ein Screenreader
 * saehe fuenf Felder ohne Zugehoerigkeit.
 */

/** Eine Position, so wie sie in der Maske steht — Menge und Preis als Text des Feldes. */
export interface Maskenposition {
  /**
   * Die Kennung der gespeicherten Position, oder `null` an einer noch nicht gespeicherten
   * (Plan #169, E2).
   *
   * Sie ist kein Eingabefeld und steht nirgends auf der Seite — die Maske fuehrt sie nur mit und
   * schickt sie beim Speichern zurueck, damit dieselbe Position fortgeschrieben statt neu
   * angelegt wird.
   */
  readonly id: number | null;
  /**
   * Der Schluessel dieser Zeile in der Liste der Maske — dauerhaft, solange die Zeile lebt.
   *
   * Die Stelle in der Liste taugt nicht als Schluessel: Beim Umordnen und Loeschen wandert sie,
   * und React wies der wandernden Zeile den Zustand ihres Nachbarn zu. Bei einer gespeicherten
   * Position entsteht der Schluessel aus der Kennung, bei einer frischen vergibt ihn die Maske
   * ({@link frischePosition}) — zwei frische Zeilen sind sonst nicht zu unterscheiden.
   */
  readonly schluessel: string;
  readonly bezeichnung: string;
  readonly abrechnungsmodus: Abrechnungsmodus;
  readonly menge: string;
  readonly einheit: Einheit;
  readonly einzelpreis: string;
  /** Wahr, sobald ein Mensch die Einheit selbst gewaehlt hat (F7). */
  readonly einheitVonHand: boolean;
}

/** Das Wort zum Abrechnungsmodus (Kriterium 4). */
export const MODUS_WORT: Readonly<Record<Abrechnungsmodus, string>> = {
  AUFWAND: 'Aufwand',
  FESTPREIS: 'Festpreis',
};

/** Das Wort zur Einheit (Kriterium 4, F7). */
export const EINHEIT_WORT: Readonly<Record<Einheit, string>> = {
  STUNDE: 'Stunde',
  PERSONENTAG: 'Personentag',
  PAUSCHAL: 'Pauschal',
};

/** Welche Einheit ein Modus vorschlaegt (F7). */
const VORSCHLAG: Readonly<Record<Abrechnungsmodus, Einheit>> = {
  AUFWAND: 'PERSONENTAG',
  FESTPREIS: 'PAUSCHAL',
};

/**
 * Eine frisch hinzugefuegte Zeile unter dem uebergebenen Schluessel.
 *
 * Menge „1" und Preis „0" statt zweier leerer Felder: Beide Angaben sind Pflicht, und ein leeres
 * Feld waere von der ersten Sekunde an eine Meldung an einer Zeile, an der noch niemand etwas
 * falsch gemacht hat. Die Bezeichnung bleibt leer — sie ist der eine Text, den nur der Mensch
 * kennt, und was zum Versenden fehlt, nennt die Versandpruefung (E27).
 *
 * Eine Funktion und keine Konstante mehr, seit die Zeile einen Schluessel traegt: Zwei frische
 * Zeilen desselben Angebots muessen sich unterscheiden, und eine Konstante gaebe beiden denselben.
 * Den Schluessel vergibt die Maske und nicht diese Funktion — sie allein kennt die Zeilen, die es
 * schon gibt.
 */
export function frischePosition(schluessel: string): Maskenposition {
  return {
    // Ohne Kennung: Die entsteht erst, wenn der Server die Position anlegt (Plan #169, E2).
    id: null,
    schluessel,
    bezeichnung: '',
    abrechnungsmodus: 'AUFWAND',
    menge: '1',
    einheit: 'PERSONENTAG',
    einzelpreis: '0',
    einheitVonHand: false,
  };
}

/** Was an einem Feld steht, dessen Zahl keine ist. */
export const KEINE_ZAHL = 'Bitte eine Zahl mit höchstens zwei Nachkommastellen angeben.';

/**
 * Der Betrag der Position in ganzen Cent — `null`, solange Menge oder Preis keine Zahl sind.
 *
 * Steht hier und nicht in der Maske, obwohl beide ihn brauchen: Die Zeile zeigt ihn, die Maske
 * summiert ihn. Zwei Rechnungen fuer denselben Betrag zeigten irgendwann zwei Zahlen.
 */
export function betragDerPosition(position: Maskenposition): number | null {
  const menge = hundertstel(position.menge);
  const preis = hundertstel(position.einzelpreis);
  return menge === null || preis === null ? null : betrag(menge, preis);
}

export interface PositionsmaskeProps {
  /** Die Nummer der Zeile in der gezeigten Reihenfolge, von 1 an — sie benennt die Gruppe. */
  readonly nummer: number;
  readonly position: Maskenposition;
  readonly aendere: (position: Maskenposition) => void;
  readonly loesche: () => void;
  readonly nachOben: () => void;
  readonly nachUnten: () => void;
  readonly erste: boolean;
  readonly letzte: boolean;
  /** Die Meldung des Servers an der Bezeichnung (`positionen[n].bezeichnung`), sofern es eine gibt. */
  readonly bezeichnungFehler?: string;
}

const SYMBOL = 17;

export default function Positionsmaske({
  nummer,
  position,
  aendere,
  loesche,
  nachOben,
  nachUnten,
  erste,
  letzte,
  bezeichnungFehler,
}: PositionsmaskeProps) {
  const cent = betragDerPosition(position);
  const mengeFehlt = hundertstel(position.menge) === null;
  const preisFehlt = hundertstel(position.einzelpreis) === null;
  return (
    <Box
      component="fieldset"
      sx={(theme) => ({
        display: 'grid',
        gap: '14px',
        gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: 'minmax(0, 1fr) auto' },
        alignItems: 'end',
        margin: 0,
        padding: '18px',
        border: 0,
        borderRadius: `${RADIUS_MITTEL}px`,
        background: theme.vars.palette.kupferwolke.flaecheWeich,
      })}
    >
      <Box
        component="legend"
        sx={(theme) => ({
          fontSize: 12.5,
          fontWeight: 600,
          padding: 0,
          color: theme.vars.palette.kupferwolke.textSchwach,
        })}
      >
        {`Position ${String(nummer)}`}
      </Box>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: '14px', minWidth: 0 }}>
        <TextField
          label="Bezeichnung"
          value={position.bezeichnung}
          onChange={(ereignis) => {
            aendere({ ...position, bezeichnung: ereignis.target.value });
          }}
          error={bezeichnungFehler !== undefined}
          helperText={bezeichnungFehler}
          required
          fullWidth
        />
        <Box
          sx={{
            display: 'grid',
            gap: '14px',
            gridTemplateColumns: {
              xs: 'minmax(0, 1fr)',
              sm: 'repeat(2, minmax(0, 1fr))',
              lg: 'repeat(4, minmax(0, 1fr))',
            },
          }}
        >
          <TextField
            select
            label="Abrechnung"
            value={position.abrechnungsmodus}
            onChange={(ereignis) => {
              // Der Vorschlag gilt nur, solange niemand die Einheit selbst gesetzt hat (F7).
              const modus = ereignis.target.value === 'FESTPREIS' ? 'FESTPREIS' : 'AUFWAND';
              aendere({
                ...position,
                abrechnungsmodus: modus,
                einheit: position.einheitVonHand ? position.einheit : VORSCHLAG[modus],
              });
            }}
            fullWidth
            slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
          >
            {Object.entries(MODUS_WORT).map(([wert, wort]) => (
              <option key={wert} value={wert}>
                {wort}
              </option>
            ))}
          </TextField>
          <TextField
            label="Menge"
            value={position.menge}
            onChange={(ereignis) => {
              aendere({ ...position, menge: ereignis.target.value });
            }}
            error={mengeFehlt}
            helperText={mengeFehlt ? KEINE_ZAHL : undefined}
            fullWidth
            slotProps={{ htmlInput: { inputMode: 'decimal', className: ZAHLEN_KLASSE } }}
          />
          <TextField
            select
            label="Einheit"
            value={position.einheit}
            onChange={(ereignis) => {
              const gewaehlt = ereignis.target.value;
              // Ab jetzt gehoert die Einheit dem Menschen — kein Modus-Wechsel setzt sie um.
              aendere({
                ...position,
                einheit: gewaehlt === 'STUNDE' || gewaehlt === 'PAUSCHAL' ? gewaehlt : 'PERSONENTAG',
                einheitVonHand: true,
              });
            }}
            fullWidth
            slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
          >
            {Object.entries(EINHEIT_WORT).map(([wert, wort]) => (
              <option key={wert} value={wert}>
                {wort}
              </option>
            ))}
          </TextField>
          <TextField
            label="Einzelpreis (netto)"
            value={position.einzelpreis}
            onChange={(ereignis) => {
              aendere({ ...position, einzelpreis: ereignis.target.value });
            }}
            error={preisFehlt}
            helperText={preisFehlt ? KEINE_ZAHL : undefined}
            fullWidth
            slotProps={{ htmlInput: { inputMode: 'decimal', className: ZAHLEN_KLASSE } }}
          />
        </Box>
      </Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
        <Box sx={{ minWidth: 110, textAlign: { md: 'right' } }}>
          <Typography
            sx={(theme) => ({ fontSize: 12, color: theme.vars.palette.kupferwolke.textSchwach })}
          >
            Betrag
          </Typography>
          <Typography
            data-testid="positions-betrag"
            className={ZAHLEN_KLASSE}
            sx={{ fontSize: 15, fontWeight: 700 }}
          >
            {cent === null ? '—' : euro(cent)}
          </Typography>
        </Box>
        <IconButton
          aria-label={`Position ${String(nummer)} nach oben`}
          onClick={nachOben}
          disabled={erste}
          sx={{ borderRadius: `${RADIUS_RUND}px` }}
        >
          <IconArrowUp size={SYMBOL} stroke={1.8} aria-hidden />
        </IconButton>
        <IconButton
          aria-label={`Position ${String(nummer)} nach unten`}
          onClick={nachUnten}
          disabled={letzte}
          sx={{ borderRadius: `${RADIUS_RUND}px` }}
        >
          <IconArrowDown size={SYMBOL} stroke={1.8} aria-hidden />
        </IconButton>
        <IconButton
          aria-label={`Position ${String(nummer)} löschen`}
          onClick={loesche}
          sx={(theme) => ({
            borderRadius: `${RADIUS_RUND}px`,
            color: theme.vars.palette.kupferwolke.toenung.rose.schrift,
          })}
        >
          <IconTrash size={SYMBOL} stroke={1.8} aria-hidden />
        </IconButton>
      </Box>
    </Box>
  );
}
