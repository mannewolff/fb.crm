import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import Link from '@mui/material/Link';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import {
  IconCircleCheck,
  IconCircleX,
  IconDeviceFloppy,
  IconDownload,
  IconFileInvoice,
  IconRotate2,
  IconTrash,
} from '@tabler/icons-react';
import { Fragment, useEffect, useId, useState } from 'react';
import type { ReactNode } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';

import type { Einheit } from '../api/angebote';
import type { FieldErrors } from '../api/client';
import {
  rechnungAendern,
  rechnungDokumentPfad,
  rechnungLesen,
  rechnungLoeschen,
  rechnungStellen,
  setzeRechnungszustand,
} from '../api/rechnungen';
import type { AbrechnungsangabeEingabe, Rechnung, Rechnungsmaskenzeile } from '../api/rechnungen';
import AktionsMenue from '../components/AktionsMenue';
import Karte from '../components/Karte';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import KupferTaste, { kupferSx } from '../components/KupferTaste';
import { EINHEIT_WORT } from '../components/Positionsmaske';
import RechnungszustandChip from '../components/RechnungszustandChip';
import Tafel, { type TafelSpalte } from '../components/Tafel';
import Ueberschreitungshinweis from '../components/Ueberschreitungshinweis';
import TastenSymbol from '../components/TastenSymbol';
import WeicheTaste from '../components/WeicheTaste';
import { rechnungssummen, ueberschreitung } from '../lib/abrechnung';
import type { Rechnungssummen } from '../lib/abrechnung';
import { feldMeldungen, nichtGefunden, serverMeldung } from '../lib/apifehler';
import { meldungAm } from '../lib/feldmeldung';
import { betrag, dezimal, euro, hundertstel } from '../lib/geld';
import { kennungAus } from '../lib/kennung';
import { istGestellt } from '../lib/rechnungszustand';
import type { Rechnungszustand } from '../lib/rechnungszustand';
import { KEIN_ZEITRAUM, tagWort } from '../lib/tag';
import { ABSTAND_BUEHNE, RADIUS_RUND, TASTE_INNENABSTAND, ZAHLEN_KLASSE } from '../theme';

/**
 * Die Seite der einzelnen Rechnung: im Entwurf die Maske der Teilabrechnung (Issue #185), nach dem
 * Stellen der fertige Beleg (Issue #186).
 *
 * Oben die Kopfkarte mit Ueberschrift, Angaben und Aktionen; darunter im Entwurf die Angaben zur
 * Rechnung und die Positionstafel mit „jetzt abrechnen" und den drei Summen. Die <b>gestellte</b>
 * Rechnung zeigt dieselben Bereiche als Leseansicht — Stammdaten, Positionen, Summen — mit
 * „Herunterladen" als Hauptaktion und dem Ausgang der Forderung daneben.
 *
 * <b>Die Leseansicht gilt fuer jeden gestellten Zustand</b> (Issue #253): Bezahlt und Abgeschrieben
 * sind gestellte Rechnungen und zeigen denselben Beleg mit derselben Nummer und demselben Dokument.
 * Gefragt wird darum `istGestellt` und nicht `=== 'GESTELLT'`. Umgestellt wird hier und nicht in der
 * Listenzeile: Die Liste traegt bisher keine Aktion ausser dem Download, und der kleinste Eingriff
 * liegt dort, wo die gestellte Rechnung schon ihre Werkzeuge hat. Ein unzulaessiger Uebergang kommt
 * als 409 mit der Meldung des Servers und erscheint ueber der Ansicht — die Seite bleibt stehen.
 *
 * <b>Die Maske zeigt jede Position des Angebots</b>, auch eine, die dieser Entwurf nicht abrechnet
 * (Plan #169, E5). So sieht der Freiberufler beim Wiederoeffnen, was er beim ersten Mal weggelassen
 * hat. Hinaus geht darum ebenfalls jede Zeile — eine Menge 0 laesst die Position aus der Rechnung
 * herausfallen (`RechnungPositionRequest`), und so bleibt die Stelle einer Zeile im Rumpf dieselbe
 * wie in der Tafel. Nur deshalb trifft ein Feldfehler `positionen[n].bezeichnung` des Servers die
 * Zeile, die der Mensch sieht. <b>Die gestellte Rechnung zeigt diese Zeilen nicht</b>: Auf einem
 * Beleg steht, was berechnet wird, und eine Zeile ueber 0,00 € steht auf keiner Rechnung.
 *
 * <b>Die Liste ist Zustand der Seite</b>, wie in {@link AngebotMaske}: Getippt wird im Zustand,
 * geschickt wird beim Speichern als Ganzes. Nach dem Speichern gilt die Antwort — Mengen, Betraege
 * und Summen stehen danach so da, wie der Server sie fuehrt. Die Maske bleibt dabei stehen; sie ist
 * die Werkbank an der Rechnung.
 *
 * <b>Gerechnet wird mitgetippt und in ganzen Zahlen</b> (`lib/abrechnung.ts`): Betrag je Zeile auf
 * den Cent, Steuer aus der Netto-Summe — dieselbe Reihenfolge der Rundungen wie im Backend. Sonst
 * sprang der Betrag beim Speichern um einen Cent. Die <b>gestellte</b> Rechnung rechnet nichts mit:
 * Netto, Steuer und Brutto stehen fest und kommen aus der Antwort.
 *
 * <b>Eine Menge ueber dem Offenen verhindert nichts</b> (Kriterium 8): Sie steht als Hinweis mit
 * Wort und Symbol an der Zeile, und gespeichert wird trotzdem. Eine Teilabrechnung ueber das
 * Angebot hinaus ist eine Entscheidung des Freiberuflers, kein Eingabefehler. Was dagegen <b>keine
 * Menge</b> ist — leeres Feld, Buchstaben, drei Nachkommastellen, ein Minus —, meldet sich am Feld
 * und haelt das Speichern auf; ein stiller Ersatzwert waere ein Betrag, den niemand eingegeben hat.
 *
 * <b>„Rechnung stellen" ist die eine Kupfertaste des Entwurfs</b>, „Speichern" tritt als weiche
 * Taste daneben zurueck (CLAUDE-design.md, „Tasten"): Das Speichern ist ein Zwischenstand, das
 * Stellen der Zweck der Maske. Vor dem Stellen gehen ungespeicherte Aenderungen hinaus — sonst
 * stuende auf dem Beleg etwas anderes, als der Mensch gerade liest —, und erst danach fragt die
 * Seite nach. <b>Die Rueckfrage nennt den Bruttobetrag der Antwort</b> und nicht die mitgetippte
 * Summe: Bestaetigt wird, was der Server gleich festschreibt.
 *
 * <b>„Entwurf löschen" steht im ⋯-Menue mit Rueckfrage</b> (CLAUDE-design.md, „Tasten"): Es ist
 * nicht umkehrbar und steht darum nicht gleichrangig neben den Tasten der Kopfkarte.
 *
 * <b>Kein Formular um die Maske</b> (seit Issue #186): Beide Tasten des Entwurfs sind Schalter —
 * „Speichern", weil es neben der Hauptaktion steht, und „Rechnung stellen", weil die Eingabetaste
 * in einem Feld keinen nicht umkehrbaren Schritt anstossen soll.
 *
 * Laden, „gibt es nicht" (404) und Ausfall wie in {@link AngebotPage}: Eine Kennung, die keine ist,
 * geht gar nicht erst ans Netz (`lib/kennung.ts`).
 */

