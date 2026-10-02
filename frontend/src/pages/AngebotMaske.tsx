import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Checkbox from '@mui/material/Checkbox';
import FormControl from '@mui/material/FormControl';
import FormControlLabel from '@mui/material/FormControlLabel';
import FormHelperText from '@mui/material/FormHelperText';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { IconPlus } from '@tabler/icons-react';
import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import type { FieldErrors } from '../api/client';
import { angebotAendern, angebotAnlegen, angebotLesen } from '../api/angebote';
import type { Angebot, PositionEingabe } from '../api/angebote';
import { firmaLesen } from '../api/firmen';
import type { Ansprechpartner } from '../api/firmen';
import Karte from '../components/Karte';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import KupferTaste from '../components/KupferTaste';
import Positionsmaske, { betragDerPosition, frischePosition } from '../components/Positionsmaske';
import type { Maskenposition } from '../components/Positionsmaske';
import WeicheTaste from '../components/WeicheTaste';
import { feldMeldungen, nichtGefunden } from '../lib/apifehler';
import { meldungAm } from '../lib/feldmeldung';
import { dezimal, euro, hundertstel } from '../lib/geld';
import { kennungAus } from '../lib/kennung';
import { namensZug } from '../lib/namenszug';
import { ZAHLEN_KLASSE } from '../theme';

/**
 * Die Maske des Angebots — Anlegen und Bearbeiten in einer Ansicht (Issue #127, Kriterien 2, 5).
 *
 * <b>Zwei Schritte, weil das Anlegen zwei Schritte sind.</b> Beim Anlegen nimmt das Backend nur
 * den Ansprechpartner (`AngebotAnlegenRequest`, Issue #126); Datum, Beschreibung und Positionen
 * bekommt das Angebot danach ueber den einen Aenderungsweg (E8). Die Maske folgt dem: Unter
 * `/firmen/:id/angebote/neu` steht die Wahl des Ansprechpartners, danach fuehrt sie mit `replace`
 * in `/angebote/:angebotId/bearbeiten`. Beide Wege in einem
 * Absenden zu buendeln hiesse, bei einem Fehlschlag auf halbem Weg ein Angebot
 * zurueckzulassen, das niemand bestellt hat.
 *
 * <b>Die Liste ist Zustand der Maske.</b> Hinzufuegen, Loeschen und Verschieben aendern nur die
 * Reihenfolge im Zustand; geschickt wird sie beim Speichern als Ganzes (E8). Ein Netzweg je Klick
 * machte aus einer Reihenfolgeaenderung eine Kette halbfertiger Zustaende.
 *
 * <b>Jede Zeile fuehrt die Kennung ihrer Position mit</b> (Plan #169, E2). Beim Laden kommt sie aus
 * der Antwort, beim Hinzufuegen bleibt sie leer, beim Umordnen und Aendern wandert sie mit der
 * Zeile, und beim Speichern geht sie hinaus. Erst dadurch schreibt ein Speichern die vorhandenen
 * Positionen fort, statt sie zu loeschen und neu anzulegen — woran sonst keine Rechnungsposition
 * haengenbleiben koennte (#160, Kriterium 28). Sichtbar ist sie nirgends.
 *
 * <b>Nach dem Speichern gilt die Antwort.</b> Der `PUT` gibt das Angebot mit den neu gerechneten
 * Betraegen zurueck, und die Maske uebernimmt es — Menge, Preis, Betrag und Summe stehen danach so
 * da, wie der Server sie fuehrt. Die Maske bleibt dabei stehen: Sie ist die Werkbank am Angebot,
 * und genau dafuer traegt die Antwort einen Rumpf (`AngebotController`, „ohne zweiten Aufruf").
 *
 * <b>Offen in jedem Status</b> (Kriterium 5): Die Maske fragt nicht nach dem Status.
 *
 * <b>Zur Wahl des Ansprechpartners</b> stehen die aktiven Ansprechpartner der Firma und dazu der
 * gespeicherte, auch wenn er inzwischen stillgelegt ist — sonst liesse sich das Angebot nicht mehr
 * speichern, ohne ihn zu verlieren. Beim Bearbeiten liest die Maske dafuer auch die Firma.
 *
 * <b>Die Meldung an einer Position</b> kommt vom Server als `positionen[n].bezeichnung` und steht an
 * der n-ten Positionsmaske, nicht oben in einer Sammelmeldung.
 *
 * <b>Das Kaestchen „Internes Projekt" steht in beiden Schritten</b> (Issue #207, Kriterium 1).
 * Beim Anlegen, weil der Anfangsstatus daran haengt (`LAEUFT` statt `ANGELEGT`, Issue #226); beim
 * Bearbeiten, weil das Umstellen dort geschieht. Es haengt an derselben Anbindung wie jedes andere
 * Feld: Weist der Server einen gesperrten Artwechsel ab, kommt die Meldung als Feldfehler an
 * `intern` und steht am Kaestchen statt in einer Sammelmeldung (Plan #218, E20) — eine Meldung
 * gehoert an die Stelle, an der sie gilt. Das Kaestchen bleibt dabei <b>bedienbar</b>: Die Sperre
 * kennt nur der Server, und sie kann sich wieder loesen (eine Rechnung wird geloescht); ein
 * gesperrtes Kaestchen ohne Weg zurueck waere eine Sackgasse.
 *
 * <b>Der Haken verbirgt, er wirft nicht weg</b> (Kriterien 3, 8). Menge, Einheit, Einzelpreis und
 * Abrechnungsart bleiben im Zustand der Maske stehen und verschwinden nur aus der Ansicht; wer
 * zurueckschaltet, findet sie wieder. Das ist die Oberflaeche zu dem, was der Server tut — er
 * behaelt die vier Angaben ebenfalls. Eine Maske, die beim Haken wegwirft, was der Server behaelt,
 * widerspraeche ihm. Hinaus gehen die vier am internen Angebot trotzdem nicht; das filtert die
 * Systemgrenze (`api/angebote.ts`, `rumpf`).
 *
 * <b>Am internen Angebot entfaellt die Summenzeile.</b> Sie ist die mitrechnende Summe der Maske
 * und nicht die Spalte einer Tafel; eine Zeile, die nur noch „–" sagte, waere eine Beschriftung
 * ohne Inhalt. Damit entfaellt auch der Ort, an dem {@link ZAHLEN_UNKLAR} stuende — und darum haelt
 * eine unlesbare Zahl in einem verborgenen Feld das Speichern nicht mehr auf (siehe
 * {@link alsEingaben}).
 *
 * <b>Der Kopfpfad nennt die Firma beim Namen</b>, sobald sie bekannt ist.
 */

