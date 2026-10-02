import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useId, useState } from 'react';
import type { FormEvent, ReactNode } from 'react';

import { buchbarePositionen, zeiteintragAendern, zeiteintragAnlegen } from '../api/arbeitszeit';
import type { Buchungsposition, ZeiteintragEingabe, Zeitzeile } from '../api/arbeitszeit';
import type { FieldErrors } from '../api/client';
import { feldMeldungen } from '../lib/apifehler';
import { dauerInHundertsteln, stundenWort, uhrzeitFeld } from '../lib/arbeitszeit';
import { meldungAm } from '../lib/feldmeldung';
import { heute, tagWort } from '../lib/tag';
import { ZAHLEN_KLASSE } from '../theme';
import KupferTaste from './KupferTaste';
import WeicheTaste from './WeicheTaste';

/**
 * Der Dialog, in dem eine Arbeitszeit entsteht und sich aendert (Issue #193, Kriterien 1 bis 4
 * und 6; Plan #194, A14 bis A16, A19).
 *
 * <b>Ein Dialog und keine eigene Route</b> (A14): Vier Felder sind kein Formular, das eine Seite
 * braucht, und nach dem Speichern soll die Monatsliste sofort wieder dastehen. Eine Komponente fuer
 * beide Wege — Erfassen und Aendern —, aus demselben Grund wie bei {@link FirmaMaske}: Felder,
 * Meldungen und Verhalten sind dieselben, und nur Herkunft der Werte und Ziel des Speicherns
 * unterscheiden sich. Welcher der beiden Wege gilt, sagt {@link ZeiteintragMaskeProps.zeile}.
 *
 * <b>Der Dialog holt seine Auswahlliste selbst</b> (`GET /api/arbeitszeit/buchbare-positionen`):
 * Sie gehoert zu ihm und nicht zur Monatsliste, die ohne ihn auskommt. <b>Die Liste fuehrt, der
 * Server entscheidet</b> — dass eine Position hier steht, ist eine Auskunft und keine Zusage; das
 * Anlegen prueft dieselbe Buchbarkeit noch einmal und meldet sie am Feld.
 *
 * <b>Beim Aendern steht die Position der Zeile notfalls voran</b> (A15): Ein Eintrag kann auf einer
 * Position liegen, auf die heute nicht mehr gebucht werden darf — das Angebot ist abgerechnet, oder
 * die Position wurde umgestellt. Fehlte sie in der Auswahl, zeigte der Dialog eine andere Position
 * als die gebuchte, und ein Speichern haenge den Eintrag stillschweigend um.
 *
 * <b>Die Dauer wird gezeigt, nicht gesendet</b> (Kriterium 2, A3): Sie erscheint neben „bis",
 * sobald beide Uhrzeiten eine Spanne ergeben, und steht in keinem Rumpf — sie entsteht aus Beginn
 * und Ende, und die Anwendung kennt sie selbst.
 *
 * <b>Die eigene Pruefung ist Nutzerfuehrung, nicht die Entscheidung</b> (wie in
 * {@link AnsprechpartnerMaske}): Sie haelt eine halbe Buchung zurueck, die der Server nur als
 * Formfehler ohne Feldbezug abweisen koennte. Alles Fachliche — Raster, Reihenfolge, Buchbarkeit,
 * Ueberschneidung — entscheidet der Server, und seine Meldungen landen aus `fieldErrors` am
 * jeweiligen Feld (A19, A8).
 */

/** Die Schrittweite der Zeitfelder in Sekunden — eine Viertelstunde (A16, Antwort 6). */
const SCHRITT = 900;

const LAEDT = 'Die buchbaren Positionen werden geladen …';
const AUSFALL_LESEN =
  'Die buchbaren Positionen sind gerade nicht zu erreichen. Bitte später erneut versuchen.';
const LEER =
  'Keine Position ist buchbar. Eine Position muss nach Aufwand in Stunden abgerechnet werden, und' +
  ' ihr Angebot muss bestellt oder erledigt sein.';
const AUSFALL_SPEICHERN = 'Die Arbeitszeit wurde nicht gespeichert. Bitte später erneut versuchen.';

/** Was an einem Feld steht, das eine Angabe braucht und keine hat. */
const PFLICHT = 'Bitte ausfüllen.';