const NICHT_GEFUNDEN = 'Diese Rechnung gibt es nicht.';
const AUSFALL = 'Die Rechnung ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_SPEICHERN = 'Die Rechnung wurde nicht gespeichert. Bitte später erneut versuchen.';
const AUSFALL_LOESCHEN = 'Der Entwurf wurde nicht gelöscht. Bitte später erneut versuchen.';
const AUSFALL_STELLEN = 'Die Rechnung wurde nicht gestellt. Bitte später erneut versuchen.';
const LAEDT = 'Die Rechnung wird geladen …';
const GESPEICHERT = 'Gespeichert.';
const DATUM_FEHLT = 'Bitte das Datum der Rechnung angeben.';
const ZEITRAUM_FEHLT = 'Die Rechnung braucht einen Leistungszeitraum.';
const BEZEICHNUNG_FEHLT = 'Jede Position braucht eine Bezeichnung.';
const MENGE_UNKLAR =
  'Bitte eine Menge mit höchstens zwei Nachkommastellen angeben, nicht negativ.';
const LOESCHEN_FRAGE =
  'Der Entwurf wird mit allen Positionen gelöscht. Das lässt sich nicht zurücknehmen.';
const STELLEN = 'Rechnung stellen';
const OHNE_MENGE =
  'Ohne eine Position mit einer Menge über 0 lässt sich die Rechnung nicht stellen.';
const PFLICHTANGABEN = 'Für das Stellen fehlen noch Angaben:';
const KEIN_ZIEL = 'nicht festgelegt';
const BEZAHLT = 'Als bezahlt markieren';
const ABSCHREIBEN = 'Abschreiben';
const ZURUECK = 'Zurück auf gestellt';
const ABSCHREIBEN_FRAGE =
  'Die Forderung gilt damit als uneinbringlich. Die Rechnung bleibt mit Nummer und Dokument bestehen, und der Schritt lässt sich zurücknehmen.';
const AUSFALL_ZUSTAND = 'Der Zustand wurde nicht geändert. Bitte später erneut versuchen.';

/** Die Grenzen der Felder — dieselben wie in `RechnungRequest` und `RechnungPositionRequest`. */
const ZEITRAUM_LAENGE = 100;
const BEZEICHNUNG_LAENGE = 300;

/** Die Symbolgroesse in den Tasten (wie in {@link AngebotPage}). */
const SYMBOL_TASTE = 16;

/** Ueber jeder Rechnungsansicht steht die Liste der Rechnungen (E6). */
const ZU_RECHNUNGEN: PfadVerweis = { titel: 'Rechnungen', ziel: '/rechnungen' };

const SPALTEN: readonly TafelSpalte[] = [
  'Leistung',
  'Einheit',
  { beschriftung: 'Einzelpreis', zahl: true },
  { beschriftung: 'Angeboten', zahl: true },
  { beschriftung: 'Abgerechnet', zahl: true },
  { beschriftung: 'Offen', zahl: true },
  'Jetzt abrechnen',
  { beschriftung: 'Betrag', zahl: true },
];

/** Die Spalten des fertigen Belegs — nur, was auf einer Rechnung steht (Kriterium 24). */
const SPALTEN_BELEG: readonly TafelSpalte[] = [
  { beschriftung: 'Anzahl', zahl: true },
  'Einheit',
  'Leistung',
  { beschriftung: 'Einzelpreis', zahl: true },
  { beschriftung: 'Gesamtpreis', zahl: true },
];

/**
 * Die Woerter zu den Feldern, die der Server beim Stellen vermisst (`Belegpflichtangaben`).
 *
 * Ohne sie staende fuenfmal derselbe Satz da und niemand wuesste, welche Angabe gemeint ist. Was
 * hier fehlt, erscheint mit seinem Feldnamen: Eine Pflichtangabe, die das Backend spaeter
 * hinzunimmt, soll sichtbar bleiben und nicht aus der Liste fallen.
 */
const ANGABE_WORT: ReadonlyMap<string, string> = new Map([
  ['name', 'Name'],
  ['strasse', 'Straße und Hausnummer'],
  ['plz', 'Postleitzahl'],
  ['ort', 'Ort'],
  ['bankverbindung', 'Bankverbindung'],
  ['steuernummer', 'Steuernummer'],
  ['firma.name', 'Name der Firma'],
  ['firma.strasse', 'Straße und Hausnummer der Firma'],
  ['firma.plz', 'Postleitzahl der Firma'],
  ['firma.ort', 'Ort der Firma'],
]);

/** Was die Ansicht gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly rechnung: Rechnung }
  | { readonly art: 'unbekannt' }
  | { readonly art: 'ausfall' };

/** Die Angaben der Rechnung, so wie sie in den Feldern stehen. */
interface Texte {
  /** Tag (`YYYY-MM-DD`), wie das `date`-Feld ihn fuehrt. */
  readonly rechnungDatum: string;
  readonly leistungszeitraum: string;
}

const LEERE_TEXTE: Texte = { rechnungDatum: '', leistungszeitraum: '' };

