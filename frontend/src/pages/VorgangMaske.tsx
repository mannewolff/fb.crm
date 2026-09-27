import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';

import { firmaLesen, firmenUebersicht } from '../api/firmen';
import type { Ansprechpartner, FirmaZeile } from '../api/firmen';
import type { FieldErrors } from '../api/client';
import { vorgangAendern, vorgangAnlegen, vorgangLesen } from '../api/vorgaenge';
import type { Zuordnung } from '../api/vorgaenge';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import KupferTaste from '../components/KupferTaste';
import Karte from '../components/Karte';
import WeicheTaste from '../components/WeicheTaste';
import { feldMeldungen, nichtGefunden } from '../lib/apifehler';
import { meldungAm } from '../lib/feldmeldung';
import { kennungAus } from '../lib/kennung';
import { namensZug } from '../lib/namenszug';

/**
 * Die Maske des Vorgangs — Anlegen unter `/vorgaenge/neu`, Aendern unter
 * `/vorgaenge/:id/bearbeiten` (Kriterien 5, 6, 7, 10, 23, 26).
 *
 * Eine Komponente fuer beide Wege, wie in {@link FirmaMaske}: Die Felder, ihre Meldungen und ihr
 * Verhalten sind dieselben, und der einzige Unterschied ist, woher die Werte kommen und wohin das
 * Speichern fuehrt. Zwei Abschriften liefen beim ersten Nachziehen auseinander.
 *
 * Rahmen und Aufbau folgen ebenfalls {@link FirmaMaske}: eine {@link Karte} auf der Buehne und
 * nicht die Karte der Auth-Seiten (E19) — die bringt ein eigenes `main` und die Marke mit, und
 * innerhalb des angemeldeten Rahmens waere das ein zweites `main` in derselben Seite. Eine eigene
 * Ruecknahme braucht es nicht: „ohne Speichern verlassen" ist der Weg zurueck (E10).
 *
 * <b>Kein eigener Leseweg fuer die Auswahllisten</b> (E18). Die Firmen kommen aus
 * `GET /api/firmen` — ohne Schalter liefert der Weg nur aktive —, die Ansprechpartner aus
 * `GET /api/firmen/{id}`, dort samt `aktiv`, weshalb diese Maske sie filtert. Ein dritter Weg auf
 * dieselben Daten waere eine zweite Wahrheit. Die Schranke liegt ohnehin nicht hier, sondern
 * serverseitig (E19): Die Oberflaeche fuehrt, sie sperrt nicht.
 *
 * <b>Die eigene Zuordnung des Vorgangs steht in der Wahl, solange sie gewaehlt ist</b>
 * (Kriterium 23, E18). Sie kann inzwischen stillgelegt sein und fehlt dann in den Leselisten;
 * diese Maske mischt sie hinzu, gekennzeichnet in der Aufschrift. Wer etwas anderes waehlt, findet
 * sie danach nicht wieder — {@link mitEigener} haengt sie an die Wahl und nicht an den Vorgang.
 *
 * Beide Auswahllisten sind <b>native</b> `select`-Elemente. Sie tragen die Bedienung des jeweiligen
 * Systems mit — Tastatur, Sprachsteuerung, Screenreader —, statt sie nachzubilden (Kriterium 26).
 * Darum steht „stillgelegt" als Wort in der Aufschrift und nicht als Schild daneben: Ein `option`
 * traegt nichts als Text, und so liest der Screenreader den Stand mit dem Namen.
 *
 * Das Formular traegt `noValidate`. Die Pflichtangabe steht als `required` am Feld — fuer den
 * Screenreader und fuer das Sternchen —, die Meldung dazu schreibt aber diese Maske, damit sie in
 * derselben Sprache und an derselben Stelle steht wie die Meldungen des Servers.
 */

/** Der Wert, mit dem „nichts gewaehlt" im `select` steht. */
const KEINE_WAHL = '';

const TITEL_FEHLT = 'Bitte einen Titel angeben.';
const FIRMA_FEHLT = 'Bitte eine Firma wählen.';
const OHNE_AKTIVE_FIRMA = 'Es ist keine aktive Firma vorhanden.';
const NICHT_GEFUNDEN = 'Diesen Vorgang gibt es nicht.';
const AUSFALL_FIRMEN = 'Die Firmen sind gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_LESEN = 'Der Vorgang ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_PARTNER =
  'Die Ansprechpartner sind gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_SPEICHERN = 'Der Vorgang wurde nicht gespeichert. Bitte später erneut versuchen.';