const NICHT_GEFUNDEN_FIRMA = 'Diese Firma gibt es nicht.';
const FIRMA_STILLGELEGT = 'Diese Firma ist stillgelegt; ein neues Angebot entsteht daran nicht.';
const NICHT_GEFUNDEN_ANGEBOT = 'Dieses Angebot gibt es nicht.';
const AUSFALL_FIRMA = 'Die Firma ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_LESEN = 'Das Angebot ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_ANLEGEN = 'Das Angebot wurde nicht angelegt. Bitte später erneut versuchen.';
const AUSFALL_SPEICHERN = 'Das Angebot wurde nicht gespeichert. Bitte später erneut versuchen.';
const DATUM_FEHLT = 'Bitte das Datum des Angebots angeben.';
const GESPEICHERT = 'Gespeichert.';
const ZAHLEN_UNKLAR =
  'Solange eine Menge oder ein Einzelpreis keine Zahl ist, kann nicht gespeichert werden.';
const LAEDT = 'Das Angebot wird geladen …';
const OHNE_POSITION = 'Noch keine Position. „Position hinzufügen“ legt die erste an.';
const KEINE_PERSON = '';

/** Was die Maske gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | {
      readonly art: 'anlegen';
      readonly firmaId: number;
      readonly firmaName: string;
      /** Nur die aktiven Ansprechpartner stehen zur Wahl (Issue #126). */
      readonly personen: readonly Ansprechpartner[];
    }
  | {
      readonly art: 'bearbeiten';
      readonly firmaId: number;
      readonly firmaName: string;
      readonly angebotId: number;
      /** Die aktiven Ansprechpartner der Firma und der gespeicherte, auch wenn stillgelegt. */
      readonly personen: readonly Ansprechpartner[];
      /**
       * Alle Ansprechpartner der Firma, aktiv und stillgelegt (Issue #138).
       *
       * Aus ihnen entsteht {@link waehlbare} nach jedem Speichern neu. Fuehrte die Maske nur die
       * gefilterte Liste, blieb ein ersetzter stillgelegter Ansprechpartner bis zum Neuladen in
       * der Auswahl — und waehlte ihn jemand dort erneut, wies der Server das als unzulaessige
       * Neuwahl ab.
       */
      readonly alle: readonly Ansprechpartner[];
    }
  | { readonly art: 'meldung'; readonly meldung: string };