/**
 * Eine Zeile der Maske: die festen Angaben der Position und die zwei Felder.
 *
 * Einheit, Einzelpreis und die drei Mengen kommen vom Server und sind an der Rechnung nicht
 * aenderbar (Kriterium 9); sie stehen hier mit, weil die Zeile ihren Betrag und ihre
 * Ueberschreitung selbst zeigt. Die Menge steht als <b>Text</b> und nicht als Zahl, aus demselben
 * Grund wie in {@link Positionsmaske}: `type="number"` liefert eine Gleitkommazahl, und
 * `lib/geld.ts` rechnet ausdruecklich nicht in Gleitkomma (E5).
 */
interface Maskenzeile {
  readonly angebotPositionId: number;
  readonly einheit: Einheit;
  readonly einzelpreisInCent: number;
  readonly angebotenInHundertsteln: number;
  readonly abgerechnetInHundertsteln: number;
  readonly offenInHundertsteln: number;
  readonly bezeichnung: string;
  readonly menge: string;
}

/** Eine Zeile mit ihrer gelesenen Menge — `null`, solange der Text keine ist. */
interface Reihe {
  readonly zeile: Maskenzeile;
  readonly mengeInHundertsteln: number | null;
}

/** Was sich aus lesbaren Mengen ergibt: der Rumpf der Anfrage und die drei Summen. */
interface Gerechnetes {
  readonly eingaben: readonly AbrechnungsangabeEingabe[];
  readonly summen: Rechnungssummen;
}

/** Ein Eintrag einer Stammdaten-Liste: Beschriftung und Wert. */
interface Stammeintrag {
  readonly name: string;
  readonly wert: ReactNode;
}

/** Die Ueberschrift: der Entwurf hat keine Nummer, er heisst nach seinem Zustand (Kriterium 15). */
function ueberschriftZu(rechnung: Rechnung): string {
  return rechnung.nummer === null ? 'Rechnung (Entwurf)' : `Rechnung ${rechnung.nummer}`;
}

/** Die Zeilen der Antwort als Zeilen der Maske. */
function alsZeilen(rechnung: Rechnung): readonly Maskenzeile[] {
  return rechnung.zeilen.map((zeile) => ({
    angebotPositionId: zeile.angebotPositionId,
    einheit: zeile.einheit,
    einzelpreisInCent: zeile.einzelpreisInCent,
    angebotenInHundertsteln: zeile.angebotenInHundertsteln,
    abgerechnetInHundertsteln: zeile.abgerechnetInHundertsteln,
    offenInHundertsteln: zeile.offenInHundertsteln,
    bezeichnung: zeile.bezeichnung,
    menge: dezimal(zeile.mengeInHundertsteln, ','),
  }));
}

/** Die Angaben der Antwort als Feldinhalte — `null` wird zum leeren Feld, nicht zu „null". */
function alsTexte(rechnung: Rechnung): Texte {
  return {
    rechnungDatum: rechnung.rechnungDatum,
    leistungszeitraum: rechnung.leistungszeitraum ?? '',
  };
}

/** Jede Zeile mit ihrer gelesenen Menge — einmal gelesen, von Tafel, Summen und Rumpf benutzt. */
function alsReihen(zeilen: readonly Maskenzeile[]): readonly Reihe[] {
  return zeilen.map((zeile) => ({ zeile, mengeInHundertsteln: hundertstel(zeile.menge) }));
}

/**
 * Ob der Entwurf etwas abrechnet (Kriterium 5).
 *
 * Eine Rechnung ueber nichts ist keine Rechnung — der Server weist sie ab, und die Oberflaeche
 * laesst den Schritt darum gar nicht erst zu. Eine unlesbare Menge zaehlt dabei nicht mit: Sie ist
 * keine Zahl, und was keine Zahl ist, rechnet nichts ab.
 */
function abrechenbar(reihen: readonly Reihe[]): boolean {
  return reihen.some(
    (reihe) => reihe.mengeInHundertsteln !== null && reihe.mengeInHundertsteln > 0,
  );
}

/** Die Zeilen des fertigen Belegs: was eine Menge traegt — alles andere steht auf keiner Rechnung. */
function belegzeilen(rechnung: Rechnung): readonly Rechnungsmaskenzeile[] {
  return rechnung.zeilen.filter((zeile) => zeile.mengeInHundertsteln > 0);
}

/** Das festgeschriebene Zahlungsziel als Wort — ein Entwurf traegt noch keines. */
function zahlungszielWort(tage: number | null): string {
  return tage === null ? KEIN_ZIEL : `${String(tage)} Tage`;
}

/**
 * Rumpf und Summen — `null`, solange eine Menge keine Zahl ist.
 *
 * Beides in einem Durchlauf und alles oder nichts: Eine Rechnung wird als Ganzes geschrieben, und
 * eine Liste, aus der die unlesbaren Zeilen stillschweigend herausfielen, loeschte Positionen, die
 * der Mensch sieht. Die Summen gelten aus demselben Grund erst, wenn jede Zeile lesbar ist — eine
 * Summe ueber die lesbaren waere eine Zahl, die zu keiner Rechnung gehoert.
 */
function gerechnetesAus(
  reihen: readonly Reihe[],
  satzInHundertsteln: number,
): Gerechnetes | null {
  const eingaben: AbrechnungsangabeEingabe[] = [];
  const posten: { mengeInHundertsteln: number; einzelpreisInCent: number }[] = [];
  for (const reihe of reihen) {
    const menge = reihe.mengeInHundertsteln;
    if (menge === null) {
      return null;
    }
    eingaben.push({
      angebotPositionId: reihe.zeile.angebotPositionId,
      bezeichnung: reihe.zeile.bezeichnung,
      menge: dezimal(menge, '.'),
    });
    posten.push({ mengeInHundertsteln: menge, einzelpreisInCent: reihe.zeile.einzelpreisInCent });
  }
  return { eingaben, summen: rechnungssummen(posten, satzInHundertsteln) };
}

/**
 * Was die Seite selbst beanstandet, bevor etwas hinausgeht.
 *
 * Die Mengen stehen nicht darin: Ob eine Menge lesbar ist, zeigt die Zeile beim Tippen, und die
 * Stelle, an der das Speichern daran scheitert, ist {@link gerechnetesAus}. Clientseitige Pruefung
 * ist Nutzerfuehrung und kein Ersatz fuer die Pruefung des Servers (CLAUDE-react.md).
 */