const LAEDT_FIRMEN = 'Die Firmen werden geladen …';
const LAEDT_VORGANG = 'Der Vorgang wird geladen …';

/** Was die Maske ueber die Firmen und — beim Aendern — ueber den Vorgang weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'bereit'; readonly firmen: readonly FirmaZeile[] }
  | { readonly art: 'unbekannt' }
  | { readonly art: 'ausfall'; readonly meldung: string };

/** Was die Maske ueber die Ansprechpartner der gewaehlten Firma weiss. */
type PartnerStand =
  | { readonly art: 'ohne' }
  | { readonly art: 'bereit'; readonly partner: readonly Ansprechpartner[] }
  | { readonly art: 'ausfall' };

const OHNE_PARTNER: PartnerStand = { art: 'ohne' };

/** Holt die Firmen und macht auch aus dem Fehlschlag einen Stand. */
async function firmenLaden(): Promise<Stand> {
  try {
    // Ohne Schalter: nur aktive Firmen (E18, Kriterium 6).
    const uebersicht = await firmenUebersicht('', false);
    return { art: 'bereit', firmen: uebersicht.firmen };
  } catch {
    // Jeder Grund fuehrt zur selben Meldung; welcher es war, hilft dem Benutzer nicht.
    return { art: 'ausfall', meldung: AUSFALL_FIRMEN };
  }
}

/** Holt die aktiven Ansprechpartner einer Firma und macht auch aus dem Fehlschlag einen Stand. */
async function partnerLaden(firmaId: number): Promise<PartnerStand> {
  try {
    const firma = await firmaLesen(firmaId);
    return { art: 'bereit', partner: firma.ansprechpartner.filter((einer) => einer.aktiv) };
  } catch {
    return { art: 'ausfall' };
  }
}

/** Ein Eintrag einer Auswahlliste: die Kennung als Wert, die Aufschrift als Text. */
interface Wahleintrag {
  readonly id: number;
  readonly aufschrift: string;
}

/**
 * Die elf Werte der Abschlusswahrscheinlichkeit: 0 bis 100 in Zehnerschritten (Kriterium 21).
 *
 * Eine Auswahl und kein Zahlenfeld: Das Backend laesst ohnehin nur Zehnerschritte zu
 * (`AbschlusswahrscheinlichkeitConstraint`), und eine freie Eingabe boete 37 % an, um es danach
 * abzuweisen. Die Null steht als eigener Wert darin — sie heisst „aussichtslos" und ist etwas
 * anderes als „nicht eingeschaetzt", wofuer der Leereintrag steht.
 */
const WAHRSCHEINLICHKEITEN: readonly Wahleintrag[] = Array.from({ length: 11 }, (_leer, stufe) => ({
  id: stufe * 10,
  aufschrift: `${String(stufe * 10)} %`,
}));

/**
 * Die eigene Zuordnung des Vorgangs als Eintrag der Wahl.
 *
 * Gekennzeichnet nur, solange sie stillgelegt ist: Eine aktive Zuordnung steht ohnehin in der
 * Leseliste, und ein Zusatz an ihrer Aufschrift waere eine Auszeichnung ohne Inhalt.
 */
function alsEintrag(zuordnung: Zuordnung): Wahleintrag {
  return {
    id: zuordnung.id,
    aufschrift: zuordnung.aktiv ? zuordnung.name : `${zuordnung.name} (stillgelegt)`,
  };
}

/**
 * Mischt die eigene Zuordnung des Vorgangs in die Wahl — solange sie gewaehlt ist (Kriterium 23).
 *
 * Die Bedingung ist die Wahl und nicht der Vorgang: Wer etwas anderes waehlt, findet die
 * stillgelegte Zuordnung danach nicht wieder, und genau das steht im Kriterium. Doppelt steht sie
 * nie da — eine aktive Zuordnung kommt bereits aus der Leseliste.
 *
 * Sie steht <b>vorn</b>: Sie ist der gewaehlte Stand und der einzige Eintrag, der nicht aus der
 * nach Namen sortierten Antwort des Servers stammt. Sie einzusortieren hiesse, dessen Ordnung hier
 * nachzubilden — dieselbe Reihenfolge an zwei Stellen.
 */
function mitEigener(
  eintraege: readonly Wahleintrag[],
  eigene: Zuordnung | null,
  gewaehlt: string,
): readonly Wahleintrag[] {
  if (eigene === null || gewaehlt !== String(eigene.id)) {
    return eintraege;
  }
  return eintraege.some((einer) => einer.id === eigene.id)
    ? eintraege
    : [alsEintrag(eigene), ...eintraege];
}