/** Die Angaben des Angebots, so wie sie in den Feldern stehen. */
interface Texte {
  /** Tag (`YYYY-MM-DD`), wie das `date`-Feld ihn fuehrt. */
  readonly angebotDatum: string;
  /** Kennung des Ansprechpartners als Text, oder {@link KEINE_PERSON}. */
  readonly ansprechpartner: string;
  readonly beschreibung: string;
}

const LEERE_TEXTE: Texte = { angebotDatum: '', ansprechpartner: '', beschreibung: '' };

/** Der Schluessel einer gespeicherten Zeile — er haengt an der Kennung, nicht an der Stelle. */
function schluesselZu(id: number): string {
  return `position-${String(id)}`;
}

/** Die Positionen der Antwort als Zeilen der Maske. */
function alsZeilen(angebot: Angebot): readonly Maskenposition[] {
  return angebot.positionen.map((position) => ({
    id: position.id,
    schluessel: schluesselZu(position.id),
    bezeichnung: position.bezeichnung,
    abrechnungsmodus: position.abrechnungsmodus,
    menge: dezimal(position.mengeInHundertsteln, ','),
    einheit: position.einheit,
    einzelpreis: dezimal(position.einzelpreisInCent, ','),
    // Was gespeichert ist, hat jemand so gewollt: Ein Modus-Wechsel setzt es nicht um (F7).
    einheitVonHand: true,
  }));
}

/** Die Angaben der Antwort als Feldinhalte — `null` wird zum leeren Feld, nicht zu „null". */
function alsTexte(angebot: Angebot): Texte {
  return {
    angebotDatum: angebot.angebotDatum,
    ansprechpartner:
      angebot.ansprechpartnerId === null ? KEINE_PERSON : String(angebot.ansprechpartnerId),
    beschreibung: angebot.beschreibung ?? '',
  };
}

/** Ein Feldinhalt als Angabe fuer den Rumpf — leer heisst „keine Angabe", nicht „leerer Text". */
function oderNull(wert: string): string | null {
  const getrimmt = wert.trim();
  return getrimmt === '' ? null : getrimmt;
}

/**
 * Eine Zeile als Positionseingabe — `null`, wenn Menge oder Preis keine Zahl sind.
 *
 * Die Reihenfolge der Felder ist die von `AngebotPositionRequest`; die Reihenfolge der Liste ist
 * die gezeigte (E24).
 *
 * Die Kennung geht mit: Eine geladene Zeile schreibt damit dieselbe Position fort, eine frische
 * schickt `null` und laesst eine neue entstehen (Plan #169, E2).
 */
function alsEingabe(position: Maskenposition): PositionEingabe | null {
  const menge = hundertstel(position.menge);
  const preis = hundertstel(position.einzelpreis);
  if (menge === null || preis === null) {
    return null;
  }
  return {
    id: position.id,
    bezeichnung: position.bezeichnung,
    abrechnungsmodus: position.abrechnungsmodus,
    menge: dezimal(menge, '.'),
    einheit: position.einheit,
    einzelpreis: dezimal(preis, '.'),
  };
}

/** Die Summe der Positionsbetraege in Cent — `null`, solange eine Zahl keine ist (Kriterium 4). */
function summeIn(positionen: readonly Maskenposition[]): number | null {
  let summe = 0;
  for (const position of positionen) {
    const cent = betragDerPosition(position);
    if (cent === null) {
      return null;
    }
    summe += cent;
  }
  return summe;
}