function eigenePruefung(texte: Texte, zeilen: readonly Maskenzeile[]): FieldErrors {
  const fehler: Record<string, readonly string[]> = {};
  if (texte.rechnungDatum === '') {
    fehler.rechnungDatum = [DATUM_FEHLT];
  }
  if (texte.leistungszeitraum.trim() === '') {
    fehler.leistungszeitraum = [ZEITRAUM_FEHLT];
  }
  zeilen.forEach((zeile, stelle) => {
    if (zeile.bezeichnung.trim() === '') {
      fehler[`positionen[${String(stelle)}].bezeichnung`] = [BEZEICHNUNG_FEHLT];
    }
  });
  return fehler;
}

/** Eine Zahl in der Tafel: rechts, in Tabellenziffern, ohne Umbruch. */
function Zahlzelle({
  children,
  testid,
  stark = false,
}: {
  readonly children: ReactNode;
  readonly testid?: string;
  readonly stark?: boolean;
}) {
  return (
    <Box
      component="td"
      data-testid={testid}
      className={ZAHLEN_KLASSE}
      sx={{ textAlign: 'right', whiteSpace: 'nowrap', fontWeight: stark ? 600 : 400 }}
    >
      {children}
    </Box>
  );
}

/** Eine der drei Summen unter der Tafel. */
function Summenzeile({
  name,
  wert,
  testid,
  stark = false,
}: {
  readonly name: string;
  readonly wert: string;
  readonly testid: string;
  readonly stark?: boolean;
}) {
  return (
    <Box
      data-testid={testid}
      sx={{ display: 'flex', alignItems: 'baseline', gap: '10px', justifyContent: 'flex-end' }}
    >
      <Typography sx={{ fontSize: 13.5, fontWeight: stark ? 600 : 400 }}>{name}</Typography>
      <Typography
        className={ZAHLEN_KLASSE}
        sx={{ fontSize: stark ? 17 : 13.5, fontWeight: stark ? 800 : 600, minWidth: 120 }}
      >
        {wert}
      </Typography>
    </Box>
  );
}

/**
 * Die drei Summen unter der Positionstafel — im Entwurf mitgetippt, am Beleg festgeschrieben.
 *
 * Die Werte kommen als fertige Zeichenketten herein: Der Entwurf setzt an die Stelle einer
 * unlesbaren Menge einen Strich, der Beleg nie.
 */
function Summen({
  satzInHundertsteln,
  netto,
  steuer,
  brutto,
}: {
  readonly satzInHundertsteln: number;
  readonly netto: string;
  readonly steuer: string;
  readonly brutto: string;
}) {
  return (
    <Box
      sx={(theme) => ({
        display: 'flex',
        flexDirection: 'column',
        gap: '6px',
        paddingTop: '14px',
        borderTop: `1px solid ${theme.vars.palette.kupferwolke.linie}`,
      })}
    >
      <Summenzeile testid="rechnung-netto" name="Summe (netto)" wert={netto} />
      <Summenzeile
        testid="rechnung-steuer"
        name={`Mehrwertsteuer ${dezimal(satzInHundertsteln, ',')} %`}
        wert={steuer}
      />
      <Summenzeile testid="rechnung-brutto" name="Bruttobetrag" wert={brutto} stark />
    </Box>
  );
}

/** Eine Beschriftung-Wert-Liste (CLAUDE-design.md, „Stammdaten"; Vorlage `.stamm` Z. 100–102). */
function Stammdaten({
  testid,
  eintraege,
}: {
  readonly testid: string;
  readonly eintraege: readonly Stammeintrag[];
}) {
  return (
    <Box
      component="dl"
      data-testid={testid}
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'auto minmax(0, 1fr)' },
        alignItems: 'center',
        gap: '10px 20px',
        margin: 0,
        fontSize: 13.5,
      }}
    >
      {eintraege.map((eintrag) => (
        <Fragment key={eintrag.name}>
          <Box
            component="dt"
            sx={(theme) => ({ color: theme.vars.palette.kupferwolke.textSchwach })}
          >
            {eintrag.name}
          </Box>
          <Box component="dd" sx={{ margin: 0, fontWeight: 500 }}>
            {eintrag.wert}
          </Box>
        </Fragment>
      ))}
    </Box>
  );
}

/** Die Angaben des Kopfes: Firma, Angebot und Zustand — in beiden Zustaenden dieselben. */
function Angaben({ rechnung }: { readonly rechnung: Rechnung }) {
  return (
    <Stammdaten
      testid="rechnung-angaben"
      eintraege={[
        {
          name: 'Firma',
          wert: (
            <Link
              component={RouterLink}
              to={`/firmen/${String(rechnung.firmaId)}`}
              underline="hover"
              sx={{ fontSize: 13.5, fontWeight: 500 }}
            >
              {rechnung.firmaName}
            </Link>
          ),
        },
        {
          name: 'Angebot',
          wert: (
            <Link
              component={RouterLink}
              to={`/angebote/${String(rechnung.angebotId)}`}
              underline="hover"
              sx={{ fontSize: 13.5, fontWeight: 500 }}
            >
              Zum Angebot
            </Link>
          ),
        },
        { name: 'Zustand', wert: <RechnungszustandChip zustand={rechnung.zustand} /> },
      ]}
    />
  );
}

/**
 * Die festgeschriebenen Angaben der gestellten Rechnung (Kriterium 14).
 *
 * Steuersatz und Zahlungsziel stehen hier und nicht am Entwurf: Beide gelten erst mit dem Stellen,
 * und bis dahin koennen die Einstellungen sie noch aendern.
 */
function Rechnungsangaben({ rechnung }: { readonly rechnung: Rechnung }) {
  return (
    <Stammdaten
      testid="rechnung-stammdaten"
      eintraege={[
        { name: 'Rechnungsdatum', wert: tagWort(rechnung.rechnungDatum) },
        { name: 'Leistungszeitraum', wert: rechnung.leistungszeitraum ?? KEIN_ZEITRAUM },
        { name: 'Steuersatz', wert: `${dezimal(rechnung.steuersatzInHundertsteln, ',')} %` },
        { name: 'Zahlungsziel', wert: zahlungszielWort(rechnung.zahlungszielTage) },
      ]}
    />
  );
}

/**
 * Eine Zeile der Positionstafel (Kriterien 4, 7, 8).
 *
 * <b>Gesteuert, nicht selbststaendig</b>, wie {@link Positionsmaske}: Der Zustand liegt in der
 * Seite, denn die Rechnung wird als Ganzes geschickt. Dass diese Komponente auf Modulebene steht
 * und nicht in der Seite, ist kein Stilfrage: Eine im Rumpf der Seite erklaerte Komponente ist bei
 * jedem Zeichnen ein neuer Typ, React baut die Zeile dann neu auf — und der Fokus faellt nach dem
 * ersten getippten Zeichen aus dem Feld.
 */