/** Die Eintraege einer Auswahlliste, samt dem Eintrag fuer „nichts gewaehlt". */
interface WahlProps {
  readonly label: string;
  readonly leerEintrag: string;
  readonly wert: string;
  readonly setzeWert: (wert: string) => void;
  readonly eintraege: readonly Wahleintrag[];
  readonly meldung?: ReactNode;
  /** Rot und als fehlerhaft angesagt. Ein Hinweis (Kriterium 7) ist kein Fehler am Feld. */
  readonly fehlerhaft?: boolean;
  readonly pflicht?: boolean;
}

/**
 * Eine Auswahlliste der Maske samt ihrer Meldung.
 *
 * `shrink` steht fest: Ein `select` zeigt immer einen Eintrag, also gibt es keinen Stand, in dem
 * die Beschriftung noch im Feld liegen duerfte.
 */
function Wahl({
  label,
  leerEintrag,
  wert,
  setzeWert,
  eintraege,
  meldung,
  fehlerhaft = false,
  pflicht = false,
}: WahlProps) {
  return (
    <TextField
      select
      label={label}
      value={wert}
      onChange={(ereignis) => {
        setzeWert(ereignis.target.value);
      }}
      error={fehlerhaft}
      helperText={meldung}
      required={pflicht}
      fullWidth
      slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
    >
      <option value={KEINE_WAHL}>{leerEintrag}</option>
      {eintraege.map((eintrag) => (
        <option key={eintrag.id} value={eintrag.id}>
          {eintrag.aufschrift}
        </option>
      ))}
    </TextField>
  );
}

/** Eine optionale Kennung auf dem Weg zum Server: „nichts gewaehlt" ist `null`, nicht 0. */
function oderNull(wert: string): number | null {
  return wert === KEINE_WAHL ? null : Number(wert);
}

/** Die Zuordnungen, mit denen der Vorgang gelesen wurde — die Quelle des Einschubs in die Wahl. */
interface Eigene {
  readonly firma: Zuordnung;
  readonly ansprechpartner: Zuordnung | null;
}

/** Ueber jeder Vorgangs-Ansicht steht die Uebersicht (E6). */
const ZU_VORGAENGEN: readonly PfadVerweis[] = [{ titel: 'Vorgänge', ziel: '/vorgaenge' }];