/** Die Aufschrift der Auswahl, solange keine Position gewaehlt ist. */
const KEINE_WAHL = 'Bitte wählen';

export interface ZeiteintragMaskeProps {
  /** Der Eintrag, der geaendert wird — `null` beim Erfassen. */
  readonly zeile: Zeitzeile | null;
  /** Gespeichert: Die Monatsliste ist nicht mehr aktuell und der Dialog hat seinen Zweck erfuellt. */
  readonly onGespeichert: () => void;
  /** Abgebrochen — ohne Wirkung auf den Bestand. */
  readonly onSchliessen: () => void;
}

/** Die vier Felder des Dialogs, jedes als Zeichenkette — leer heisst „keine Angabe". */
interface Werte {
  readonly tag: string;
  readonly von: string;
  readonly bis: string;
  readonly angebotPositionId: string;
}

/** Die vier Angaben, und keine davon darf fehlen (`ZeiteintragRequest`). */
const PFLICHTFELDER: readonly (keyof Werte)[] = ['tag', 'von', 'bis', 'angebotPositionId'];

/** Was die Auswahlliste gerade weiss. */
type Wahlstand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly positionen: readonly Buchungsposition[] }
  | { readonly art: 'fehler' };

/** Eine Gruppe der Auswahl: ein Angebot einer Firma mit seinen buchbaren Positionen (A15). */
interface Gruppe {
  readonly angebotId: number;
  readonly name: string;
  readonly positionen: Buchungsposition[];
}

/** Holt die buchbaren Positionen und macht auch aus dem Fehlschlag einen Stand. */
async function wahlLaden(): Promise<Wahlstand> {
  try {
    return { art: 'daten', positionen: await buchbarePositionen() };
  } catch {
    return { art: 'fehler' };
  }
}

/** Die Vorbelegung: beim Aendern die Zeile, beim Erfassen der heutige Tag und sonst nichts. */
function werteAus(zeile: Zeitzeile | null): Werte {
  if (zeile === null) {
    return { tag: heute(), von: '', bis: '', angebotPositionId: '' };
  }
  return {
    tag: zeile.tag,
    von: uhrzeitFeld(zeile.von),
    bis: uhrzeitFeld(zeile.bis),
    angebotPositionId: String(zeile.position.id),
  };
}

/** Die Felder ohne Angabe, jedes mit seiner Meldung — leer heisst „vollstaendig". */
function fehlendeAngaben(werte: Werte): FieldErrors {
  const fehlen: Record<string, readonly string[]> = {};
  for (const feld of PFLICHTFELDER) {
    if (werte[feld] === '') {
      fehlen[feld] = [PFLICHT];
    }
  }
  return fehlen;
}

/**
 * Die Auswahl mit der Position der geaenderten Zeile, wenn sie dort fehlt (A15).
 *
 * Sie steht dann <b>vorn</b> und nicht an der Stelle, an die sie alphabetisch gehoerte: Sie ist die
 * gebuchte Position, und sie gehoert nicht in die Reihe der buchbaren.
 */
function mitZeilenposition(
  zeile: Zeitzeile | null,
  positionen: readonly Buchungsposition[],
): readonly Buchungsposition[] {
  if (zeile === null || positionen.some((position) => position.id === zeile.position.id)) {
    return positionen;
  }
  return [zeile.position, ...positionen];
}

/** Was hinten an den Gruppennamen eines internen Angebots tritt (Issue #236, Kriterium 2). */
const INTERN_ZUSATZ = ' — intern';

/**
 * Zerlegt die Auswahl in einem Durchgang in Gruppen (A15).
 *
 * Ohne eigenes Sortieren: Der Server liefert Firma alphabetisch, darin das neueste Angebot zuerst
 * — die Positionen eines Angebots stehen also beieinander ({@code BuchbarePositionenUseCase}). Eine
 * zweite Ordnung hier waere eine zweite Wahrheit ueber dieselbe Liste.
 *
 * <b>Das Kennzeichen „intern" steht als Wort hinten im Gruppennamen</b> (Issue #236, Kriterium 2;
 * Plan #218, E21) — und nicht als Symbol oder {@code InternChip} wie an den uebrigen Fundstellen.
 * Grund: Die Auswahl ist ein natives Select, ihre Gruppen sind {@code optgroup}, und ein
 * {@code optgroup}-Label nimmt nur Text; ein Element darin gaebe es nicht. Nativ ist die Konvention
 * dieses Projekts und kein Einzelfall — jedes Select im Frontend ist nativ. Hinten angehaengt,
 * damit die Gruppe weiter mit dem Namen beginnt, unter dem sie sortiert ist.
 */