function Positionszeile({
  reihe,
  stelle,
  meldung,
  aendere,
}: {
  readonly reihe: Reihe;
  readonly stelle: number;
  /** Die Meldung an einem Feld: die eigene der Seite oder die des Servers. */
  readonly meldung: (feld: string) => string | undefined;
  readonly aendere: (stelle: number, neu: Maskenzeile) => void;
}) {
  const { zeile, mengeInHundertsteln } = reihe;
  const nummer = String(stelle + 1);
  const zuViel =
    mengeInHundertsteln === null
      ? 0
      : ueberschreitung(
          zeile.abgerechnetInHundertsteln,
          zeile.angebotenInHundertsteln,
          mengeInHundertsteln,
        );
  const bezeichnungMeldung = meldung(`positionen[${String(stelle)}].bezeichnung`);
  const mengeMeldung =
    mengeInHundertsteln === null ? MENGE_UNKLAR : meldung(`positionen[${String(stelle)}].menge`);
  return (
    <Box component="tr">
      <Box component="td" sx={{ minWidth: 220 }}>
        <TextField
          value={zeile.bezeichnung}
          onChange={(ereignis) => {
            aendere(stelle, { ...zeile, bezeichnung: ereignis.target.value });
          }}
          error={bezeichnungMeldung !== undefined}
          helperText={bezeichnungMeldung}
          fullWidth
          size="small"
          slotProps={{
            htmlInput: {
              'aria-label': `Leistung, Position ${nummer}`,
              maxLength: BEZEICHNUNG_LAENGE,
            },
          }}
        />
      </Box>
      <Box component="td">{EINHEIT_WORT[zeile.einheit]}</Box>
      <Zahlzelle>{euro(zeile.einzelpreisInCent)}</Zahlzelle>
      <Zahlzelle>{dezimal(zeile.angebotenInHundertsteln, ',')}</Zahlzelle>
      <Zahlzelle testid="zeile-abgerechnet">
        {dezimal(zeile.abgerechnetInHundertsteln, ',')}
      </Zahlzelle>
      <Zahlzelle testid="zeile-offen">{dezimal(zeile.offenInHundertsteln, ',')}</Zahlzelle>
      <Box component="td" sx={{ minWidth: 150 }}>
        <TextField
          value={zeile.menge}
          onChange={(ereignis) => {
            aendere(stelle, { ...zeile, menge: ereignis.target.value });
          }}
          error={mengeMeldung !== undefined}
          helperText={
            mengeMeldung ??
            (zuViel > 0 ? (
              <Ueberschreitungshinweis
                mengeInHundertsteln={zuViel}
                angebotPositionId={zeile.angebotPositionId}
              />
            ) : undefined)
          }
          size="small"
          slotProps={{
            htmlInput: {
              'aria-label': `Jetzt abrechnen, Position ${nummer}`,
              inputMode: 'decimal',
              className: ZAHLEN_KLASSE,
            },
          }}
        />
      </Box>
      <Zahlzelle testid={`zeile-betrag-${String(zeile.angebotPositionId)}`} stark>
        {mengeInHundertsteln === null
          ? '—'
          : euro(betrag(mengeInHundertsteln, zeile.einzelpreisInCent))}
      </Zahlzelle>
    </Box>
  );
}

/**
 * Die Rueckfrage vor einem folgenreichen Schritt: vor dem Stellen (Kriterium 13) und vor dem
 * Abschreiben (Issue #253).
 *
 * Ein <b>eigener Dialog</b> und nicht das `confirm` des Browsers, aus denselben Gruenden wie in
 * {@link AktionsMenue} (E8). Die bestaetigende Taste traegt die Rose-Toenung der folgenreichen
 * Aktion und nicht den Kupferverlauf: Die eine Kupfertaste der Ansicht steht in der Kopfkarte, und
 * eine zweite im Dialog machte aus einer Hauptaktion zwei.
 *
 * <b>Ein Dialog fuer beide Rueckfragen</b> und nicht zwei: Titel, Satz und Aufschrift der
 * bestaetigenden Taste kommen von aussen, der Rest ist derselbe. Zwei Abschriften derselben Gestalt
 * liefen beim naechsten Nachziehen der Vorlage auseinander.
 *
 * <b>Den Fokus gibt MUI selbst zurueck</b> — anders als dort braucht es kein `disableRestoreFocus`:
 * Die Taste, von der die Rueckfrage ausgeht, bleibt stehen, waehrend der Dialog offen ist.
 */