export default function VorgangMaske() {
  const { id } = useParams();
  const aendern = id !== undefined;
  const kennung = kennungAus(id);
  const navigate = useNavigate();
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });
  const [partnerStand, setzePartnerStand] = useState<PartnerStand>(OHNE_PARTNER);
  const [titel, setzeTitel] = useState('');
  const [firmaWahl, setzeFirmaWahl] = useState(KEINE_WAHL);
  const [partnerWahl, setzePartnerWahl] = useState(KEINE_WAHL);
  const [wahrscheinlichkeit, setzeWahrscheinlichkeit] = useState(KEINE_WAHL);
  const [entscheidung, setzeEntscheidung] = useState('');
  const [eigene, setzeEigene] = useState<Eigene | null>(null);
  const [eigeneFehler, setzeEigeneFehler] = useState<FieldErrors>({});
  const [feldFehler, setzeFeldFehler] = useState<FieldErrors>({});
  const [fehler, setzeFehler] = useState<string | null>(null);
  useKopfPfad(ZU_VORGAENGEN, aendern ? 'Vorgang bearbeiten' : 'Neuer Vorgang');
  const [laeuft, setzeLaeuft] = useState(false);

  useEffect(() => {
    if (!aendern) {
      void firmenLaden().then(setzeStand);
      return;
    }
    if (kennung === null) {
      // Eine Kennung, die keine ist, geht gar nicht erst ans Netz (kennung.ts).
      setzeStand({ art: 'unbekannt' });
      return;
    }
    // Erst der Vorgang, dann die Firmen: Ohne den Vorgang gibt es nichts vorzubelegen, und ein
    // unbekannter Vorgang ersparte den zweiten Aufruf ganz. `firmenLaden` scheitert nie — das
    // `catch` gehoert allein dem Lesen des Vorgangs.
    void vorgangLesen(kennung)
      .then(async (vorgang) => {
        setzeTitel(vorgang.titel);
        setzeFirmaWahl(String(vorgang.firma.id));
        setzePartnerWahl(
          vorgang.ansprechpartner === null ? KEINE_WAHL : String(vorgang.ansprechpartner.id),
        );
        setzeWahrscheinlichkeit(
          vorgang.abschlusswahrscheinlichkeit === null
            ? KEINE_WAHL
            : String(vorgang.abschlusswahrscheinlichkeit),
        );
        // Der Tag kommt als `YYYY-MM-DD` — genau die Form, die ein `date`-Feld erwartet. Es
        // braucht darum keine Umrechnung wie `lib/zeitpunkt` beim Zeitpunkt der Historie.
        setzeEntscheidung(vorgang.entscheidungErwartetAm ?? '');
        setzeEigene({ firma: vorgang.firma, ansprechpartner: vorgang.ansprechpartner });
        setzeStand(await firmenLaden());
      })
      .catch((ursache: unknown) => {
        setzeStand(
          nichtGefunden(ursache)
            ? { art: 'unbekannt' }
            : { art: 'ausfall', meldung: AUSFALL_LESEN },
        );
      });
  }, [aendern, kennung]);

  useEffect(() => {
    if (firmaWahl === KEINE_WAHL) {
      setzePartnerStand(OHNE_PARTNER);
      return;
    }
    let aktuell = true;
    setzePartnerStand(OHNE_PARTNER);
    void partnerLaden(Number(firmaWahl)).then((neu) => {
      // Die Firma kann inzwischen gewechselt sein. Dann gehoert diese Antwort zu einer Wahl,
      // die es nicht mehr gibt — sie darf die Liste der neuen Firma nicht ueberschreiben.
      if (aktuell) {
        setzePartnerStand(neu);
      }
    });
    return () => {
      aktuell = false;
    };
  }, [firmaWahl]);

  const absenden = async (ereignis: FormEvent<HTMLFormElement>) => {
    ereignis.preventDefault();
    setzeFehler(null);
    setzeFeldFehler({});
    const eigene: Record<string, readonly string[]> = {};
    if (titel.trim() === '') {
      eigene.titel = [TITEL_FEHLT];
    }
    if (firmaWahl === KEINE_WAHL) {
      eigene.firmaId = [FIRMA_FEHLT];
    }
    setzeEigeneFehler(eigene);
    if (Object.keys(eigene).length > 0) {
      return;
    }
    setzeLaeuft(true);
    const eingabe = {
      titel: titel.trim(),
      firmaId: Number(firmaWahl),
      ansprechpartnerId: oderNull(partnerWahl),
      abschlusswahrscheinlichkeit: oderNull(wahrscheinlichkeit),
      // Ein leeres Datumsfeld ist „nicht gesetzt" und keine Zeichenkette ohne Inhalt: Das Backend
      // liest `LocalDate`, und "" waere dort ein Formfehler statt einer fehlenden Angabe.
      entscheidungErwartetAm: entscheidung === '' ? null : entscheidung,
    };
    try {
      if (kennung === null) {
        const angelegt = await vorgangAnlegen(eingabe);
        navigate(`/vorgaenge/${String(angelegt.id)}`, { replace: true });
      } else {
        await vorgangAendern(kennung, eingabe);
        navigate(`/vorgaenge/${String(kennung)}`, { replace: true });
      }
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
  const meldung = (feld: string) => meldungAm(eigeneFehler, feld) ?? meldungAm(feldFehler, feld);

  const firmenEintraege = mitEigener(
    stand.art === 'bereit'
      ? stand.firmen.map((firma) => ({ id: firma.id, aufschrift: firma.name }))
      : [],
    eigene === null ? null : eigene.firma,
    firmaWahl,
  );

  const partnerEintraege = mitEigener(
    partnerStand.art === 'bereit'
      ? partnerStand.partner.map((einer) => ({ id: einer.id, aufschrift: namensZug(einer) }))
      : [],
    eigene === null ? null : eigene.ansprechpartner,
    partnerWahl,
  );

  /**
   * Gemessen wird die Wahl und nicht die Antwort des Servers.
   *
   * Beim Anlegen ist das dasselbe. Beim Aendern nicht: Dort steht die eigene, womoeglich
   * stillgelegte Firma in der Wahl, auch wenn es keine aktive gibt — und dann soll sich der Vorgang
   * speichern lassen, ohne sie zu aendern (Kriterium 23 vor Kriterium 7).
   */
  const ohneAktiveFirma = stand.art === 'bereit' && firmenEintraege.length === 0;

  /**
   * Der Hinweis an der Firmenauswahl (Kriterium 7): Ohne aktive Firma steht dort der Weg zum
   * Anlegen einer Firma — und zwar als Link, damit er im Tabulatorweg liegt.
   */
  const firmaMeldung: ReactNode = ohneAktiveFirma ? (
    <>
      {`${OHNE_AKTIVE_FIRMA} `}
      <Link component={RouterLink} to="/firmen/neu" underline="hover">
        Firma anlegen
      </Link>
    </>
  ) : (
    meldung('firmaId')
  );

  const partnerMeldung: ReactNode =
    partnerStand.art === 'ausfall' ? AUSFALL_PARTNER : meldung('ansprechpartnerId');

  /**
   * Der Weg zurueck (E10): beim Aendern auf den Vorgang, beim Anlegen auf die Uebersicht.
   *
   * Die Kennung entscheidet und nicht `aendern`: Ohne sie gibt es keinen Vorgang, auf den der Weg
   * fuehren koennte — weder beim Anlegen noch bei einer Kennung, die keine ist.
   */
  const zurueck = kennung === null ? '/vorgaenge' : `/vorgaenge/${String(kennung)}`;

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        // Aussenabstand und Spalt bringt der Rahmen mit; hier bleibt nur der Abstand zwischen
        // den Bereichen der Buehne (CLAUDE-design.md, „Rahmen").
        gap: '22px',
      }}
    >
      <Karte titel={aendern ? 'Vorgang bearbeiten' : 'Neuer Vorgang'}>
        {stand.art === 'bereit' ? (
          <Box
            component="form"
            noValidate
            onSubmit={(ereignis: FormEvent<HTMLFormElement>) => {
              void absenden(ereignis);
            }}
            sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}
          >
            {fehler === null ? null : <Alert severity="error">{fehler}</Alert>}
            <TextField
              label="Titel"
              value={titel}
              onChange={(ereignis) => {
                setzeTitel(ereignis.target.value);
              }}
              error={meldung('titel') !== undefined}
              helperText={meldung('titel')}
              required
              fullWidth
            />
            <Wahl
              label="Firma"
              leerEintrag="— bitte wählen —"
              wert={firmaWahl}
              setzeWert={(neu) => {
                setzeFirmaWahl(neu);
                // Kriterium 6: Mit der Firma wechselt der Kreis der Ansprechpartner — ein zuvor
                // gewaehlter gehoert nicht mehr dazu.
                setzePartnerWahl(KEINE_WAHL);
              }}
              eintraege={firmenEintraege}
              meldung={firmaMeldung}
              fehlerhaft={!ohneAktiveFirma && meldung('firmaId') !== undefined}
              pflicht
            />
            <Wahl
              label="Ansprechpartner"
              leerEintrag="— keiner —"
              wert={partnerWahl}
              setzeWert={setzePartnerWahl}
              eintraege={partnerEintraege}
              meldung={partnerMeldung}
              fehlerhaft={partnerMeldung !== undefined}
            />
            <Wahl
              label="Abschlusswahrscheinlichkeit"
              leerEintrag="— nicht eingeschätzt —"
              wert={wahrscheinlichkeit}
              setzeWert={setzeWahrscheinlichkeit}
              eintraege={WAHRSCHEINLICHKEITEN}
              meldung={meldung('abschlusswahrscheinlichkeit')}
              fehlerhaft={meldung('abschlusswahrscheinlichkeit') !== undefined}
            />
            <TextField
              label="Erwartete Entscheidung"
              type="date"
              value={entscheidung}
              onChange={(ereignis) => {
                setzeEntscheidung(ereignis.target.value);
              }}
              error={meldung('entscheidungErwartetAm') !== undefined}
              helperText={meldung('entscheidungErwartetAm')}
              fullWidth
              // Ein `date`-Feld zeigt immer seine Maske, also darf die Beschriftung nie im Feld
              // liegen — dieselbe Begruendung wie beim `select` in {@link Wahl}.
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, flexWrap: 'wrap' }}>
              <KupferTaste disabled={laeuft || ohneAktiveFirma}>
                {aendern ? 'Speichern' : 'Anlegen'}
              </KupferTaste>
              <WeicheTaste to={zurueck}>Abbrechen</WeicheTaste>
            </Box>
          </Box>
        ) : stand.art === 'laedt' ? (
          <Typography
            sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
          >
            {aendern ? LAEDT_VORGANG : LAEDT_FIRMEN}
          </Typography>
        ) : (
          <Alert severity="error">
            {stand.art === 'unbekannt' ? NICHT_GEFUNDEN : stand.meldung}
          </Alert>
        )}
      </Karte>
    </Box>
  );
}