function gruppiert(positionen: readonly Buchungsposition[]): readonly Gruppe[] {
  const gruppen: Gruppe[] = [];
  for (const position of positionen) {
    const letzte = gruppen.at(-1);
    if (letzte !== undefined && letzte.angebotId === position.angebotId) {
      letzte.positionen.push(position);
    } else {
      gruppen.push({
        angebotId: position.angebotId,
        name:
          `${position.firmaName} — Angebot vom ${tagWort(position.angebotDatum)}` +
          (position.intern ? INTERN_ZUSATZ : ''),
        positionen: [position],
      });
    }
  }
  return gruppen;
}

/** Was ueber den Feldern zur Auswahlliste steht: Ladehinweis, Meldung, Satz zur Leere — oder nichts. */
function hinweisZu(wahlstand: Wahlstand): ReactNode {
  if (wahlstand.art === 'laedt') {
    return (
      <Typography
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
      >
        {LAEDT}
      </Typography>
    );
  }
  if (wahlstand.art === 'fehler') {
    return <Alert severity="error">{AUSFALL_LESEN}</Alert>;
  }
  if (wahlstand.positionen.length === 0) {
    return (
      <Typography
        role="status"
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textMatt })}
      >
        {LEER}
      </Typography>
    );
  }
  return null;
}

