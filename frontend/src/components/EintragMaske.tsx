import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import FormControl from '@mui/material/FormControl';
import FormHelperText from '@mui/material/FormHelperText';
import FormLabel from '@mui/material/FormLabel';
import TextField from '@mui/material/TextField';
import { useEffect, useRef, useState } from 'react';
import type { ChangeEvent, FormEvent, ReactNode } from 'react';

import type { FieldErrors } from '../api/client';
import { eintragAendern, eintragHinzufuegen } from '../api/vorgaenge';
import type { Eintrag, Eintragsart } from '../api/vorgaenge';
import { feldMeldungen } from '../lib/apifehler';
import { dateigroesse, MAX_UPLOAD_BYTE } from '../lib/dateigroesse';
import { ersteDatei } from '../lib/dateiwahl';
import { meldungAm } from '../lib/feldmeldung';
import { alsEingabe, alsZeitstempel } from '../lib/zeitpunkt';
import KupferTaste from './KupferTaste';

/**
 * Die Maske fuer einen Eintrag der Historie (E20, Kriterien 13, 14, 18, 19, 26).
 *
 * Sie sitzt als Platte ueber der Historie und nicht auf einer eigenen Seite: Ein Kommentar
 * entsteht im Lesen des Vorgangs, und ein Seitenwechsel dafuer naehme dem Benutzer genau den
 * Zusammenhang, in dem er schreibt. Im Modus <b>Aendern</b> sitzt sie noch enger dran — in der
 * Zeile des Eintrags selbst (E20).
 *
 * <b>Zwei Modi, eine Maske</b> ({@link EintragModus}). Beide fragen dieselben zwei Felder ab und
 * schreiben ihre Meldungen an dieselben Stellen; getrennte Komponenten waeren zwei Orte fuer
 * Pflichtpruefung, Zeitumrechnung und Feldmeldungen. Sie unterscheiden sich in drei Punkten:
 *
 * <ul>
 *   <li><b>Art und Datei stehen nur im Hinzufuegen.</b> Beide sind im Bestand nicht aenderbar —
 *       ein Anhang laesst sich nicht austauschen, wer eine andere Datei meint, haengt sie an
 *       ({@code EintragAenderungRequest} kennt darum kein Feld dafuer).</li>
 *   <li><b>Der Rumpf ist verschieden</b> (E21): Formular beim Hinzufuegen, weil eine Datei dabei
 *       sein kann, JSON beim Aendern, weil dort keine mitkommt.</li>
 *   <li><b>Danach passiert Verschiedenes</b>: Das Hinzufuegen leert die Maske fuer den naechsten
 *       Eintrag, das Aendern gibt sie auf — die Zeile geht wieder zu.</li>
 * </ul>
 *
 * Drei Zusagen tragen die Maske:
 *
 * <ul>
 *   <li><b>Was Pflicht ist, haengt an der Art</b> (Kriterien 13, 14): Der Kommentar braucht einen
 *       Text, der Anhang eine Datei. Beim Anhang ist der Text die Beschreibung und darf
 *       fehlen — im Aendern dagegen nie, denn dort setzt die Schnittstelle den Text auf einen
 *       Wert (Kriterium 19).</li>
 *   <li><b>Die Grenze der Dateigroesse greift vor dem Absenden</b> (E10, Kriterium 18). Eine zu
 *       grosse Datei erst hochzuladen, um danach abgewiesen zu werden, kostet den Benutzer die
 *       Wartezeit und das Netz die Uebertragung. Die Grenze ist dieselbe Zahl wie im Backend, und
 *       sie steht im Frontend an genau einer Stelle ({@link MAX_UPLOAD_BYTE}). Die Schranke bleibt
 *       trotzdem serverseitig — die Oberflaeche fuehrt, sie sperrt nicht.</li>
 *   <li><b>Jede Meldung steht am Feld und wird angesagt</b> (Kriterium 26): ueber
 *       `aria-describedby` mit dem Feld verbunden und in einem Live-Bereich, damit der
 *       Screenreader sie hoert, sobald sie erscheint — auch wenn der Fokus noch auf der Taste
 *       liegt.</li>
 * </ul>
 *
 * Der neue Eintrag geht als <b>Formular</b> hinaus (E21): Beim Hinzufuegen kann eine Datei dabei
 * sein, und die passt in keinen JSON-Rumpf. Die Felder heissen wie die Komponenten von
 * `EintragRequest` im Backend — `art`, `geschehenAm`, `text`, `datei`.
 */