/**
 * Die Zahlen, die eine Zeile des internen Angebots stellvertretend traegt.
 *
 * Sie gehen nie hinaus: Die Systemgrenze streicht am internen Angebot alles ausser Kennung und
 * Bezeichnung (`api/angebote.ts`, `rumpf`). Sie stehen hier nur, weil {@link PositionEingabe} die
 * Felder verlangt — ein eigener Typ fuer dieselbe Liste waere eine zweite Form desselben Rumpfs.
 */
const OHNE_ZAHLEN = {
  abrechnungsmodus: 'AUFWAND',
  menge: '0.00',
  einheit: 'PERSONENTAG',
  einzelpreis: '0.00',
} as const;

/**
 * Die ganze Liste als Eingaben — `null`, solange eine Zahl keine ist.
 *
 * Alles oder nichts: Ein Angebot wird als Ganzes geschrieben (E8), und eine Liste, aus der die
 * unlesbaren Zeilen stillschweigend herausfielen, loeschte Positionen, die der Mensch sieht.
 *
 * <b>Am internen Angebot haelt eine unlesbare Zahl nichts auf.</b> Menge und Einzelpreis gehen von
 * dort nicht hinaus, sie sind verborgen, und die Meldung dazu stuende an der Summenzeile, die es
 * am internen Angebot nicht gibt. Ohne diese Ausnahme wiese die Maske ein Speichern stumm ab —
 * kein Hinweis, kein Netzweg, nichts.
 */
function alsEingaben(
  positionen: readonly Maskenposition[],
  intern: boolean,
): readonly PositionEingabe[] | null {
  const eingaben: PositionEingabe[] = [];
  for (const position of positionen) {
    const eingabe = alsEingabe(position);
    if (eingabe === null) {
      if (!intern) {
        return null;
      }
      eingaben.push({ ...OHNE_ZAHLEN, id: position.id, bezeichnung: position.bezeichnung });
      continue;
    }
    eingaben.push(eingabe);
  }
  return eingaben;
}

/** Eine Zeile verschieben — `richtung` ist -1 nach oben und 1 nach unten (Kriterium 6). */
function verschoben(
  positionen: readonly Maskenposition[],
  stelle: number,
  richtung: number,
): readonly Maskenposition[] {
  const neu = [...positionen];
  const [gezogen] = neu.splice(stelle, 1);
  neu.splice(stelle + richtung, 0, gezogen);
  return neu;
}

/** Die erste Stufe des Kopfpfads ueber jeder Angebotsansicht. */
const ZU_FIRMEN: PfadVerweis = { titel: 'Firmen', ziel: '/firmen' };

/** Der Kopfpfad: ueber die Firma, sobald die Maske sie kennt. */
function pfadZu(stand: Stand): readonly PfadVerweis[] {
  if (stand.art === 'anlegen' || stand.art === 'bearbeiten') {
    return [ZU_FIRMEN, { titel: stand.firmaName, ziel: `/firmen/${String(stand.firmaId)}` }];
  }
  return [ZU_FIRMEN];
}

/** Was die Maske beim Uebernehmen einer Antwort setzt — Stand, Texte und Zeilen in einem Stueck. */
interface Uebernahme {
  readonly stand: Stand;
  readonly texte: Texte;
  readonly positionen: readonly Maskenposition[];
  /** Das Kennzeichen des gelesenen Angebots — es belegt das Kaestchen (Issue #207, Kriterium 1). */
  readonly intern: boolean;
}

/**
 * Die Antwort als neuer Stand der Maske — in jedem Status zum Bearbeiten offen (Kriterium 5).
 *
 * `alle` ist die vollstaendige Personenliste der Firma; die Wahl entsteht daraus jedes Mal neu am
 * gespeicherten Ansprechpartner der Antwort (Issue #138).
 */
function uebernahme(angebot: Angebot, alle: readonly Ansprechpartner[]): Uebernahme {
  return {
    stand: {
      art: 'bearbeiten',
      firmaId: angebot.firmaId,
      firmaName: angebot.firmaName,
      angebotId: angebot.id,
      personen: waehlbare(alle, angebot.ansprechpartnerId),
      alle,
    },
    texte: alsTexte(angebot),
    positionen: alsZeilen(angebot),
    intern: angebot.intern,
  };
}