export default function ZeiteintragMaske({
  zeile,
  onGespeichert,
  onSchliessen,
}: ZeiteintragMaskeProps) {
  const titelId = useId();
  const [wahlstand, setzeWahlstand] = useState<Wahlstand>({ art: 'laedt' });
  const [werte, setzeWerte] = useState<Werte>(() => werteAus(zeile));
  const [eigeneFehler, setzeEigeneFehler] = useState<FieldErrors>({});
  const [feldFehler, setzeFeldFehler] = useState<FieldErrors>({});
  const [fehler, setzeFehler] = useState<string | null>(null);
  const [laeuft, setzeLaeuft] = useState(false);

  useEffect(() => {
    let gueltig = true;
    void wahlLaden().then((neu) => {
      // Der Dialog wurde geschlossen, bevor die Antwort kam; sie schreibt dann nichts mehr.
      if (gueltig) {
        setzeWahlstand(neu);
      }
    });
    return () => {
      gueltig = false;
    };
  }, []);

  const setze = (feld: keyof Werte, wert: string) => {
    setzeWerte((alt) => ({ ...alt, [feld]: wert }));
  };

  const absenden = async (ereignis: FormEvent<HTMLFormElement>) => {
    ereignis.preventDefault();
    setzeFehler(null);
    setzeFeldFehler({});
    const fehlen = fehlendeAngaben(werte);
    setzeEigeneFehler(fehlen);
    if (Object.keys(fehlen).length > 0) {
      return;
    }
    setzeLaeuft(true);
    const eingabe: ZeiteintragEingabe = {
      angebotPositionId: Number(werte.angebotPositionId),
      tag: werte.tag,
      von: werte.von,
      bis: werte.bis,
    };
    try {
      if (zeile === null) {
        await zeiteintragAnlegen(eingabe);
      } else {
        await zeiteintragAendern(zeile.id, eingabe);
      }
      // Kein `setzeLaeuft(false)`: Der Dialog hat seinen Zweck erfuellt, und die Monatsliste baut
      // ihn ab. Ein freigegebenes Speichern waere ein zweiter Eintrag auf einen Klick.
      onGespeichert();
    } catch (ursache) {
      setzeLaeuft(false);
      const felder = feldMeldungen(ursache);
      if (Object.keys(felder).length > 0) {
        setzeFeldFehler(felder);
      } else {
        setzeFehler(AUSFALL_SPEICHERN);
      }
    }
  };

  /** Die eigene Meldung geht vor: Sie beschreibt die Eingabe, die gar nicht erst abging. */
  const meldung = (feld: keyof Werte) =>
    meldungAm(eigeneFehler, feld) ?? meldungAm(feldFehler, feld);

  const dauer = dauerInHundertsteln(werte.von, werte.bis);
  const gruppen = gruppiert(
    mitZeilenposition(zeile, wahlstand.art === 'daten' ? wahlstand.positionen : []),
  );

  return (
    <Dialog open onClose={onSchliessen} fullWidth maxWidth="sm" aria-labelledby={titelId}>
      <DialogTitle id={titelId} sx={{ fontSize: 16 }}>
        {zeile === null ? 'Zeit erfassen' : 'Arbeitszeit ändern'}
      </DialogTitle>
      <Box
        component="form"
        noValidate
        onSubmit={(ereignis: FormEvent<HTMLFormElement>) => {
          void absenden(ereignis);
        }}
      >
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {fehler === null ? null : <Alert severity="error">{fehler}</Alert>}
          {hinweisZu(wahlstand)}
          <TextField
            label="Tag"
            type="date"
            value={werte.tag}
            onChange={(ereignis) => {
              setze('tag', ereignis.target.value);
            }}
            error={meldung('tag') !== undefined}
            helperText={meldung('tag')}
            required
            fullWidth
            slotProps={{ inputLabel: { shrink: true }, htmlInput: { className: ZAHLEN_KLASSE } }}
          />
          <Box sx={{ display: 'flex', gap: '14px', alignItems: 'flex-start', flexWrap: 'wrap' }}>
            <TextField
              label="von"
              type="time"
              value={werte.von}
              onChange={(ereignis) => {
                setze('von', ereignis.target.value);
              }}
              error={meldung('von') !== undefined}
              helperText={meldung('von')}
              required
              sx={{ flex: '1 1 140px' }}
              slotProps={{
                inputLabel: { shrink: true },
                htmlInput: { step: SCHRITT, className: ZAHLEN_KLASSE },
              }}
            />
            <TextField
              label="bis"
              type="time"
              value={werte.bis}
              onChange={(ereignis) => {
                setze('bis', ereignis.target.value);
              }}
              error={meldung('bis') !== undefined}
              helperText={meldung('bis')}
              required
              sx={{ flex: '1 1 140px' }}
              slotProps={{
                inputLabel: { shrink: true },
                htmlInput: { step: SCHRITT, className: ZAHLEN_KLASSE },
              }}
            />
            {dauer === null ? null : (
              <Box sx={{ minWidth: 92, paddingTop: '6px' }}>
                <Typography
                  sx={(theme) => ({
                    fontSize: 12,
                    color: theme.vars.palette.kupferwolke.textSchwach,
                  })}
                >
                  Dauer
                </Typography>
                <Typography
                  data-testid="zeit-dauer"
                  className={ZAHLEN_KLASSE}
                  sx={{ fontSize: 15, fontWeight: 700 }}
                >
                  {stundenWort(dauer)}
                </Typography>
              </Box>
            )}
          </Box>
          <TextField
            select
            label="Position"
            value={werte.angebotPositionId}
            onChange={(ereignis) => {
              setze('angebotPositionId', ereignis.target.value);
            }}
            error={meldung('angebotPositionId') !== undefined}
            helperText={meldung('angebotPositionId')}
            required
            fullWidth
            slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
          >
            <option value="">{KEINE_WAHL}</option>
            {gruppen.map((gruppe) => (
              <optgroup key={gruppe.angebotId} label={gruppe.name}>
                {gruppe.positionen.map((position) => (
                  <option key={position.id} value={String(position.id)}>
                    {position.bezeichnung}
                  </option>
                ))}
              </optgroup>
            ))}
          </TextField>
        </DialogContent>
        <DialogActions sx={{ padding: '4px 24px 20px', gap: '10px' }}>
          <WeicheTaste onClick={onSchliessen} disabled={laeuft}>
            Abbrechen
          </WeicheTaste>
          <KupferTaste disabled={laeuft}>Speichern</KupferTaste>
        </DialogActions>
      </Box>
    </Dialog>
  );
}