/** Die Kennungen des Dateifeldes und seiner Meldung — es traegt sein `aria-describedby` selbst. */
const DATEI_FELD = 'eintrag-datei';
const DATEI_MELDUNG = 'eintrag-datei-meldung';

const TEXT_FEHLT = 'Bitte einen Text angeben.';
const DATEI_FEHLT = 'Bitte eine Datei wählen.';
const ZEITPUNKT_FEHLT = 'Bitte einen Zeitpunkt angeben.';
const ZU_GROSS = `Die Datei darf höchstens ${dateigroesse(MAX_UPLOAD_BYTE)} groß sein.`;
const AUSFALL = 'Der Eintrag wurde nicht gespeichert. Bitte später erneut versuchen.';

/** „Jetzt" in der Form, die ein `datetime-local`-Feld annimmt (Kriterium 13). */
function jetzt(): string {
  return alsEingabe(new Date().toISOString());
}

/**
 * Eine Meldung am Feld als Live-Bereich — oder nichts.
 *
 * `undefined` statt eines leeren Bereichs: MUI verknuepft den Hilfetext nur dann ueber
 * `aria-describedby`, wenn es ihn gibt (siehe `lib/feldmeldung`).
 */
function ansage(meldung: string | undefined): ReactNode {
  return meldung === undefined ? undefined : (
    <Box component="span" role="alert">
      {meldung}
    </Box>
  );
}

/**
 * Was die Maske tut: einen Eintrag anlegen oder einen bestehenden aendern.
 *
 * Ein ausgezeichneter Verbund und nicht zwei einzelne, wahlfreie Angaben: So gibt es keinen
 * Zustand „Aendern ohne Eintrag" und keinen „Eintrag ohne Weg zurueck", den der Uebersetzer
 * durchliesse und erst die laufende Anwendung bemerkte.
 */
export type EintragModus =
  | { readonly art: 'hinzufuegen' }
  | {
      readonly art: 'aendern';
      /** Der Eintrag, dessen Text und Zeitpunkt fortgeschrieben werden. */
      readonly eintrag: Eintrag;
      /** Gibt die Maske auf, ohne zu speichern — die Zeile geht wieder zu. */
      readonly abgebrochen: () => void;
    };

export interface EintragMaskeProps {
  /** Der Vorgang, an dessen Historie der Eintrag haengt. */
  readonly vorgangId: number;
  /** Hinzufuegen oder Aendern — samt dem, was der jeweilige Modus dazu braucht. */
  readonly modus: EintragModus;
  /**
   * Ruft den Aufrufer zum Neuladen auf, sobald der Eintrag gespeichert wurde.
   *
   * Die Maske haelt die Historie nicht selbst: Der Server vergibt Kennung, Erfassungszeitpunkt,
   * den Vermerk „geaendert" und die Reihenfolge, und nur er weiss, wie die Liste danach aussieht.
   */
  readonly gespeichert: () => void;
}

