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

import {
  ALLE,
  arbeitszeitMonat,
  buchbarePositionen,
  personen,
  zeiteintragAendern,
  zeiteintragAnlegen,
} from '../api/arbeitszeit';
import type {
  Arbeitsmonat,
  Buchungsposition,
  Person,
  Personenauswahl,
  ZeiteintragEingabe,
  Zeitzeile,
} from '../api/arbeitszeit';
import type { FieldErrors } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { useRechte } from '../auth/rechte';
import { feldMeldungen, istBearbeitungskonflikt } from '../lib/apifehler';
import { dauerInHundertsteln, stundenWort, uhrzeitFeld } from '../lib/arbeitszeit';
import { meldungAm } from '../lib/feldmeldung';
import { heute, tagWort } from '../lib/tag';
import { ZAHLEN_KLASSE } from '../theme';
import EigeneEingaben from './EigeneEingaben';
import type { EigeneEingabe } from './EigeneEingaben';
import Konfliktmeldung from './Konfliktmeldung';
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
 *
 * <b>Ein C-Level waehlt die Person, fuer die er erfasst</b> (Plan #426, E12; Kriterium 3 von
 * #410), ueber der Position; ein Employee sieht die Auswahl nicht und nennt nie eine Person — sein
 * Rumpf traegt kein `accountId`, und der Eintrag gehoert ihm. Die Personen holt der Dialog selbst,
 * wie seine Positionen. Beim Anlegen ist vorbelegt, worauf die Ansicht umgeschaltet ist: eine
 * aktive Person, bei „Alle" das angemeldete Konto, bei einer stillgelegten niemand — stillgelegte
 * Personen stehen beim Anlegen nicht zur Wahl. Beim Aendern steht die Person der Zeile voran, auch
 * eine stillgelegte, aus demselben Grund wie bei der Position (A15): Fehlte sie, haenge ein
 * Speichern den Eintrag stillschweigend um. Der Server taete das nicht (E12) — die Maske verlaesst
 * sich nicht darauf.
 *
 * <b>Das Aendern legt den Stand der Zeile vor</b> (#411, Kriterien 1 bis 3; Plan #415, E2, E12):
 * Hat jemand den Eintrag seit dem Laden der Monatsliste geaendert, weist der Server ab, und die
 * {@link Konfliktmeldung} sagt, wer wann. Die eigenen Eingaben bleiben im Dialog stehen. „Neu
 * laden" holt den Eintrag aus dem Monat seines Tages und der Sicht seiner Person — einen Abruf je
 * Eintrag kennt die Schnittstelle nicht —, setzt ihn samt neuem Stand in die Felder und stellt die
 * eigenen Werte daneben in {@link EigeneEingaben}, bis „Verwerfen" gewaehlt wird. Liegt der
 * Eintrag dort nicht mehr, weil er inzwischen an einen anderen Monat oder eine andere Person ging,
 * sagt der Dialog das; die eigenen Werte stehen trotzdem zum Kopieren da. Das Erfassen legt keinen
 * Stand vor (Kriterium 7).
 *
 * <b>Ohne Schreibrecht entsteht der Dialog gar nicht</b> (Kriterium 10 von #409, Plan #414, A10):
 * Er dient nur dem Schreiben, und die Monatsliste oeffnet ihn dann ohnehin nicht. Die Huelle fragt
 * das Recht vor dem eigentlichen Dialog, damit auch seine Auswahlliste nicht geladen wird.
 */

/** Die Schrittweite der Zeitfelder in Sekunden — eine Viertelstunde (A16, Antwort 6). */
const SCHRITT = 900;

const LAEDT = 'Die buchbaren Positionen werden geladen …';
const AUSFALL_LESEN =
  'Die buchbaren Positionen sind gerade nicht zu erreichen. Bitte später erneut versuchen.';
const LEER =
  'Keine Position ist buchbar. Eine Position muss nach Aufwand in Stunden abgerechnet werden, und' +
  ' ihr Angebot muss bestellt oder erledigt sein.';
const AUSFALL_PERSONEN =
  'Die Personen sind gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_SPEICHERN = 'Die Arbeitszeit wurde nicht gespeichert. Bitte später erneut versuchen.';
const AUSFALL_NEU_LADEN =
  'Der aktuelle Stand ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const NICHT_MEHR_DA =
  'Der Eintrag steht nicht mehr an seinem Monat und bei seiner Person. Schließen Sie den Dialog' +
  ' und öffnen Sie ihn in der Liste neu; Ihre Eingaben stehen unten zum Kopieren.';

/** Wohin die Konfliktmeldung fuehrt, wenn es den Eintrag nicht mehr gibt. */
const UEBERSICHT = '/arbeitszeit';

/** Was an einem Feld steht, das eine Angabe braucht und keine hat. */
const PFLICHT = 'Bitte ausfüllen.';

/** Die Aufschrift einer Auswahl, solange nichts gewaehlt ist. */
const KEINE_WAHL = 'Bitte wählen';

export interface ZeiteintragMaskeProps {
  /** Der Eintrag, der geaendert wird — `null` beim Erfassen. */
  readonly zeile: Zeitzeile | null;
  /**
   * Worauf die Ansicht umgeschaltet ist — die Vorbelegung der Person beim Erfassen; `null`, solange
   * die Ansicht keine Auswahl kennt, gilt wie „Alle". Beim Aendern ohne Belang.
   */
  readonly auswahl: Personenauswahl | null;
  /** Gespeichert: Die Monatsliste ist nicht mehr aktuell und der Dialog hat seinen Zweck erfuellt. */
  readonly onGespeichert: () => void;
  /** „Neu laden" hat den Stand des Servers geholt — die Monatsliste dahinter ist nicht mehr aktuell. */
  readonly onNeuGeladen?: () => void;
  /** Abgebrochen — ohne Wirkung auf den Bestand. */
  readonly onSchliessen: () => void;
}

/** Die Felder des Dialogs, jedes als Zeichenkette — leer heisst „keine Angabe". */
interface Werte {
  readonly tag: string;
  readonly von: string;
  readonly bis: string;
  readonly angebotPositionId: string;
  /** Die gewaehlte Person; nur fuer ein C-Level von Belang, sonst bleibt es leer. */
  readonly accountId: string;
}

/** Die vier Angaben, und keine davon darf fehlen (`ZeiteintragRequest`). */
const PFLICHTFELDER: readonly (keyof Werte)[] = ['tag', 'von', 'bis', 'angebotPositionId'];

/** Mit Personenauswahl ist auch die Person Pflicht — ohne sie wuesste der Dialog nicht, fuer wen. */
const PFLICHTFELDER_MIT_PERSON: readonly (keyof Werte)[] = [...PFLICHTFELDER, 'accountId'];

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

/** Was die Personenauswahl gerade weiss. */
type Personenstand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly personen: readonly Person[] }
  | { readonly art: 'fehler' };

/** Holt die Personen und macht auch aus dem Fehlschlag einen Stand. */
async function personenLaden(): Promise<Personenstand> {
  try {
    return { art: 'daten', personen: await personen() };
  } catch {
    return { art: 'fehler' };
  }
}

/** Holt die buchbaren Positionen und macht auch aus dem Fehlschlag einen Stand. */
async function wahlLaden(): Promise<Wahlstand> {
  try {
    return { art: 'daten', positionen: await buchbarePositionen() };
  } catch {
    return { art: 'fehler' };
  }
}

/**
 * Die Vorbelegung: beim Aendern die Zeile samt ihrer Person, beim Erfassen der heutige Tag und
 * sonst nichts — die Person beim Erfassen setzt {@link personBeimErfassen}, sobald die Personen da
 * sind.
 */
function werteAus(zeile: Zeitzeile | null): Werte {
  if (zeile === null) {
    return { tag: heute(), von: '', bis: '', angebotPositionId: '', accountId: '' };
  }
  return {
    tag: zeile.tag,
    von: uhrzeitFeld(zeile.von),
    bis: uhrzeitFeld(zeile.bis),
    angebotPositionId: String(zeile.position.id),
    accountId: String(zeile.person.id),
  };
}

/**
 * Wen das Erfassen vorbelegt (Kriterium 3 von #410, E12): bei „Alle" das angemeldete Konto, sonst
 * die umgeschaltete Person, wenn sie aktiv ist — und niemanden, wenn sie stillgelegt ist oder der
 * Server sie nicht nennt.
 */
function personBeimErfassen(
  auswahl: Personenauswahl | null,
  kontoId: number,
  liste: readonly Person[],
): string {
  if (auswahl === null || auswahl === ALLE) {
    return String(kontoId);
  }
  return liste.some((person) => person.id === auswahl && person.aktiv) ? String(auswahl) : '';
}

/**
 * Die Personen, fuer die erfasst werden darf: die aktiven, und beim Aendern die Person der Zeile
 * vorn, wenn sie nicht darunter ist — die einzige Ausnahme fuer eine stillgelegte (wie A15).
 */
function waehlbarePersonen(zeile: Zeitzeile | null, liste: readonly Person[]): readonly Person[] {
  const aktive = liste.filter((person) => person.aktiv);
  if (zeile === null || aktive.some((person) => person.id === zeile.person.id)) {
    return aktive;
  }
  return [zeile.person, ...aktive];
}

/**
 * Was gerade im Dialog steht, als eigene Eingaben zum Kopieren (#411, Kriterium 3) — beschriftet
 * wie die Felder. Position und Person stehen mit ihrem Namen da, nicht mit ihrer Kennung; was die
 * Auswahl nicht kennt, steht als Kennung, damit nichts still verschwindet.
 */
function eigeneEingabenAus(
  werte: Werte,
  positionen: readonly Buchungsposition[],
  personenwahl: readonly Person[] | null,
): readonly EigeneEingabe[] {
  const position = positionen.find((kandidat) => String(kandidat.id) === werte.angebotPositionId);
  const eingaben: EigeneEingabe[] = [
    { bezeichnung: 'Tag', wert: tagWort(werte.tag) },
    { bezeichnung: 'von', wert: werte.von },
    { bezeichnung: 'bis', wert: werte.bis },
  ];
  if (personenwahl !== null) {
    const person = personenwahl.find((kandidat) => String(kandidat.id) === werte.accountId);
    eingaben.push({ bezeichnung: 'Person', wert: person?.name ?? werte.accountId });
  }
  eingaben.push({
    bezeichnung: 'Position',
    wert:
      position === undefined
        ? werte.angebotPositionId
        : `${position.firmaName} — ${position.bezeichnung}`,
  });
  return eingaben;
}

/** Der Eintrag in einem gelesenen Monat, oder `undefined`, wo er dort nicht (mehr) steht. */
function eintragIn(monat: Arbeitsmonat, id: number): Zeitzeile | undefined {
  return monat.tage.flatMap((arbeitstag) => arbeitstag.eintraege).find((zeile) => zeile.id === id);
}

/** Die Felder ohne Angabe, jedes mit seiner Meldung — leer heisst „vollstaendig". */
function fehlendeAngaben(werte: Werte, mitPerson: boolean): FieldErrors {
  const fehlen: Record<string, readonly string[]> = {};
  for (const feld of mitPerson ? PFLICHTFELDER_MIT_PERSON : PFLICHTFELDER) {
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
 * <b>Die Gruppe heisst „<Firma> — <Titel>"</b>, wo das Angebot einen Titel traegt, sonst wie bisher
 * „<Firma> — Angebot vom <Datum>" (Issue #328; Plan #323, E11): Zwei Angebote derselben Firma vom
 * selben Tag hiessen sonst gleich. Die Optionen darunter benennen weiter die Position.
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
    if (letzte?.angebotId === position.angebotId) {
      letzte.positionen.push(position);
    } else {
      gruppen.push({
        angebotId: position.angebotId,
        name:
          `${position.firmaName} — ${angebotsname(position)}` +
          (position.intern ? INTERN_ZUSATZ : ''),
        positionen: [position],
      });
    }
  }
  return gruppen;
}

/** Wie die Gruppe das Angebot nennt: mit seinem Titel, ohne Titel wie bisher mit seinem Datum. */
function angebotsname(position: Buchungsposition): string {
  return position.angebotTitel ?? `Angebot vom ${tagWort(position.angebotDatum)}`;
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

/**
 * Die Huelle: Sie fragt Recht und Sitzung, bevor der Dialog entsteht.
 *
 * <b>Die Personenauswahl bekommt, wer schreiben und die Zeiten aller sehen darf</b> — das ist
 * allein ein C-Level (`auth/rechte`); gefragt wird das Recht, nicht die Rolle. Das angemeldete
 * Konto kommt aus der Sitzung, verengt ueber `status === 'angemeldet'` (E17).
 */
export default function ZeiteintragMaske(props: ZeiteintragMaskeProps) {
  const { darfFachlichSchreiben, darfZeitenAllerSehen } = useRechte();
  const { sitzung } = useAuth();
  if (!darfFachlichSchreiben || sitzung.status !== 'angemeldet') {
    return null;
  }
  return (
    <Zeiteintragsdialog
      {...props}
      kontoId={sitzung.konto.id}
      mitPerson={darfZeitenAllerSehen}
    />
  );
}

interface DialogProps extends ZeiteintragMaskeProps {
  /** Die Kennung des angemeldeten Kontos — die Vorbelegung bei „Alle". */
  readonly kontoId: number;
  /** Ob der Dialog die Person waehlen laesst (nur ein C-Level). */
  readonly mitPerson: boolean;
}

/** Der Dialog selbst — nur fuer eine Rolle, die fachlich schreiben darf. */
function Zeiteintragsdialog({
  zeile: geoeffnet,
  auswahl,
  kontoId,
  mitPerson,
  onGespeichert,
  onNeuGeladen,
  onSchliessen,
}: DialogProps) {
  const titelId = useId();
  const [wahlstand, setzeWahlstand] = useState<Wahlstand>({ art: 'laedt' });
  const [personenstand, setzePersonenstand] = useState<Personenstand>({ art: 'laedt' });
  const [werte, setzeWerte] = useState<Werte>(() => werteAus(geoeffnet));
  const [eigeneFehler, setzeEigeneFehler] = useState<FieldErrors>({});
  const [feldFehler, setzeFeldFehler] = useState<FieldErrors>({});
  const [fehler, setzeFehler] = useState<string | null>(null);
  const [laeuft, setzeLaeuft] = useState(false);
  // Der Eintrag, auf dem das Aendern beruht: geoeffnet, oder aus „Neu laden" — samt seinem Stand.
  const [zeile, setzeZeile] = useState<Zeitzeile | null>(geoeffnet);
  // Die Abweisung eines Speicherns auf altem Stand, ungeprueft — die Konfliktmeldung liest sie selbst.
  const [abweisung, setzeAbweisung] = useState<unknown>(null);
  // Die eigenen Werte nach „Neu laden", bis „Verwerfen" gewaehlt wird (#411, Kriterium 3).
  const [eigene, setzeEigene] = useState<readonly EigeneEingabe[] | null>(null);

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

  useEffect(() => {
    // Ein Employee waehlt keine Person und fragt darum auch keine.
    if (!mitPerson) {
      return undefined;
    }
    let gueltig = true;
    void personenLaden().then((neu) => {
      if (!gueltig) {
        return;
      }
      setzePersonenstand(neu);
      // Beim Aendern steht die Person der Zeile schon; beim Erfassen entscheidet erst die Liste,
      // ob die umgeschaltete Person aktiv ist.
      if (geoeffnet === null && neu.art === 'daten') {
        const vorbelegt = personBeimErfassen(auswahl, kontoId, neu.personen);
        setzeWerte((alt) => ({ ...alt, accountId: vorbelegt }));
      }
    });
    return () => {
      gueltig = false;
    };
  }, [mitPerson, geoeffnet, auswahl, kontoId]);

  const setze = (feld: keyof Werte, wert: string) => {
    setzeWerte((alt) => ({ ...alt, [feld]: wert }));
  };

  const absenden = async (ereignis: FormEvent<HTMLFormElement>) => {
    ereignis.preventDefault();
    setzeFehler(null);
    setzeAbweisung(null);
    setzeFeldFehler({});
    const fehlen = fehlendeAngaben(werte, mitPerson);
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
      ...(mitPerson ? { accountId: Number(werte.accountId) } : {}),
    };
    try {
      if (zeile === null) {
        await zeiteintragAnlegen(eingabe);
      } else {
        await zeiteintragAendern(zeile.id, zeile.version, eingabe);
      }
      // Kein `setzeLaeuft(false)`: Der Dialog hat seinen Zweck erfuellt, und die Monatsliste baut
      // ihn ab. Ein freigegebenes Speichern waere ein zweiter Eintrag auf einen Klick.
      onGespeichert();
    } catch (ursache) {
      setzeLaeuft(false);
      const felder = feldMeldungen(ursache);
      if (istBearbeitungskonflikt(ursache)) {
        // Die Eingaben bleiben stehen; den Weg weiter zeigt die Konfliktmeldung (Kriterium 3).
        setzeAbweisung(ursache);
      } else if (Object.keys(felder).length > 0) {
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
  const personenwahl = waehlbarePersonen(
    zeile,
    personenstand.art === 'daten' ? personenstand.personen : [],
  );
  const positionen = mitZeilenposition(
    zeile,
    wahlstand.art === 'daten' ? wahlstand.positionen : [],
  );
  const gruppen = gruppiert(positionen);

  /**
   * „Neu laden" nach einer Abweisung: der Stand des Servers in die Felder, die eigenen Werte daneben
   * (#411, Kriterium 3; Plan #415, E12).
   *
   * Gefragt wird der Monat des Tages, an dem der Eintrag stand, in der Sicht seiner Person — dort
   * steht er, solange ihn niemand an einen anderen Monat oder eine andere Person gegeben hat. Die
   * eigenen Werte werden genommen, wie sie in diesem Augenblick im Dialog stehen.
   */
  const neuLaden = async (bisher: Zeitzeile) => {
    const meine = eigeneEingabenAus(werte, positionen, mitPerson ? personenwahl : null);
    setzeFehler(null);
    setzeLaeuft(true);
    try {
      const monat = await arbeitszeitMonat(bisher.tag.slice(0, 7), bisher.person.id);
      const frisch = eintragIn(monat, bisher.id);
      setzeEigene(meine);
      setzeAbweisung(null);
      if (frisch === undefined) {
        setzeFehler(NICHT_MEHR_DA);
      } else {
        setzeZeile(frisch);
        setzeWerte(werteAus(frisch));
        setzeEigeneFehler({});
        setzeFeldFehler({});
      }
      onNeuGeladen?.();
    } catch {
      setzeFehler(AUSFALL_NEU_LADEN);
    } finally {
      setzeLaeuft(false);
    }
  };

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
          {zeile === null ? null : (
            <Konfliktmeldung
              ursache={abweisung}
              onNeuLaden={() => {
                void neuLaden(zeile);
              }}
              uebersicht={UEBERSICHT}
            />
          )}
          {eigene === null ? null : (
            <EigeneEingaben
              werte={eigene}
              onVerwerfen={() => {
                setzeEigene(null);
              }}
            />
          )}
          {mitPerson && personenstand.art === 'fehler' ? (
            <Alert severity="error">{AUSFALL_PERSONEN}</Alert>
          ) : null}
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
          {mitPerson ? (
            <TextField
              select
              label="Person"
              value={werte.accountId}
              onChange={(ereignis) => {
                setze('accountId', ereignis.target.value);
              }}
              error={meldung('accountId') !== undefined}
              helperText={meldung('accountId')}
              required
              fullWidth
              slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
            >
              <option value="">{KEINE_WAHL}</option>
              {personenwahl.map((person) => (
                <option key={person.id} value={String(person.id)}>
                  {person.name}
                </option>
              ))}
            </TextField>
          ) : null}
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