/** Die waehlbaren Ansprechpartner: die aktiven und der gespeicherte, auch wenn stillgelegt. */
function waehlbare(
  personen: readonly Ansprechpartner[],
  gespeichert: number | null,
): readonly Ansprechpartner[] {
  return personen.filter((person) => person.aktiv || person.id === gespeichert);
}

/** Die Beschriftung des Kennzeichens — in beiden Schritten dieselbe (Kriterium 1). */
const INTERN_WORT = 'Internes Projekt';

/**
 * Das Kaestchen „Internes Projekt" samt Meldung des Servers.
 *
 * Ein Baustein und nicht zweimal dasselbe Markup: Beide Schritte zeigen dasselbe Kaestchen, und
 * zwei Abschriften liefen bei der naechsten Aenderung an der Beschriftung auseinander.
 */
function InternKaestchen({
  wert,
  setze,
  meldung,
}: {
  readonly wert: boolean;
  readonly setze: (wert: boolean) => void;
  readonly meldung: string | undefined;
}) {
  // Kein `component="fieldset"`: Das Kaestchen traegt seine Beschriftung selbst, und ein Feldsatz
  // um ein einzelnes Feld waere eine Gruppe ohne Gruppe — die Positionen dieser Maske sind
  // Gruppen, und eine weitere ohne Inhalt verwirrte die Ansage des Screenreaders.
  return (
    <FormControl error={meldung !== undefined} variant="standard">
      <FormControlLabel
        control={
          <Checkbox
            checked={wert}
            onChange={(ereignis) => {
              setze(ereignis.target.checked);
            }}
          />
        }
        label={INTERN_WORT}
      />
      {meldung === undefined ? null : <FormHelperText>{meldung}</FormHelperText>}
    </FormControl>
  );
}