export default function EintragMaske({ vorgangId, modus, gespeichert }: EintragMaskeProps) {
  const aendern = modus.art === 'aendern';
  /**
   * Die Art gehoert zum Hinzufuegen: Nur dort steht die Wahl, und nur dort kann daraus `ANHANG`
   * werden. Im Aendern bleibt es bei `KOMMENTAR` — damit faellt die Dateiwahl unten von selbst
   * weg, ohne eine zweite Bedingung, die dasselbe noch einmal sagt.
   */
  const [art, setzeArt] = useState<Eintragsart>('KOMMENTAR');
  const [text, setzeText] = useState(() =>
    modus.art === 'aendern' ? (modus.eintrag.text ?? '') : '',
  );
  const [zeitpunkt, setzeZeitpunkt] = useState(() =>
    modus.art === 'aendern' ? alsEingabe(modus.eintrag.geschehenAm) : jetzt(),
  );
  const [datei, setzeDatei] = useState<File | null>(null);
  const [eigeneFehler, setzeEigeneFehler] = useState<FieldErrors>({});
  const [feldFehler, setzeFeldFehler] = useState<FieldErrors>({});
  const [fehler, setzeFehler] = useState<string | null>(null);
  const [laeuft, setzeLaeuft] = useState(false);
  const textFeld = useRef<HTMLTextAreaElement>(null);

  /**
   * Im Aendern springt der Fokus in die Maske (Kriterium 26).
   *
   * Sie geht auf Tastendruck mitten in der Historie auf; ohne diesen Sprung stuende der Fokus
   * weiter auf einer Taste, die es gerade nicht mehr gibt, und wer nicht sieht, wo er ist,
   * muesste die Maske erst suchen. `autoFocus` taete dasselbe, verstiesse aber gegen
   * `jsx-a11y/no-autofocus` — die Regel meint Masken, die beim Laden einer Seite ungefragt
   * ziehen, nicht eine, die der Benutzer selbst gerade geoeffnet hat.
   */
  useEffect(() => {
    if (aendern) {
      textFeld.current?.focus();
    }
  }, [aendern]);

  /**
   * Beim Anhang ist der Text die Beschreibung und darf fehlen (Kriterium 14). Im Aendern ist er
   * immer Pflicht: Dort setzt die Schnittstelle ihn auf einen Wert, auch beim Anhang
   * (`EintragAenderungRequest`).
   */
  const textPflicht = aendern || art === 'KOMMENTAR';

  const absenden = async (ereignis: FormEvent<HTMLFormElement>) => {
    ereignis.preventDefault();
    setzeFehler(null);
    setzeFeldFehler({});
    const beschnitten = text.trim();
    const eigene: Record<string, readonly string[]> = {};
    if (textPflicht && beschnitten === '') {
      eigene.text = [TEXT_FEHLT];
    }
    if (art === 'ANHANG') {
      if (datei === null) {
        eigene.datei = [DATEI_FEHLT];
      } else if (datei.size > MAX_UPLOAD_BYTE) {
        eigene.datei = [ZU_GROSS];
      }
    }
    const zeitstempel = alsZeitstempel(zeitpunkt);
    if (zeitstempel === null) {
      eigene.geschehenAm = [ZEITPUNKT_FEHLT];
    }
    setzeEigeneFehler(eigene);
    // Der Zeitstempel steht zuerst: Danach weiss auch der Uebersetzer, dass er eine Zeichenkette
    // ist — ein zweiter Zweig weiter unten waere einer, der nie laeuft.
    if (zeitstempel === null || Object.keys(eigene).length > 0) {
      return;
    }
    setzeLaeuft(true);
    try {
      if (modus.art === 'aendern') {
        await eintragAendern(vorgangId, modus.eintrag.id, {
          text: beschnitten,
          geschehenAm: zeitstempel,
        });
      } else {
        const formular = new FormData();
        formular.append('art', art);
        formular.append('geschehenAm', zeitstempel);
        if (beschnitten !== '') {
          formular.append('text', beschnitten);
        }
        // Eine Datei gibt es nur beim Anhang: Mit der Art faellt sie weg (siehe die Wahl unten).
        if (datei !== null) {
          formular.append('datei', datei);
        }
        await eintragHinzufuegen(vorgangId, formular);
        // Leer heisst leer — die Maske steht fuer den naechsten Eintrag bereit. Im Aendern
        // entfaellt das: Dort gibt der Aufrufer die Maske gleich auf.
        setzeArt('KOMMENTAR');
        setzeText('');
        setzeZeitpunkt(jetzt());
        setzeDatei(null);
        setzeEigeneFehler({});
      }
      gespeichert();
    } catch (ursache) {
      const felder = feldMeldungen(ursache);
      if (Object.keys(felder).length > 0) {
        setzeFeldFehler(felder);
      } else {
        // Welcher Grund es war, hilft dem Benutzer nicht (CLAUDE-react.md).
        setzeFehler(AUSFALL);
      }
    } finally {
      setzeLaeuft(false);
    }
  };

  /** Die eigene Meldung geht vor: Sie beschreibt die Eingabe, die gar nicht erst abging. */
  const meldung = (feld: string) => meldungAm(eigeneFehler, feld) ?? meldungAm(feldFehler, feld);

  const dateiMeldung = meldung('datei');

  return (
    <Box
      component="form"
      noValidate
      onSubmit={(ereignis: FormEvent<HTMLFormElement>) => {
        void absenden(ereignis);
      }}
      sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
        // Im Aendern sitzt die Maske in der Zeile der Historie, die ihren Rand schon mitbringt.
        padding: aendern ? '8px 0 4px' : '18px 16px',
      }}
    >
      {fehler === null ? null : <Alert severity="error">{fehler}</Alert>}
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: aendern
            ? 'minmax(0, 1fr)'
            : { xs: 'minmax(0, 1fr)', sm: '180px minmax(0, 1fr)' },
          gap: 2,
          alignItems: 'start',
        }}
      >
        {aendern ? null : (
          <TextField
            select
            label="Art"
            value={art}
            onChange={(ereignis) => {
              setzeArt(ereignis.target.value === 'ANHANG' ? 'ANHANG' : 'KOMMENTAR');
              // Mit der Art wechselt, was Pflicht ist: Eine Datei aus der vorigen Wahl gehoert
              // nicht an einen Kommentar.
              setzeDatei(null);
            }}
            fullWidth
            slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
          >
            <option value="KOMMENTAR">Kommentar</option>
            <option value="ANHANG">Anhang</option>
          </TextField>
        )}
        <TextField
          label="Zeitpunkt"
          type="datetime-local"
          value={zeitpunkt}
          onChange={(ereignis) => {
            setzeZeitpunkt(ereignis.target.value);
          }}
          error={meldung('geschehenAm') !== undefined}
          helperText={ansage(meldung('geschehenAm'))}
          required
          fullWidth
          slotProps={{ inputLabel: { shrink: true } }}
        />
      </Box>
      <TextField
        label="Text"
        value={text}
        onChange={(ereignis) => {
          setzeText(ereignis.target.value);
        }}
        error={meldung('text') !== undefined}
        helperText={ansage(meldung('text'))}
        required={textPflicht}
        multiline
        minRows={3}
        fullWidth
        inputRef={textFeld}
      />
      {art === 'ANHANG' ? (
        <FormControl error={dateiMeldung !== undefined} required fullWidth>
          <FormLabel htmlFor={DATEI_FELD} sx={{ fontSize: 12.5 }}>
            Datei
          </FormLabel>
          <Box
            component="input"
            id={DATEI_FELD}
            type="file"
            required
            aria-describedby={dateiMeldung === undefined ? undefined : DATEI_MELDUNG}
            onChange={(ereignis: ChangeEvent<HTMLInputElement>) => {
              setzeDatei(ersteDatei(ereignis.target.files));
            }}
            sx={(theme) => ({
              marginTop: '8px',
              fontSize: 12.5,
              fontFamily: 'inherit',
              color: theme.vars.palette.kupferwolke.text,
            })}
          />
          {dateiMeldung === undefined ? null : (
            <FormHelperText id={DATEI_MELDUNG} role="alert">
              {dateiMeldung}
            </FormHelperText>
          )}
        </FormControl>
      ) : null}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
        <KupferTaste disabled={laeuft}>{aendern ? 'Speichern' : 'Hinzufügen'}</KupferTaste>
        {modus.art === 'aendern' ? (
          // Bewusst matt und nicht als zweite erhabene Taste: „Abbrechen" ist der Weg zurueck,
          // nicht die Handlung, zu der die Maske aufgegangen ist.
          <Button
            type="button"
            onClick={modus.abgebrochen}
            disabled={laeuft}
            sx={(theme) => ({
              fontSize: 12.5,
              fontWeight: 600,
              textTransform: 'none',
              color: theme.vars.palette.kupferwolke.textMatt,
            })}
          >
            Abbrechen
          </Button>
        ) : null}
      </Box>
    </Box>
  );
}