function Rueckfrage({
  titel,
  frage,
  onJa,
  onEnde,
}: {
  readonly titel: string;
  readonly frage: string;
  readonly onJa: () => void;
  readonly onEnde: () => void;
}) {
  const titelId = useId();
  return (
    <Dialog open onClose={onEnde} aria-labelledby={titelId}>
      <DialogTitle id={titelId}>{titel}</DialogTitle>
      <DialogContent>
        <DialogContentText>{frage}</DialogContentText>
      </DialogContent>
      <DialogActions sx={{ padding: '4px 24px 20px', gap: '10px' }}>
        <WeicheTaste onClick={onEnde}>Abbrechen</WeicheTaste>
        <Button
          type="button"
          onClick={() => {
            onEnde();
            onJa();
          }}
          sx={(theme) => ({
            borderRadius: `${RADIUS_RUND}px`,
            padding: TASTE_INNENABSTAND,
            color: theme.vars.palette.kupferwolke.toenung.rose.schrift,
            background: theme.vars.palette.kupferwolke.toenung.rose.flaeche,
            '&:hover': {
              background: theme.vars.palette.kupferwolke.toenung.rose.flaeche,
              transform: 'translateY(-1px)',
            },
            '&:active': { transform: 'none' },
          })}
        >
          {titel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

/** Der Satz der Rueckfrage vor dem Stellen — er nennt den Bruttobetrag der Antwort. */
function stellfrage(bruttoInCent: number): string {
  return `Die Rechnung über ${euro(bruttoInCent)} geht so hinaus. Das lässt sich nicht zurücknehmen: Danach kann sie weder geändert noch gelöscht werden.`;
}

/**
 * Die Angaben, die der Server fuer den Beleg vermisst (Kriterium 13).
 *
 * Er weist alle fehlenden auf einmal ab (`PflichtangabenFehlen`), und sie liegen an zwei Orten —
 * bei den eigenen Angaben und bei der Firma. Beide Wege stehen darum unter der Liste: Wer fuenf
 * Angaben nachtragen muss, soll nicht erst suchen, wo sie stehen.
 */
function Pflichtangaben({
  felder,
  rechnung,
}: {
  readonly felder: FieldErrors;
  readonly rechnung: Rechnung;
}) {
  return (
    <Alert severity="error" data-testid="rechnung-pflichtangaben">
      <Typography sx={{ fontSize: 13.5 }}>{PFLICHTANGABEN}</Typography>
      <Box component="ul" sx={{ margin: '6px 0', paddingLeft: '20px', fontSize: 13.5 }}>
        {Object.entries(felder).map(([feld, meldungen]) => (
          <Box component="li" key={feld}>
            {`${ANGABE_WORT.get(feld) ?? feld} — ${meldungen.join(' ')}`}
          </Box>
        ))}
      </Box>
      <Typography sx={{ fontSize: 13.5 }}>
        {'Zu ergänzen unter '}
        <Link component={RouterLink} to="/eigene-angaben" underline="hover">
          Eigene Angaben
        </Link>
        {' und bei '}
        <Link
          component={RouterLink}
          to={`/firmen/${String(rechnung.firmaId)}`}
          underline="hover"
        >
          {rechnung.firmaName}
        </Link>
        .
      </Typography>
    </Alert>
  );
}

export default function RechnungPage() {
  const { rechnungId } = useParams();
  const kennung = kennungAus(rechnungId);
  const navigate = useNavigate();
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });
  const [texte, setzeTexte] = useState<Texte>(LEERE_TEXTE);
  const [zeilen, setzeZeilen] = useState<readonly Maskenzeile[]>([]);
  const [eigeneFehler, setzeEigeneFehler] = useState<FieldErrors>({});
  const [feldFehler, setzeFeldFehler] = useState<FieldErrors>({});
  const [fehler, setzeFehler] = useState<string | null>(null);
  const [fehlende, setzeFehlende] = useState<FieldErrors | null>(null);
  const [gespeichert, setzeGespeichert] = useState(false);
  const [laeuft, setzeLaeuft] = useState(false);
  // Seit dem letzten Lesen oder Speichern wurde getippt — dann geht vor dem Stellen ein PUT hinaus.
  const [ungespeichert, setzeUngespeichert] = useState(false);
  // Der Bruttobetrag, ueber den die Rueckfrage gerade fragt; `null`, solange sie nicht offen ist.
  const [frage, setzeFrage] = useState<number | null>(null);
  // Ob die Rueckfrage vor dem Abschreiben offen ist (Issue #253).
  const [abschreibfrage, setzeAbschreibfrage] = useState(false);
  useKopfPfad(
    [ZU_RECHNUNGEN],
    stand.art === 'daten' ? ueberschriftZu(stand.rechnung) : 'Rechnung',
  );

  useEffect(() => {
    if (kennung === null) {
      // Eine Kennung, die keine ist, geht gar nicht erst ans Netz (kennung.ts).
      setzeStand({ art: 'unbekannt' });
      return;
    }
    void rechnungLesen(kennung)
      .then((rechnung) => {
        setzeStand({ art: 'daten', rechnung });
        setzeTexte(alsTexte(rechnung));
        setzeZeilen(alsZeilen(rechnung));
      })
      .catch((ursache: unknown) => {
        setzeStand(nichtGefunden(ursache) ? { art: 'unbekannt' } : { art: 'ausfall' });
      });
  }, [kennung]);

  const reihen = alsReihen(zeilen);

  /** Die Meldung an einem Feld: die eigene geht vor der des Servers. */
  const meldung = (feld: string) => meldungAm(eigeneFehler, feld) ?? meldungAm(feldFehler, feld);

  /** Eine Zeile aendern — die Liste ist Zustand, geschickt wird sie beim Speichern als Ganzes. */
  const aendere = (stelle: number, neu: Maskenzeile) => {
    setzeUngespeichert(true);
    setzeZeilen(zeilen.map((alt, index) => (index === stelle ? neu : alt)));
  };

  /** Eine Angabe des Kopfes aendern. */
  const aendereTexte = (neu: Texte) => {
    setzeUngespeichert(true);
    setzeTexte(neu);
  };

  /** Uebernimmt, was der Server zuletzt geschickt hat — Antwort auf Speichern und auf Stellen. */
  const uebernimm = (neu: Rechnung) => {
    setzeStand({ art: 'daten', rechnung: neu });
    setzeTexte(alsTexte(neu));
    setzeZeilen(alsZeilen(neu));
    setzeUngespeichert(false);
  };

  /** Speichert den Entwurf und gibt die Antwort heraus — `null`, wenn nichts hinausging. */
  const speichern = async (
    rechnung: Rechnung,
    gerechnet: Gerechnetes | null,
  ): Promise<Rechnung | null> => {
    setzeGespeichert(false);
    setzeFehler(null);
    setzeFehlende(null);
    setzeFeldFehler({});
    const eigene = eigenePruefung(texte, zeilen);
    setzeEigeneFehler(eigene);
    if (Object.keys(eigene).length > 0 || gerechnet === null) {
      // Was fehlt, steht am Feld beziehungsweise an der Zeile; hinaus geht nichts.
      return null;
    }
    setzeLaeuft(true);
    // Ein Ausgang am Ende und keiner im Rumpf: Ein `return` im `try` neben einem `finally` legt
    // einen zweiten Weg durch die Funktion an, den keine Probe erreichen kann.
    let gesichert: Rechnung | null = null;
    try {
      const neu = await rechnungAendern(rechnung.id, {
        rechnungDatum: texte.rechnungDatum,
        leistungszeitraum: texte.leistungszeitraum.trim(),
        positionen: gerechnet.eingaben,
      });
      uebernimm(neu);
      setzeGespeichert(true);
      gesichert = neu;
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
    return gesichert;
  };

  /**
   * Der Weg zur Rueckfrage: erst sichern, was noch nicht gesichert ist, dann fragen.
   *
   * Scheitert das Speichern, wird nicht gefragt — die Rechnung ginge sonst mit einem anderen Inhalt
   * hinaus, als der Mensch gerade liest. Gefragt wird ueber den Bruttobetrag der <b>Antwort</b>.
   */
  const stellenFragen = async (rechnung: Rechnung, gerechnet: Gerechnetes | null) => {
    if (ungespeichert) {
      const neu = await speichern(rechnung, gerechnet);
      if (neu === null) {
        return;
      }
      setzeFrage(neu.bruttoInCent);
      return;
    }
    setzeFrage(rechnung.bruttoInCent);
  };

  /** Stellt die Rechnung; die Antwort traegt die gestellte Rechnung mit ihrer Nummer. */
  const stellen = async (rechnung: Rechnung) => {
    setzeGespeichert(false);
    setzeFehler(null);
    setzeFehlende(null);
    setzeLaeuft(true);
    try {
      uebernimm(await rechnungStellen(rechnung.id));
    } catch (ursache) {
      const felder = feldMeldungen(ursache);
      if (Object.keys(felder).length > 0) {
        setzeFehlende(felder);
      } else {
        setzeFehler(serverMeldung(ursache, AUSFALL_STELLEN));
      }
    } finally {
      setzeLaeuft(false);
    }
  };

  /**
   * Stellt die gestellte Rechnung auf bezahlt, abgeschrieben oder zurueck auf gestellt (#253).
   *
   * Ein unzulaessiger Uebergang kommt als 409 mit der Meldung des Servers zurueck und erscheint als
   * Meldung ueber der Ansicht — die Seite bleibt stehen. Welcher Uebergang zulaessig ist, entscheidet
   * der Server; die Tasten zeigen nur, was im jeweiligen Zustand gemeint ist.
   */
  const umstellen = async (rechnung: Rechnung, ziel: Rechnungszustand) => {
    setzeFehler(null);
    setzeLaeuft(true);
    try {
      uebernimm(await setzeRechnungszustand(rechnung.id, ziel));
    } catch (ursache) {
      setzeFehler(serverMeldung(ursache, AUSFALL_ZUSTAND));
    } finally {
      setzeLaeuft(false);
    }
  };

  const loeschen = async (rechnung: Rechnung) => {
    setzeFehler(null);
    setzeLaeuft(true);
    try {
      await rechnungLoeschen(rechnung.id);
      navigate('/rechnungen');
    } catch {
      setzeFehler(AUSFALL_LOESCHEN);
      setzeLaeuft(false);
    }
  };

  /**
   * Die Aktionen des Entwurfs: „Rechnung stellen" als die eine Kupfertaste, „Speichern" weich
   * daneben, das Loeschen im ⋯-Menue.
   */
  function aktionenZu(rechnung: Rechnung, gerechnet: Gerechnetes | null): ReactNode {
    const kannStellen = abrechenbar(reihen);
    return (
      <Box
        data-testid="rechnung-aktionen"
        sx={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}
      >
        {kannStellen ? null : (
          <Typography
            data-testid="stellen-grund"
            sx={(theme) => ({
              fontSize: 12.5,
              maxWidth: 260,
              color: theme.vars.palette.kupferwolke.textSchwach,
            })}
          >
            {OHNE_MENGE}
          </Typography>
        )}
        <AktionsMenue
          name="Aktionen für diese Rechnung"
          objekt={ueberschriftZu(rechnung)}
          eintraege={[
            {
              titel: 'Entwurf löschen',
              symbol: <IconTrash size={SYMBOL_TASTE} stroke={1.8} />,
              rueckfrage: LOESCHEN_FRAGE,
              onAuswahl: () => {
                void loeschen(rechnung);
              },
            },
          ]}
        />
        <WeicheTaste
          onClick={() => {
            void speichern(rechnung, gerechnet);
          }}
          disabled={laeuft}
          symbol={<IconDeviceFloppy size={SYMBOL_TASTE} stroke={1.8} />}
        >
          Speichern
        </WeicheTaste>
        <KupferTaste
          onClick={() => {
            void stellenFragen(rechnung, gerechnet);
          }}
          disabled={laeuft || !kannStellen}
          symbol={<IconFileInvoice size={SYMBOL_TASTE} stroke={1.8} />}
        >
          {STELLEN}
        </KupferTaste>
      </Box>
    );
  }

  /**
   * Die Aktionen der gestellten Rechnung: „Herunterladen" als die eine Kupfertaste, der Ausgang der
   * Forderung weich daneben (Issue #253).
   *
   * Welche Tasten dastehen, haengt am Zustand: Eine gestellte Rechnung bietet die beiden Ausgaenge
   * an, eine bezahlte oder abgeschriebene den Weg zurueck. Zwischen den Ausgaengen geht es nicht
   * unmittelbar — das waere eine vierte Kante, die der Server nicht kennt.
   *
   * „Abschreiben" fragt vorher nach, „Als bezahlt markieren" nicht: Das eine sagt, dass Geld nicht
   * mehr kommt, das andere, dass es da ist, und beide sind zurueckzunehmen. Die Rueckfrage steht am
   * folgenreicheren der beiden (CLAUDE-design.md, „Tasten").
   */
  function belegaktionen(rechnung: Rechnung): ReactNode {
    return (
      <Box
        data-testid="rechnung-aktionen"
        sx={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}
      >
        {rechnung.zustand === 'GESTELLT' ? (
          <>
            <WeicheTaste
              onClick={() => {
                void umstellen(rechnung, 'BEZAHLT');
              }}
              disabled={laeuft}
              symbol={<IconCircleCheck size={SYMBOL_TASTE} stroke={1.8} />}
            >
              {BEZAHLT}
            </WeicheTaste>
            <WeicheTaste
              onClick={() => {
                setzeAbschreibfrage(true);
              }}
              disabled={laeuft}
              symbol={<IconCircleX size={SYMBOL_TASTE} stroke={1.8} />}
            >
              {ABSCHREIBEN}
            </WeicheTaste>
          </>
        ) : (
          <WeicheTaste
            onClick={() => {
              void umstellen(rechnung, 'GESTELLT');
            }}
            disabled={laeuft}
            symbol={<IconRotate2 size={SYMBOL_TASTE} stroke={1.8} />}
          >
            {ZURUECK}
          </WeicheTaste>
        )}
        <Button component="a" href={rechnungDokumentPfad(rechnung.id)} download sx={kupferSx}>
          <TastenSymbol>
            <IconDownload size={SYMBOL_TASTE} stroke={1.8} />
          </TastenSymbol>
          Herunterladen
        </Button>
      </Box>
    );
  }

  /** Die Maske des Entwurfs: Angaben, Positionstafel und die drei Summen. */
  function entwurfZu(rechnung: Rechnung, gerechnet: Gerechnetes | null): ReactNode {
    const summen = gerechnet?.summen ?? null;
    const wert = (cent: number | undefined) => (cent === undefined ? '—' : euro(cent));
    return (
      <>
        <Karte titel="Angaben zur Rechnung">
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
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
              label="Rechnungsdatum"
              type="date"
              value={texte.rechnungDatum}
              onChange={(ereignis) => {
                aendereTexte({ ...texte, rechnungDatum: ereignis.target.value });
              }}
              error={meldung('rechnungDatum') !== undefined}
              helperText={meldung('rechnungDatum')}
              required
              fullWidth
              // Ein `date`-Feld zeigt immer seine Maske, also darf die Beschriftung nie im Feld
              // liegen.
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField
              label="Leistungszeitraum"
              value={texte.leistungszeitraum}
              onChange={(ereignis) => {
                aendereTexte({ ...texte, leistungszeitraum: ereignis.target.value });
              }}
              error={meldung('leistungszeitraum') !== undefined}
              helperText={meldung('leistungszeitraum')}
              required
              fullWidth
              slotProps={{ htmlInput: { maxLength: ZEITRAUM_LAENGE } }}
            />
          </Box>
        </Karte>
        <Karte titel="Positionen" anzahl={zeilen.length}>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <Tafel beschriftung="Positionen" spalten={SPALTEN}>
              {reihen.map((reihe, stelle) => (
                // Die Kennung der Angebotsposition ist der Schluessel: Sie bleibt ueber ein
                // Speichern hinweg dieselbe, und die Reihenfolge der Liste ist die des Angebots.
                <Positionszeile
                  key={reihe.zeile.angebotPositionId}
                  reihe={reihe}
                  stelle={stelle}
                  meldung={meldung}
                  aendere={aendere}
                />
              ))}
            </Tafel>
            <Summen
              satzInHundertsteln={rechnung.steuersatzInHundertsteln}
              netto={wert(summen?.nettoInCent)}
              steuer={wert(summen?.steuerInCent)}
              brutto={wert(summen?.bruttoInCent)}
            />
          </Box>
        </Karte>
      </>
    );
  }

  /** Der Rahmen jeder Bereitschaft: der Abstand zwischen den Karten der Buehne. */
  const spalten = { display: 'flex', flexDirection: 'column', gap: ABSTAND_BUEHNE } as const;

  if (stand.art === 'laedt') {
    return (
      <Box sx={spalten}>
        <Karte>
          <Typography
            sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
          >
            {LAEDT}
          </Typography>
        </Karte>
      </Box>
    );
  }

  if (stand.art !== 'daten') {
    return (
      <Box sx={spalten}>
        <Karte>
          <Alert severity="error">{stand.art === 'unbekannt' ? NICHT_GEFUNDEN : AUSFALL}</Alert>
        </Karte>
      </Box>
    );
  }

  const { rechnung } = stand;

  // Die Leseansicht gilt fuer jeden gestellten Zustand: Bezahlt und Abgeschrieben sind gestellte
  // Rechnungen und zeigen denselben Beleg (Issue #253).
  if (istGestellt(rechnung.zustand)) {
    const belegte = belegzeilen(rechnung);
    return (
      <Box sx={spalten}>
        {fehler === null ? null : <Alert severity="error">{fehler}</Alert>}
        <Karte
          titel={ueberschriftZu(rechnung)}
          titelEbene={1}
          werkzeug={belegaktionen(rechnung)}
        >
          <Angaben rechnung={rechnung} />
        </Karte>
        <Karte titel="Angaben zur Rechnung">
          <Rechnungsangaben rechnung={rechnung} />
        </Karte>
        <Karte titel="Positionen" anzahl={belegte.length}>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <Tafel beschriftung="Positionen" spalten={SPALTEN_BELEG}>
              {belegte.map((zeile) => (
                <Box component="tr" key={zeile.angebotPositionId}>
                  <Zahlzelle>{dezimal(zeile.mengeInHundertsteln, ',')}</Zahlzelle>
                  <Box component="td">{EINHEIT_WORT[zeile.einheit]}</Box>
                  <Box component="td" sx={{ minWidth: 220 }}>
                    {zeile.bezeichnung}
                  </Box>
                  <Zahlzelle>{euro(zeile.einzelpreisInCent)}</Zahlzelle>
                  <Zahlzelle testid={`zeile-betrag-${String(zeile.angebotPositionId)}`} stark>
                    {euro(betrag(zeile.mengeInHundertsteln, zeile.einzelpreisInCent))}
                  </Zahlzelle>
                </Box>
              ))}
            </Tafel>
            <Summen
              satzInHundertsteln={rechnung.steuersatzInHundertsteln}
              netto={euro(rechnung.nettoInCent)}
              steuer={euro(rechnung.steuerInCent)}
              brutto={euro(rechnung.bruttoInCent)}
            />
          </Box>
        </Karte>
        {abschreibfrage ? (
          <Rueckfrage
            titel={ABSCHREIBEN}
            frage={ABSCHREIBEN_FRAGE}
            onJa={() => {
              void umstellen(rechnung, 'ABGESCHRIEBEN');
            }}
            onEnde={() => {
              setzeAbschreibfrage(false);
            }}
          />
        ) : null}
      </Box>
    );
  }

  const gerechnet = gerechnetesAus(reihen, rechnung.steuersatzInHundertsteln);
  return (
    <Box sx={spalten}>
      {fehler === null ? null : <Alert severity="error">{fehler}</Alert>}
      {fehlende === null ? null : <Pflichtangaben felder={fehlende} rechnung={rechnung} />}
      <Karte
        titel={ueberschriftZu(rechnung)}
        titelEbene={1}
        werkzeug={aktionenZu(rechnung, gerechnet)}
      >
        <Angaben rechnung={rechnung} />
      </Karte>
      {entwurfZu(rechnung, gerechnet)}
      {frage === null ? null : (
        <Rueckfrage
          titel={STELLEN}
          frage={stellfrage(frage)}
          onJa={() => {
            void stellen(rechnung);
          }}
          onEnde={() => {
            setzeFrage(null);
          }}
        />
      )}
    </Box>
  );
}