export default function AngebotMaske() {
  const { id, angebotId } = useParams();
  const firmaKennung = kennungAus(id);
  const angebotKennung = kennungAus(angebotId);
  const bearbeiten = angebotId !== undefined;
  const navigate = useNavigate();
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });
  const [texte, setzeTexte] = useState<Texte>(LEERE_TEXTE);
  const [positionen, setzePositionen] = useState<readonly Maskenposition[]>([]);
  const [personWahl, setzePersonWahl] = useState(KEINE_PERSON);
  /**
   * Das Kennzeichen, wie es im Kaestchen steht — in beiden Schritten derselbe Zustand.
   *
   * Nicht im {@link Stand}: Dort stuende es als „das, was der Server zuletzt sagte", und das
   * Kaestchen braucht „das, was der Mensch gerade will". Zwei Wahrheiten ueber dasselbe Kennzeichen
   * liefen beim ersten Haken auseinander.
   */
  const [intern, setzeIntern] = useState(false);
  const [eigeneFehler, setzeEigeneFehler] = useState<FieldErrors>({});
  const [feldFehler, setzeFeldFehler] = useState<FieldErrors>({});
  const [fehler, setzeFehler] = useState<string | null>(null);
  const [gespeichert, setzeGespeichert] = useState(false);
  const [laeuft, setzeLaeuft] = useState(false);
  /**
   * Der Zaehler, aus dem die Schluessel frischer Zeilen entstehen.
   *
   * Ein `useRef` und kein Zustand: Der Wert veraendert die Anzeige nicht und darf kein Neuzeichnen
   * ausloesen. Er zaehlt nur aufwaerts, auch ueber ein Speichern hinweg — ein zurueckgesetzter
   * Zaehler vergaebe einen Schluessel erneut, den es in der Liste noch gibt.
   */
  const zaehler = useRef(0);
  const naechsterSchluessel = () => {
    zaehler.current += 1;
    return `neu-${String(zaehler.current)}`;
  };
  useKopfPfad(pfadZu(stand), bearbeiten ? 'Angebot bearbeiten' : 'Neues Angebot');

  useEffect(() => {
    if (!bearbeiten) {
      if (firmaKennung === null) {
        // Eine Kennung, die keine ist, geht gar nicht erst ans Netz (kennung.ts).
        setzeStand({ art: 'meldung', meldung: NICHT_GEFUNDEN_FIRMA });
        return;
      }
      void firmaLesen(firmaKennung)
        .then((firma) => {
          setzeStand(
            firma.aktiv
              ? {
                  art: 'anlegen',
                  firmaId: firma.id,
                  firmaName: firma.name,
                  personen: firma.ansprechpartner.filter((person) => person.aktiv),
                }
              : { art: 'meldung', meldung: FIRMA_STILLGELEGT },
          );
        })
        .catch((ursache: unknown) => {
          setzeStand({
            art: 'meldung',
            meldung: nichtGefunden(ursache) ? NICHT_GEFUNDEN_FIRMA : AUSFALL_FIRMA,
          });
        });
      return;
    }
    if (angebotKennung === null) {
      setzeStand({ art: 'meldung', meldung: NICHT_GEFUNDEN_ANGEBOT });
      return;
    }
    void angebotLesen(angebotKennung)
      .then(async (angebot) => {
        const firma = await firmaLesen(angebot.firmaId);
        const neu = uebernahme(angebot, firma.ansprechpartner);
        setzeStand(neu.stand);
        setzeTexte(neu.texte);
        setzePositionen(neu.positionen);
        setzeIntern(neu.intern);
      })
      .catch((ursache: unknown) => {
        setzeStand({
          art: 'meldung',
          meldung: nichtGefunden(ursache) ? NICHT_GEFUNDEN_ANGEBOT : AUSFALL_LESEN,
        });
      });
  }, [firmaKennung, bearbeiten, angebotKennung]);

  const anlegen = async (firmaId: number) => {
    setzeFehler(null);
    setzeLaeuft(true);
    try {
      const angebot = await angebotAnlegen(
        firmaId,
        personWahl === KEINE_PERSON ? null : Number(personWahl),
        intern,
      );
      navigate(`/angebote/${String(angebot.id)}/bearbeiten`, { replace: true });
    } catch {
      setzeLaeuft(false);
      setzeFehler(AUSFALL_ANLEGEN);
    }
  };

  const speichern = async (angebot: number, alle: readonly Ansprechpartner[]) => {
    setzeGespeichert(false);
    setzeFehler(null);
    setzeFeldFehler({});
    const fehlendesDatum = texte.angebotDatum === '';
    setzeEigeneFehler(fehlendesDatum ? { angebotDatum: [DATUM_FEHLT] } : {});
    const eingaben = alsEingaben(positionen, intern);
    if (fehlendesDatum || eingaben === null) {
      // Was fehlt, steht am Feld beziehungsweise an der Summe; hinaus geht nichts.
      return;
    }
    setzeLaeuft(true);
    try {
      const neu = uebernahme(
        await angebotAendern(angebot, {
          angebotDatum: texte.angebotDatum,
          ansprechpartnerId:
            texte.ansprechpartner === KEINE_PERSON ? null : Number(texte.ansprechpartner),
          beschreibung: oderNull(texte.beschreibung),
          intern,
          positionen: eingaben,
        }),
        alle,
      );
      setzeStand(neu.stand);
      setzeTexte(neu.texte);
      setzePositionen(neu.positionen);
      setzeIntern(neu.intern);
      setzeGespeichert(true);
    } catch (ursache) {
      const felder = feldMeldungen(ursache);
      if (Object.keys(felder).length > 0) {
        setzeFeldFehler(felder);
      } else {
        setzeFehler(AUSFALL_SPEICHERN);
      }
    } finally {
      setzeLaeuft(false);
    }
  };

  const meldung = (feld: string) =>
    meldungAm(eigeneFehler, feld) ?? meldungAm(feldFehler, feld);
  const summe = summeIn(positionen);

  /** Der Rahmen jeder Bereitschaft: der Abstand zwischen den Karten der Buehne. */
  const spalten = { display: 'flex', flexDirection: 'column', gap: '22px' } as const;

  if (stand.art === 'laedt') {
    return (
      <Box sx={spalten}>
        <Karte>
          <Typography
            sx={(theme) => ({
              fontSize: 12.5,
              color: theme.vars.palette.kupferwolke.textSchwach,
            })}
          >
            {LAEDT}
          </Typography>
        </Karte>
      </Box>
    );
  }

  if (stand.art === 'meldung') {
    return (
      <Box sx={spalten}>
        <Karte>
          <Alert severity="error">{stand.meldung}</Alert>
        </Karte>
      </Box>
    );
  }

  if (stand.art === 'anlegen') {
    const firmaId = stand.firmaId;
    return (
      <Box
        component="form"
        noValidate
        onSubmit={(ereignis: FormEvent<HTMLFormElement>) => {
          ereignis.preventDefault();
          void anlegen(firmaId);
        }}
        sx={spalten}
      >
        <Karte titel="Neues Angebot" titelEbene={1}>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {fehler === null ? null : <Alert severity="error">{fehler}</Alert>}
            <Typography
              data-testid="angebot-firma"
              sx={(theme) => ({ fontSize: 13, color: theme.vars.palette.kupferwolke.textMatt })}
            >
              {`An: ${stand.firmaName}`}
            </Typography>
            <TextField
              select
              label="Ansprechpartner"
              value={personWahl}
              onChange={(ereignis) => {
                setzePersonWahl(ereignis.target.value);
              }}
              helperText="Optional. Zur Wahl stehen die aktiven Ansprechpartner dieser Firma."
              fullWidth
              slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
            >
              <option value={KEINE_PERSON}>— keiner —</option>
              {stand.personen.map((person) => (
                <option key={person.id} value={person.id}>
                  {namensZug(person)}
                </option>
              ))}
            </TextField>
            <InternKaestchen wert={intern} setze={setzeIntern} meldung={meldung('intern')} />
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, flexWrap: 'wrap' }}>
              <KupferTaste disabled={laeuft}>Anlegen</KupferTaste>
              <WeicheTaste to={`/firmen/${String(firmaId)}`}>Abbrechen</WeicheTaste>
            </Box>
          </Box>
        </Karte>
      </Box>
    );
  }

  const zuAendern = stand.angebotId;
  const personen = stand.personen;
  const allePersonen = stand.alle;
  return (
    <Box
      component="form"
      noValidate
      onSubmit={(ereignis: FormEvent<HTMLFormElement>) => {
        ereignis.preventDefault();
        void speichern(zuAendern, allePersonen);
      }}
      sx={spalten}
    >
      <Karte titel="Angebot bearbeiten" titelEbene={1}>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {fehler === null ? null : <Alert severity="error">{fehler}</Alert>}
          {gespeichert ? (
            <Typography
              role="status"
              sx={(theme) => ({
                fontSize: 12.5,
                color: theme.vars.palette.kupferwolke.toenung.salbei.schrift,
              })}
            >
              {GESPEICHERT}
            </Typography>
          ) : null}
          <TextField
            label="Angebotsdatum"
            type="date"
            value={texte.angebotDatum}
            onChange={(ereignis) => {
              setzeTexte({ ...texte, angebotDatum: ereignis.target.value });
            }}
            error={meldung('angebotDatum') !== undefined}
            helperText={meldung('angebotDatum')}
            required
            fullWidth
            // Ein `date`-Feld zeigt immer seine Maske, also darf die Beschriftung nie im Feld
            // liegen.
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            select
            label="Ansprechpartner"
            value={texte.ansprechpartner}
            onChange={(ereignis) => {
              setzeTexte({ ...texte, ansprechpartner: ereignis.target.value });
            }}
            error={meldung('ansprechpartnerId') !== undefined}
            helperText={
              meldung('ansprechpartnerId') ??
              'Optional. Zur Wahl stehen die aktiven Ansprechpartner dieser Firma.'
            }
            fullWidth
            slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
          >
            <option value={KEINE_PERSON}>— keiner —</option>
            {personen.map((person) => (
              <option key={person.id} value={person.id}>
                {person.aktiv ? namensZug(person) : `${namensZug(person)} (stillgelegt)`}
              </option>
            ))}
          </TextField>
          <TextField
            label="Beschreibung"
            value={texte.beschreibung}
            onChange={(ereignis) => {
              setzeTexte({ ...texte, beschreibung: ereignis.target.value });
            }}
            error={meldung('beschreibung') !== undefined}
            helperText={meldung('beschreibung')}
            multiline
            minRows={3}
            fullWidth
          />
          <InternKaestchen wert={intern} setze={setzeIntern} meldung={meldung('intern')} />
        </Box>
      </Karte>
      <Karte
        titel="Positionen"
        anzahl={positionen.length}
        werkzeug={
          <WeicheTaste
            onClick={() => {
              setzePositionen([...positionen, frischePosition(naechsterSchluessel())]);
            }}
            symbol={<IconPlus size={16} stroke={1.8} />}
          >
            Position hinzufügen
          </WeicheTaste>
        }
      >
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {/*
            Die Meldung zur Liste als Ganzes — etwa, dass eine berechnete Position erhalten bleiben
            muss (#160, Kriterium 28). Sie kommt als Feldmeldung an `positionen` und steht darum
            hier und nicht oben: Sie gilt der Liste, nicht einer einzelnen Zeile, und die Zeile, um
            die es geht, hat der Mensch moeglicherweise gerade geloescht. Die Eingaben bleiben dabei
            stehen — ein abgewiesenes Speichern ist nichts Verlorenes.
          */}
          {meldung('positionen') === undefined ? null : (
            <Alert severity="error">{meldung('positionen')}</Alert>
          )}
          {positionen.length === 0 ? (
            <Typography
              role="status"
              sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textMatt })}
            >
              {OHNE_POSITION}
            </Typography>
          ) : (
            positionen.map((position, stelle) => (
              <Positionsmaske
                // Der Schluessel der Zeile und nicht ihre Stelle: Beim Umordnen wandert die Stelle,
                // und React gaebe der wandernden Zeile den Zustand ihres Nachbarn. Gespeicherte
                // Zeilen tragen ihn aus ihrer Kennung, frische aus dem Zaehler der Maske.
                key={position.schluessel}
                nummer={stelle + 1}
                position={position}
                aendere={(neu) => {
                  setzePositionen(positionen.map((alt, index) => (index === stelle ? neu : alt)));
                }}
                loesche={() => {
                  setzePositionen(positionen.filter((_alt, index) => index !== stelle));
                }}
                nachOben={() => {
                  setzePositionen(verschoben(positionen, stelle, -1));
                }}
                nachUnten={() => {
                  setzePositionen(verschoben(positionen, stelle, 1));
                }}
                erste={stelle === 0}
                letzte={stelle === positionen.length - 1}
                bezeichnungFehler={meldung(`positionen[${String(stelle)}].bezeichnung`)}
                intern={intern}
              />
            ))
          )}
          {intern ? null : (
          <Box
            sx={(theme) => ({
              display: 'flex',
              alignItems: 'baseline',
              gap: '10px',
              flexWrap: 'wrap',
              paddingTop: '14px',
              borderTop: `1px solid ${theme.vars.palette.kupferwolke.linie}`,
            })}
          >
            <Typography sx={{ fontSize: 13.5, fontWeight: 600 }}>Summe (netto)</Typography>
            {summe === null ? (
              <>
                <Typography
                  data-testid="angebot-summe"
                  className={ZAHLEN_KLASSE}
                  sx={{ fontSize: 17, fontWeight: 800 }}
                >
                  —
                </Typography>
                <Typography
                  role="status"
                  sx={(theme) => ({
                    fontSize: 12.5,
                    color: theme.vars.palette.kupferwolke.toenung.rose.schrift,
                  })}
                >
                  {ZAHLEN_UNKLAR}
                </Typography>
              </>
            ) : (
              <Typography
                data-testid="angebot-summe"
                className={ZAHLEN_KLASSE}
                sx={{ fontSize: 17, fontWeight: 800 }}
              >
                {euro(summe)}
              </Typography>
            )}
          </Box>
          )}
        </Box>
      </Karte>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, flexWrap: 'wrap' }}>
        <KupferTaste disabled={laeuft}>Speichern</KupferTaste>
        <WeicheTaste to={`/angebote/${String(zuAendern)}`}>Zum Angebot</WeicheTaste>
      </Box>
    </Box>
  );
}
