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
  IconArrowBackUp,
  IconArrowLeft,
  IconArrowRight,
  IconBan,
  IconCircleCheck,
  IconFilePlus,
  IconPencil,
} from '@tabler/icons-react';
import { useEffect, useId, useState } from 'react';
import type { ReactNode } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';

import {
  angebotLesen,
  angebotStatusWeiter,
  angebotStatusZurueck,
  angebotStornieren,
  angebotStornoZuruecknehmen,
} from '../api/angebote';
import type { Angebot, AngebotPosition } from '../api/angebote';
import { angebotAbrechnung, rechnungAnlegen } from '../api/rechnungen';
import type {
  Abrechnungsposition,
  AngebotRechnungZeile,
  Angebotsabrechnung,
} from '../api/rechnungen';
import AngebotsstatusChip from '../components/AngebotsstatusChip';
import Anlagen from '../components/Anlagen';
import InternChip from '../components/InternChip';
import Angabenliste from '../components/Angabenliste';
import Karte from '../components/Karte';
import Kommentare from '../components/Kommentare';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import KupferTaste from '../components/KupferTaste';
import Monatswahl, { monatOderKeiner, monatswahlWert } from '../components/Monatswahl';
import { EINHEIT_WORT, MODUS_WORT } from '../components/Positionsmaske';
import RechnungszustandChip from '../components/RechnungszustandChip';
import StorniertChip from '../components/StorniertChip';
import Tafel, { type TafelSpalte } from '../components/Tafel';
import Ueberschreitungshinweis from '../components/Ueberschreitungshinweis';
import WeicheTaste from '../components/WeicheTaste';
import { nichtGefunden, serverMeldung } from '../lib/apifehler';
import { stundenWort } from '../lib/arbeitszeit';
import { dezimal, euro } from '../lib/geld';
import { kennungAus } from '../lib/kennung';
import { tagWort } from '../lib/tag';
import { ABSTAND_BUEHNE, RADIUS_RUND, TASTE_INNENABSTAND, ZAHLEN_KLASSE } from '../theme';

/**
 * Die Ansicht eines Angebots (Issue #127).
 *
 * Oben die Karte mit der Ueberschrift, den Angaben und den Aktionen; darunter die Beschreibung,
 * sofern es eine gibt, die Positionen mit Summe, die Anlagen und zuletzt die Kommentare (Vorlage
 * `.karte` Z. 53, `.kopfzeile` Z. 79–80, `.stamm` Z. 100–102).
 *
 * <b>Anlagen und Kommentare laden sich selbst</b> ({@link Anlagen}, Issue #148; {@link Kommentare},
 * Issue #146): Die Seite gibt nur die Kennung weiter. Ein Ausfall eines der beiden Wege steht darum
 * in dessen Karte und laesst Ueberschrift, Angaben und Positionen stehen.
 *
 * <b>Die Aktionen.</b> „Status weiter" ist die eine Kupfertaste — der gewoehnliche naechste Schritt.
 * „Status zurueck" und „Bearbeiten" stehen weich daneben (CLAUDE-design.md, Leitgedanke 2). An den
 * Enden der Reihe fehlt die Taste, die ins Leere fuehrte: bei „angelegt" die zurueck, bei
 * „abgerechnet" die weiter. Bearbeiten laesst sich in jedem Status (Kriterium 5) — nur nicht am
 * stornierten Angebot (Issue #340, #330 Kriterium 10).
 *
 * <b>Nach jedem Statuswechsel gilt die Antwort</b>, nicht ein selbst umgeschalteter Status: Was der
 * Server fuehrt, ist die Wahrheit. Danach liest die Ansicht auch den Abrechnungsstand neu: Ob sich
 * eine Rechnung schreiben laesst, haengt am Status und am Storno, und beides hat sich eben geaendert.
 *
 * <b>Das Storno</b> (Issue #340, Plan #332, E2, E13): Am Kundenangebot in „angelegt", „abgegeben"
 * und „bestellt" steht „Stornieren" weich neben dem Statusweg. Es fragt zurueck
 * ({@link Stornodialog}), denn es nimmt das Angebot aus Forecast und „Angebote in Arbeit"
 * (Kriterium 6). Am stornierten Angebot ist die Statuskette eingefroren: Dort steht allein
 * „Storno zurücknehmen" — ohne Rueckfrage, denn es stellt nur den Zustand von vorher wieder her —
 * und, wo der Server es erlaubt, „Rechnung schreiben" ueber die gebuchten Stunden. Im Kopf steht
 * neben dem Status der {@link StorniertChip}, in den Angaben der Grund, wo einer festgehalten ist.
 *
 * <b>Der Abrechnungsstand ist ein eigener Weg</b> (#160, Kriterien 3 und 26; Issue #187): Er traegt
 * je Position, was abgerechnet, offen und angefallen ist, und dazu die Rechnungen dieses Angebots.
 * Er faellt fuer sich aus, genau wie Anlagen und Kommentare — dann steht seine Meldung in der Karte
 * „Rechnungen", und Ueberschrift, Angaben und Positionen bleiben stehen. Was er nicht weiss, zeigt
 * die Ansicht nicht: Ohne ihn fehlen die Zusatzspalten und „Rechnung schreiben".
 *
 * <b>Die Zusatzspalten haengen an zwei verschiedenen Fragen</b> ({@link Zusatzspalten}).
 * „Abgerechnet" und „Offen" stehen erst, wenn es eine Rechnung gibt: Solange keine existiert, sagten
 * sie mit 0,00 und der vollen Menge nichts, was die Spalte „Menge" nicht schon sagt — zwei Spalten
 * Rauschen in jeder Angebotsansicht der Anwendung. „Angefallen" steht dagegen, sobald eine Position
 * buchbar ist (Issue #193, Kriterien 7, 8, 11): Die Stunden sind da, bevor etwas abgerechnet ist,
 * und sie bleiben sichtbar, nachdem das Stellen der letzten Rechnung das Angebot auf „abgerechnet"
 * gesetzt hat.
 *
 * <b>„Rechnung schreiben" ist weich</b>: Die eine Kupfertaste der Ansicht bleibt „Status weiter"
 * (CLAUDE-design.md, Leitgedanke 2). Sie steht nur, wo sie etwas bewirkt — und das sagt allein das
 * Feld `schreibbar` des Abrechnungsstands (Plan #332, E9): Die Regel haengt an Status, Menge,
 * gebuchten Stunden und Storno, und ein Nachbau hier waere eine zweite Wahrheit, die beim ersten
 * Nachziehen auseinanderliefe. <b>Die Taste fuehrt, der Server entscheidet</b>, wie in
 * {@link RechnungenPage}. <b>Sie legt nicht selbst an</b>, sondern oeffnet einen kleinen Dialog mit
 * der einen Frage, die vorher zu beantworten ist: welchen Monat der Arbeitszeit der Entwurf
 * vorbelegen soll ({@link Monatswahl}, Issue #203). Weist das Anlegen mit 409 ab, steht die Meldung
 * des Servers in diesem Dialog — dort, wo die Wahl steht, die der Betrachter aendern kann.
 *
 * <b>Das interne Angebot ist dieselbe Ansicht in einer anderen Zusammenstellung</b> (Issue #235,
 * Plan #218, E15). Es haelt die eigene Arbeit fest, es geht an keinen Kunden, und darum fehlt ihm
 * alles, was mit Geld zu tun hat: In der Positionstafel stehen nur „Bezeichnung" und „Angefallen",
 * in der Fusszeile die Summe der erfassten Stunden statt der Nettosumme, und „Rechnung schreiben"
 * gibt es nicht. Im Kopf steht neben dem Status das Kennzeichen ({@link InternChip}).
 *
 * <b>Abgeschlossen und wieder geoeffnet wird ueber dieselben zwei Wege</b> wie der Statuswechsel
 * am Kundenangebot (E15): `status/weiter` und `status/zurueck`. Nur die Beschriftung ist eine
 * andere — „Abschließen" und „Wieder öffnen" —, denn die interne Kette heisst
 * „Läuft — Abgeschlossen" und kennt kein „weiter" darueber hinaus. Eigene Wege `/abschliessen`
 * und `/oeffnen` waeren dieselbe Mechanik zweimal.
 *
 * <b>„Rechnung schreiben" braucht intern keine eigene Bedingung.</b> Die Aktionsreihe verzweigt als
 * Ganzes: intern die zwei Tasten der internen Kette, extern die des Kundenangebots — und nur dort
 * steht „Rechnung schreiben". Storniert wird intern ebenso wenig: Storniert wird ein Angebot an
 * einen Kunden (#330, Kriterium 1).
 */

const NICHT_GEFUNDEN = 'Dieses Angebot gibt es nicht.';
const AUSFALL = 'Das Angebot ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_AKTION = 'Der Status wurde nicht geändert. Bitte später erneut versuchen.';
/** Wo Storno oder Ruecknahme ohne Meldung des Servers scheitern. */
const AUSFALL_STORNO = 'Das Angebot wurde nicht geändert. Bitte später erneut versuchen.';
const LAEDT = 'Das Angebot wird geladen …';
const OHNE_POSITION = 'Noch keine Position.';
const NETTO = 'Alle Beträge netto, zzgl. gesetzlicher Umsatzsteuer';

/** Die Symbolgroesse in den Tasten (wie in {@link FirmaPage}). */
const SYMBOL_TASTE = 16;

const OHNE_RECHNUNG = 'Noch keine Rechnung.';
const LAEDT_ABRECHNUNG = 'Der Abrechnungsstand wird geladen …';
const AUSFALL_ABRECHNUNG =
  'Der Abrechnungsstand ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_ENTWURF = 'Der Rechnungsentwurf wurde nicht angelegt. Bitte später erneut versuchen.';

/** Die Ueberschrift des Dialogs, in dem der Monat gewaehlt wird (Issue #203). */
const WAHL_TITEL = 'Rechnung schreiben';

/** Was an der Stelle der Nummer steht, solange die Rechnung keine hat (wie in {@link RechnungenPage}). */
const OHNE_NUMMER = 'Entwurf';

/** Die Spalten der Positionstafel vor den Mengen des Stands. */
const SPALTEN_VOR: readonly TafelSpalte[] = [
  'Bezeichnung',
  'Abrechnung',
  { beschriftung: 'Menge', zahl: true },
];

/** Die Spalten dahinter — „Einheit" gilt fuer jede Menge links von ihr. */
const SPALTEN_NACH: readonly TafelSpalte[] = [
  'Einheit',
  { beschriftung: 'Einzelpreis', zahl: true },
  { beschriftung: 'Betrag', zahl: true },
  // Das Leistungsjahr, nur am Kundenangebot (Issue #311, E12) — ein Jahr, kein weiterer Betrag.
  'Jahr',
];

/** Die Zusatzspalte „Angefallen" (siehe {@link Zusatzspalten}). */
const SPALTEN_ANGEFALLEN: readonly TafelSpalte[] = [{ beschriftung: 'Angefallen', zahl: true }];

/** Die Zusatzspalten des Abrechnungsstands (siehe {@link Zusatzspalten}). */
const SPALTEN_STAND: readonly TafelSpalte[] = [
  { beschriftung: 'Abgerechnet', zahl: true },
  { beschriftung: 'Offen', zahl: true },
];

/**
 * Welche Zusatzspalten die Positionstafel traegt — zwei Fragen, zwei Antworten.
 *
 * Beide Zusaetze stehen bei der Menge und nicht am Ende der Zeile: Alle sind Mengen in derselben
 * Einheit, und die Spalte „Einheit" dahinter gilt damit fuer alle.
 */
interface Zusatzspalten {
  /**
   * „Angefallen" — sobald eine Position buchbar ist, <b>auch ohne Rechnung</b> (Issue #193,
   * Kriterien 7, 8, 11). Die Stunden sind da, bevor etwas abgerechnet ist, und sie bleiben da,
   * nachdem das Angebot auf „abgerechnet" gesprungen ist.
   */
  readonly angefallen: boolean;
  /** „Abgerechnet" und „Offen" — erst mit einer Rechnung. */
  readonly stand: boolean;
}

/**
 * Die Spalten der internen Positionstafel (Issue #235, Kriterien 3 und 6).
 *
 * Intern gibt es weder Menge noch Einheit noch Preis noch Abrechnungsart, und eine Rechnung gibt
 * es auch nie — es bleiben die Bezeichnung und die erfassten Stunden.
 */
const SPALTEN_INTERN: readonly TafelSpalte[] = [
  'Bezeichnung',
  { beschriftung: 'Angefallen', zahl: true },
];

/**
 * Die Spalten zu den gewaehlten Zusaetzen — intern eine eigene, kurze Reihe.
 *
 * Ein Zweig und nicht fuenf verstreute `&& !intern`: So steht die interne Tafel an einer Stelle
 * und nicht an fuenfen.
 */
function spaltenZu(zusatz: Zusatzspalten, intern: boolean): readonly TafelSpalte[] {
  if (intern) {
    return SPALTEN_INTERN;
  }
  return [
    ...SPALTEN_VOR,
    ...(zusatz.angefallen ? SPALTEN_ANGEFALLEN : []),
    ...(zusatz.stand ? SPALTEN_STAND : []),
    ...SPALTEN_NACH,
  ];
}

/** Die Spalten der Rechnungstafel — ohne Firma, die steht schon im Kopf des Angebots. */
const SPALTEN_RECHNUNGEN: readonly TafelSpalte[] = [
  'Nummer',
  'Rechnungsdatum',
  { beschriftung: 'Betrag', zahl: true },
  'Zustand',
];

/**
 * Aus diesen Staenden laesst sich ein Kundenangebot stornieren (#330, Kriterium 1).
 *
 * Die Taste fuehrt, der Server entscheidet — weist er ab, steht seine Meldung ueber der Karte.
 */
const STORNIERBAR: ReadonlySet<Angebot['status']> = new Set(['ANGELEGT', 'ABGEGEBEN', 'BESTELLT']);

/** Die Ueberschrift der Rueckfrage und zugleich die Aufschrift ihrer bestaetigenden Taste. */
const STORNIEREN = 'Stornieren';

/** Der Satz der Rueckfrage: was das Storno bewirkt, und dass es sich zuruecknehmen laesst. */
const STORNO_FRAGE =
  'Das Angebot zählt danach weder im Forecast noch unter „Angebote in Arbeit“. Es bleibt mit Positionen, Kommentaren und Anlagen nachlesbar, und das Storno lässt sich zurücknehmen.';

/** Die hoechste Laenge des Grundes — dieselbe wie im Server (`AngebotStornoRequest`, E4). */
const GRUND_LAENGE = 2000;

/** Was die Ansicht gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly angebot: Angebot }
  | { readonly art: 'unbekannt' }
  | { readonly art: 'ausfall' };

/**
 * Was die Ansicht ueber den Abrechnungsstand weiss.
 *
 * Ein eigener Stand neben {@link Stand}: Der Weg faellt fuer sich aus, und ein Ausfall dort darf das
 * Angebot nicht mitnehmen.
 */
type Abrechnungsstand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly abrechnung: Angebotsabrechnung }
  | { readonly art: 'ausfall' };

/**
 * Die Ueberschrift und die Endstufe des Kopfpfads: ein Angebot hat keine Nummer, es heisst nach
 * seinem Titel — und ein Angebot aus der Zeit vor dem Titel weiter nach seinem Datum (Kriterium 5).
 */
function ueberschriftZu(angebot: Angebot): string {
  return angebot.titel ?? `Angebot vom ${tagWort(angebot.angebotDatum)}`;
}

/** Die Beschriftung der Fusszeile — intern benennt sie Stunden, extern Geld (Issue #235). */
function summenwortZu(intern: boolean): string {
  return intern ? 'Angefallen' : 'Summe';
}

/** Was in der Fusszeile steht, solange der Abrechnungsstand fehlt (Halbgeviertstrich). */
const OHNE_STUNDEN = '–';

/**
 * Die Zahl der Fusszeile: intern die Summe der erfassten Stunden, sonst die Nettosumme.
 *
 * Beide Zahlen kommen vom Server — die Nettosumme mit dem Angebot, die Stundensumme mit dem
 * Abrechnungsstand (Issue #231). In der Ansicht entsteht keine (E10); faellt der Abrechnungsweg
 * aus, steht darum der Strich und keine Null.
 */
function summeZu(angebot: Angebot, abrechnung: Abrechnungsstand): string {
  if (!angebot.intern) {
    return euro(angebot.summeInCent);
  }
  return abrechnung.art === 'daten'
    ? stundenWort(abrechnung.abrechnung.angefallenInHundertsteln)
    : OHNE_STUNDEN;
}

/**
 * Eine Zeile der internen Positionstafel: die Bezeichnung und die erfassten Stunden (Issue #235).
 *
 * <b>Kein Ueberschreitungshinweis.</b> Er misst erfasste Zeit gegen ein angebotenes Kontingent, und
 * intern ist nichts angeboten; die Zahl steht fuer sich (Kriterium 6).
 *
 * <b>Die Stunden tragen hier ihre Einheit</b> ({@link stundenWort}) und nicht wie am Kundenangebot
 * nur die Ziffern: Dort sagt die Spalte „Einheit" daneben, worin gerechnet wird — die gibt es
 * intern nicht.
 *
 * Ohne Stand bleibt die Zelle leer statt bei 0 zu stehen: Der Abrechnungsweg kann fuer sich
 * ausfallen, und eine erfundene Null waere dann eine Auskunft, die niemand gegeben hat.
 */
function InternePositionszeile({
  position,
  stand,
}: {
  readonly position: AngebotPosition;
  readonly stand: Abrechnungsposition | undefined;
}) {
  return (
    <Box component="tr">
      <Box component="td" sx={{ fontWeight: 500 }}>
        {position.bezeichnung}
      </Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ textAlign: 'right', whiteSpace: 'nowrap' }}
      >
        {stand === undefined ? null : stundenWort(stand.angefallenInHundertsteln)}
      </Box>
    </Box>
  );
}

/**
 * Die Zelle „Angefallen" einer Positionszeile (Issue #193, Kriterien 7, 8).
 *
 * Leer, solange die Position keinen buchbaren Stand hat. Sonst die erfassten Stunden und, wo sie
 * das Kontingent sprengen, der Hinweis mit der Menge darueber (Kriterium 8).
 */
function AngefallenZelle({ stand }: { readonly stand: Abrechnungsposition | undefined }) {
  if (!stand?.buchbar) {
    return <Box component="td" sx={{ textAlign: 'right' }} />;
  }
  // Was ueber das Kontingent hinaus erfasst wurde (Kriterium 8), oder 0.
  const ueberKontingent = Math.max(
    0,
    stand.angefallenInHundertsteln - stand.angebotenInHundertsteln,
  );
  return (
    <Box component="td" sx={{ textAlign: 'right' }}>
      <Box className={ZAHLEN_KLASSE} sx={{ whiteSpace: 'nowrap' }}>
        {dezimal(stand.angefallenInHundertsteln, ',')}
      </Box>
      {ueberKontingent > 0 ? (
        // Der Hinweis steht an der Zahl, die das Kontingent sprengt (Kriterium 8).
        <Box sx={{ fontSize: 12.5 }}>
          <Ueberschreitungshinweis
            mengeInHundertsteln={ueberKontingent}
            angebotPositionId={stand.angebotPositionId}
            art="angefallen"
          />
        </Box>
      ) : null}
    </Box>
  );
}

/**
 * Eine Zeile der Positionstafel (Kriterien 4, 5, 26; Issue #193, Kriterien 7, 8, 11).
 *
 * `stand` ist der Abrechnungsstand dieser Position, oder `undefined` — dann bleiben die Zellen der
 * Zusatzspalten leer. Eine Position ohne Stand bei geladenem Abrechnungsweg gibt es nicht; die
 * leere Zelle ist hier die ehrlichere als eine erfundene Null.
 *
 * `zusatz` entscheidet, <b>welche</b> Zellen die Zeile ueberhaupt setzt — sie muss dieselben
 * stellen wie der Kopf der Tafel, sonst verrutscht die Spalte.
 *
 * <b>Die Zelle „Angefallen" haengt an `buchbar`</b>, nicht an der Zahl: 0 angefallene Stunden an
 * einer buchbaren Position sind eine Auskunft, an einer Festpreisposition dagegen keine — dort
 * bleibt die Zelle leer.
 *
 * `intern` schaltet auf die kurze Zeile zu {@link SPALTEN_INTERN} um (Issue #235).
 */
function Positionszeile({
  position,
  stand,
  zusatz,
  intern,
}: {
  readonly position: AngebotPosition;
  readonly stand: Abrechnungsposition | undefined;
  readonly zusatz: Zusatzspalten;
  readonly intern: boolean;
}) {
  if (intern) {
    return <InternePositionszeile position={position} stand={stand} />;
  }
  return (
    <Box component="tr">
      <Box component="td" sx={{ fontWeight: 500 }}>
        {position.bezeichnung}
      </Box>
      <Box component="td">{MODUS_WORT[position.abrechnungsmodus]}</Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ textAlign: 'right', whiteSpace: 'nowrap' }}
      >
        {dezimal(position.mengeInHundertsteln, ',')}
      </Box>
      {!zusatz.angefallen ? null : <AngefallenZelle stand={stand} />}
      {!zusatz.stand ? null : (
        <>
          <Box
            component="td"
            className={ZAHLEN_KLASSE}
            sx={{ textAlign: 'right', whiteSpace: 'nowrap' }}
          >
            {stand === undefined ? null : dezimal(stand.abgerechnetInHundertsteln, ',')}
          </Box>
          <Box component="td" sx={{ textAlign: 'right' }}>
            {stand === undefined ? null : (
              <>
                <Box className={ZAHLEN_KLASSE} sx={{ whiteSpace: 'nowrap' }}>
                  {dezimal(stand.offenInHundertsteln, ',')}
                </Box>
                {stand.ueberschreitungInHundertsteln > 0 ? (
                  // Der Hinweis steht bei „Offen": Dort steht 0,00, und er sagt, warum das zu wenig
                  // ist.
                  <Box sx={{ fontSize: 12.5 }}>
                    <Ueberschreitungshinweis
                      mengeInHundertsteln={stand.ueberschreitungInHundertsteln}
                      angebotPositionId={stand.angebotPositionId}
                    />
                  </Box>
                ) : null}
              </>
            )}
          </Box>
        </>
      )}
      <Box component="td">{EINHEIT_WORT[position.einheit]}</Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ textAlign: 'right', whiteSpace: 'nowrap' }}
      >
        {euro(position.einzelpreisInCent)}
      </Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ textAlign: 'right', whiteSpace: 'nowrap', fontWeight: 600 }}
      >
        {euro(position.betragInCent)}
      </Box>
      <Box component="td" className={ZAHLEN_KLASSE}>
        {position.leistungsjahr}
      </Box>
    </Box>
  );
}

/** Die Angaben des Angebots als Stammdaten-Liste (Vorlage `.stamm` Z. 100–102). */
function Angaben({ angebot }: { readonly angebot: Angebot }) {
  const zeilen: readonly { name: string; wert: ReactNode }[] = [
    {
      name: 'Firma',
      wert: (
        <Link
          component={RouterLink}
          to={`/firmen/${String(angebot.firmaId)}`}
          underline="hover"
          sx={{ fontSize: 13.5, fontWeight: 500 }}
        >
          {angebot.firmaName}
        </Link>
      ),
    },
    // Der Ansprechpartner ist optional (Issue #126); ohne ihn steht die Zeile gar nicht da.
    ...(angebot.ansprechpartnerName === null
      ? []
      : [{ name: 'Ansprechpartner', wert: angebot.ansprechpartnerName }]),
    { name: 'Angebotsdatum', wert: tagWort(angebot.angebotDatum) },
    {
      name: 'Status',
      // Das Kennzeichen steht neben dem Status und nicht in einer eigenen Zeile: Es sagt, welche
      // der zwei Ketten gilt, und gehoert damit an den Status (Issue #235).
      wert: (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
          <AngebotsstatusChip status={angebot.status} />
          {angebot.intern ? <InternChip /> : null}
          {/* Das Storno steht neben dem Status und nicht an seiner Stelle (Plan #332, E13). */}
          {angebot.storniert ? <StorniertChip /> : null}
        </Box>
      ),
    },
    // Der Grund ist freiwillig (Kriterium 5); ohne ihn steht die Zeile gar nicht da.
    ...(angebot.stornoGrund === null ? [] : [{ name: 'Stornogrund', wert: angebot.stornoGrund }]),
  ];
  return <Angabenliste zeilen={zeilen} mittig testId="angebot-angaben" />;
}

/**
 * Eine Zeile der Rechnungstafel; die Nummernspalte traegt den Weg zur Rechnung.
 *
 * Dieselbe Form wie in {@link RechnungenPage}: ein Link je Zeile, und der Entwurf heisst „Entwurf",
 * weil er noch keine Nummer hat (Kriterium 15).
 */
function Rechnungszeile({ rechnung }: { readonly rechnung: AngebotRechnungZeile }) {
  return (
    <Box component="tr">
      <Box component="td" sx={{ fontWeight: 500, whiteSpace: 'nowrap' }}>
        <Box
          component={RouterLink}
          to={`/rechnungen/${String(rechnung.id)}`}
          className={ZAHLEN_KLASSE}
          sx={(theme) => ({
            color: 'inherit',
            textDecoration: 'none',
            '&:hover': { color: theme.vars.palette.kupferwolke.kupfer },
          })}
        >
          {rechnung.nummer ?? OHNE_NUMMER}
        </Box>
      </Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={{ whiteSpace: 'nowrap' }}>
        {tagWort(rechnung.rechnungDatum)}
      </Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ fontWeight: 600, whiteSpace: 'nowrap', textAlign: 'right' }}
      >
        {euro(rechnung.bruttoInCent)}
      </Box>
      <Box component="td">
        <RechnungszustandChip zustand={rechnung.zustand} />
      </Box>
    </Box>
  );
}

/** Was in der Karte „Rechnungen" steht: Ladehinweis, Meldung, Satz zur Leere oder die Tafel. */
function rechnungenInhalt(stand: Abrechnungsstand): ReactNode {
  if (stand.art === 'ausfall') {
    return <Alert severity="error">{AUSFALL_ABRECHNUNG}</Alert>;
  }
  if (stand.art === 'laedt') {
    return (
      <Typography
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
      >
        {LAEDT_ABRECHNUNG}
      </Typography>
    );
  }
  if (stand.abrechnung.rechnungen.length === 0) {
    return (
      <Typography
        role="status"
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textMatt })}
      >
        {OHNE_RECHNUNG}
      </Typography>
    );
  }
  return (
    <Tafel beschriftung="Rechnungen" spalten={SPALTEN_RECHNUNGEN}>
      {stand.abrechnung.rechnungen.map((rechnung) => (
        <Rechnungszeile key={rechnung.id} rechnung={rechnung} />
      ))}
    </Tafel>
  );
}

/**
 * Die Rueckfrage vor dem Storno (Issue #340, #330 Kriterium 6) mit dem Feld fuer den Grund
 * (Kriterium 5).
 *
 * Nach dem Muster der `Rueckfrage` in {@link RechnungPage}: ein eigener Dialog und nicht das
 * `confirm` des Browsers, die bestaetigende Taste in der Rose-Toenung der folgenreichen Aktion und
 * nicht im Kupferverlauf. Die Taste, von der die Rueckfrage ausgeht, bleibt stehen, waehrend der
 * Dialog offen ist — den Fokus gibt MUI darum selbst zurueck.
 *
 * <b>Das Feld lebt im Dialog</b>: Er entsteht mit jedem Oeffnen neu, und ein abgebrochener Grund
 * steht beim naechsten Mal nicht mehr da. „Abbrechen" sendet nichts.
 *
 * <b>Ein leeres Feld heisst „ohne Grund"</b> und geht als `null` hinaus — auch eines aus blossem
 * Leerraum: Der Server nimmt einen leeren Grund nicht an (E4), und ein Storno daran scheitern zu
 * lassen, waere eine Abweisung ohne Not.
 */
function Stornodialog({
  onJa,
  onEnde,
}: {
  readonly onJa: (grund: string | null) => void;
  readonly onEnde: () => void;
}) {
  const titelId = useId();
  const [grund, setzeGrund] = useState('');
  return (
    <Dialog open onClose={onEnde} fullWidth maxWidth="xs" aria-labelledby={titelId}>
      <DialogTitle id={titelId}>{STORNIEREN}</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
        <DialogContentText>{STORNO_FRAGE}</DialogContentText>
        <TextField
          label="Grund (freiwillig)"
          value={grund}
          onChange={(ereignis) => {
            setzeGrund(ereignis.target.value);
          }}
          fullWidth
          slotProps={{ inputLabel: { shrink: true }, htmlInput: { maxLength: GRUND_LAENGE } }}
        />
      </DialogContent>
      <DialogActions sx={{ padding: '4px 24px 20px', gap: '10px' }}>
        <WeicheTaste onClick={onEnde}>Abbrechen</WeicheTaste>
        <Button
          type="button"
          onClick={() => {
            onEnde();
            onJa(grund.trim() === '' ? null : grund);
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
          {STORNIEREN}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

/** Ueber jeder Angebotsansicht stehen die Firmen und — sobald bekannt — die Firma (E6). */
const ZU_FIRMEN: PfadVerweis = { titel: 'Firmen', ziel: '/firmen' };

export default function AngebotPage() {
  const { angebotId } = useParams();
  const kennung = kennungAus(angebotId);
  const navigate = useNavigate();
  const titelId = useId();
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });
  const [abrechnung, setzeAbrechnung] = useState<Abrechnungsstand>({ art: 'laedt' });
  // Zaehlt die Lesungen des Abrechnungsstands — jeder Wechsel am Angebot stoesst eine neue an.
  const [abrechnungslauf, setzeAbrechnungslauf] = useState(0);
  const [stornofrage, setzeStornofrage] = useState(false);
  const [meldung, setzeMeldung] = useState<string | null>(null);
  const [laeuft, setzeLaeuft] = useState(false);
  const [wahlOffen, setzeWahlOffen] = useState(false);
  const [wahlmeldung, setzeWahlmeldung] = useState<string | null>(null);
  const [monat, setzeMonat] = useState(monatswahlWert);
  // Solange das Angebot nicht gelesen ist, traegt die Endstufe das Wort „Angebot"; die Firma kommt
  // mit dem Angebot, denn erst das Angebot weiss, an wen es geht.
  useKopfPfad(
    stand.art === 'daten'
      ? [
          ZU_FIRMEN,
          {
            titel: stand.angebot.firmaName,
            ziel: `/firmen/${String(stand.angebot.firmaId)}`,
          },
        ]
      : [ZU_FIRMEN],
    stand.art === 'daten' ? ueberschriftZu(stand.angebot) : 'Angebot',
  );

  useEffect(() => {
    if (kennung === null) {
      // Eine Kennung, die keine ist, geht gar nicht erst ans Netz (kennung.ts).
      setzeStand({ art: 'unbekannt' });
      return;
    }
    void angebotLesen(kennung)
      .then((angebot) => {
        setzeStand({ art: 'daten', angebot });
      })
      .catch((ursache: unknown) => {
        setzeStand(nichtGefunden(ursache) ? { art: 'unbekannt' } : { art: 'ausfall' });
      });
  }, [kennung]);

  useEffect(() => {
    if (kennung === null) {
      // Wie beim Angebot: Eine Kennung, die keine ist, geht gar nicht erst ans Netz.
      return;
    }
    let gueltig = true;
    void angebotAbrechnung(kennung)
      .then((gelesen) => {
        if (gueltig) {
          setzeAbrechnung({ art: 'daten', abrechnung: gelesen });
        }
      })
      .catch(() => {
        if (gueltig) {
          setzeAbrechnung({ art: 'ausfall' });
        }
      });
    return () => {
      gueltig = false;
    };
  }, [kennung, abrechnungslauf]);

  /**
   * Ein Wechsel am Angebot — Status oder Storno: Der neue Stand kommt aus der Antwort, nicht aus
   * der Oberflaeche. Danach wird der Abrechnungsstand neu gelesen, denn `schreibbar` haengt an
   * beidem (Plan #332, E9).
   *
   * `meldungZu` sagt, was nach einem Fehlschlag ueber der Karte steht.
   */
  const schalten = async (
    weg: (id: number) => Promise<Angebot>,
    angebot: Angebot,
    meldungZu: (ursache: unknown) => string = () => AUSFALL_AKTION,
  ) => {
    setzeMeldung(null);
    setzeLaeuft(true);
    try {
      setzeStand({ art: 'daten', angebot: await weg(angebot.id) });
      setzeAbrechnungslauf((lauf) => lauf + 1);
    } catch (ursache: unknown) {
      setzeMeldung(meldungZu(ursache));
    } finally {
      setzeLaeuft(false);
    }
  };

  /** Die Meldung nach einem gescheiterten Storno — die des Servers, wo er eine schickt. */
  const stornomeldung = (ursache: unknown) => serverMeldung(ursache, AUSFALL_STORNO);

  /**
   * „Anlegen" im Dialog: Der Entwurf entsteht am Server, die Antwort nennt seine Kennung.
   *
   * Nach dem Fehlschlag bleibt der Dialog stehen — die Wahl des Monats ist dann noch da, und der
   * Betrachter kann eine andere treffen, ohne von vorn zu beginnen.
   */
  const entwurfAnlegen = async (angebot: Angebot) => {
    setzeWahlmeldung(null);
    setzeLaeuft(true);
    try {
      const entwurf = await rechnungAnlegen(angebot.id, monatOderKeiner(monat));
      navigate(`/rechnungen/${String(entwurf.id)}`);
    } catch (ursache: unknown) {
      // Die Meldung des Servers, wo er eine schickt — er allein weiss, warum er abgewiesen hat.
      setzeWahlmeldung(serverMeldung(ursache, AUSFALL_ENTWURF));
      setzeLaeuft(false);
    }
  };

  /** Die Wahl schliessen — ohne etwas anzulegen; die Meldung des letzten Versuchs geht mit. */
  const wahlSchliessen = () => {
    setzeWahlOffen(false);
    setzeWahlmeldung(null);
  };

  /** Der Dialog mit der einen Frage: welcher Monat die Mengen vorbelegt (Issue #203). */
  function wahlZu(angebot: Angebot): ReactNode {
    return (
      <Dialog
        open
        onClose={wahlSchliessen}
        fullWidth
        maxWidth="xs"
        aria-labelledby={titelId}
      >
        <DialogTitle id={titelId} sx={{ fontSize: 16 }}>
          {WAHL_TITEL}
        </DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {wahlmeldung === null ? null : <Alert severity="error">{wahlmeldung}</Alert>}
          <Monatswahl monat={monat} setzeMonat={setzeMonat} disabled={laeuft} />
        </DialogContent>
        <DialogActions sx={{ padding: '4px 24px 20px', gap: '10px' }}>
          <WeicheTaste onClick={wahlSchliessen} disabled={laeuft}>
            Abbrechen
          </WeicheTaste>
          <KupferTaste
            onClick={() => {
              void entwurfAnlegen(angebot);
            }}
            disabled={laeuft}
          >
            Anlegen
          </KupferTaste>
        </DialogActions>
      </Dialog>
    );
  }

  /**
   * Steht „Rechnung schreiben" da? Wo der Server `schreibbar` meldet (Plan #332, E9).
   *
   * Ohne gelesenen Abrechnungsstand steht sie nicht: Was sich abrechnen laesst, weiss nur der
   * Server, und eine Taste auf Verdacht fuehrte in eine Abweisung.
   */
  function schreibbar(): boolean {
    return abrechnung.art === 'daten' && abrechnung.abrechnung.schreibbar;
  }

  /**
   * Die zwei Tasten der internen Kette (Issue #235, E15).
   *
   * Dieselben zwei Wege wie am Kundenangebot, nur anders beschriftet: „Läuft" ist der Anfang der
   * Kette und traegt darum kein „Wieder öffnen", „Abgeschlossen" ihr Ende und darum kein
   * „Abschließen" — genau wie dort „Angelegt" und „Abgerechnet".
   */
  function interneTastenZu(angebot: Angebot): ReactNode {
    return (
      <>
        {angebot.status === 'LAEUFT' ? null : (
          <WeicheTaste
            onClick={() => {
              void schalten(angebotStatusZurueck, angebot);
            }}
            disabled={laeuft}
            symbol={<IconArrowLeft size={SYMBOL_TASTE} stroke={1.8} />}
          >
            Wieder öffnen
          </WeicheTaste>
        )}
        {angebot.status === 'ABGESCHLOSSEN' ? null : (
          <KupferTaste
            onClick={() => {
              void schalten(angebotStatusWeiter, angebot);
            }}
            disabled={laeuft}
            symbol={<IconCircleCheck size={SYMBOL_TASTE} stroke={1.8} />}
          >
            Abschließen
          </KupferTaste>
        )}
      </>
    );
  }

  /**
   * Die Tasten des Kundenangebots: „Rechnung schreiben", das Storno und der Weg durch die
   * Statuskette.
   *
   * Am stornierten Angebot ist die Kette eingefroren (Plan #332, E2): Dort stehen allein
   * „Storno zurücknehmen" und, wo der Server es erlaubt, „Rechnung schreiben" ueber die bis zum
   * Storno gebuchten Stunden (#330, Kriterium 7).
   */
  function externeTastenZu(angebot: Angebot): ReactNode {
    const rechnungTaste = schreibbar() ? (
      <WeicheTaste
        onClick={() => {
          setzeWahlOffen(true);
        }}
        disabled={laeuft}
        symbol={<IconFilePlus size={SYMBOL_TASTE} stroke={1.8} />}
      >
        Rechnung schreiben
      </WeicheTaste>
    ) : null;
    if (angebot.storniert) {
      return (
        <>
          {rechnungTaste}
          {/* Keine Rueckfrage: Die Ruecknahme stellt nur den Zustand von vorher wieder her. */}
          <WeicheTaste
            onClick={() => {
              void schalten(angebotStornoZuruecknehmen, angebot, stornomeldung);
            }}
            disabled={laeuft}
            symbol={<IconArrowBackUp size={SYMBOL_TASTE} stroke={1.8} />}
          >
            Storno zurücknehmen
          </WeicheTaste>
        </>
      );
    }
    return (
      <>
        {rechnungTaste}
        {STORNIERBAR.has(angebot.status) ? (
          <WeicheTaste
            onClick={() => {
              setzeStornofrage(true);
            }}
            disabled={laeuft}
            symbol={<IconBan size={SYMBOL_TASTE} stroke={1.8} />}
          >
            {STORNIEREN}
          </WeicheTaste>
        ) : null}
        {angebot.status === 'ANGELEGT' ? null : (
          <WeicheTaste
            onClick={() => {
              void schalten(angebotStatusZurueck, angebot);
            }}
            disabled={laeuft}
            symbol={<IconArrowLeft size={SYMBOL_TASTE} stroke={1.8} />}
          >
            Status zurück
          </WeicheTaste>
        )}
        {angebot.status === 'ABGERECHNET' ? null : (
          <KupferTaste
            onClick={() => {
              void schalten(angebotStatusWeiter, angebot);
            }}
            disabled={laeuft}
            symbol={<IconArrowRight size={SYMBOL_TASTE} stroke={1.8} />}
          >
            Status weiter
          </KupferTaste>
        )}
      </>
    );
  }

  /**
   * Die Aktionen neben der Ueberschrift; an den Enden der Reihe fehlt die jeweilige Taste.
   *
   * „Bearbeiten" steht in jedem Status und bei beiden Arten (Kriterium 5) — nur am stornierten
   * Angebot nicht: Dort lassen sich Positionen, Titel und Beschreibung nicht aendern (#330,
   * Kriterium 10); Kommentare und Anlagen haben ihre eigenen Karten. Was daneben steht, entscheidet
   * das Kennzeichen — die Reihe verzweigt als Ganzes und nicht Taste fuer Taste.
   */
  function aktionenZu(angebot: Angebot): ReactNode {
    return (
      <Box
        data-testid="angebot-aktionen"
        sx={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}
      >
        {angebot.storniert ? null : (
          <WeicheTaste
            to={`/angebote/${String(angebot.id)}/bearbeiten`}
            symbol={<IconPencil size={SYMBOL_TASTE} stroke={1.8} />}
          >
            Bearbeiten
          </WeicheTaste>
        )}
        {angebot.intern ? interneTastenZu(angebot) : externeTastenZu(angebot)}
      </Box>
    );
  }

  /** Die Karte mit Ueberschrift, Angaben und Aktionen, darunter Beschreibung und Positionen. */
  function inhaltZu(angebot: Angebot): ReactNode {
    // Der Stand je Position, sobald der Abrechnungsweg geladen ist — er traegt beide Zusaetze.
    const staende =
      abrechnung.art === 'daten'
        ? new Map(
            abrechnung.abrechnung.positionen.map((position) => [
              position.angebotPositionId,
              position,
            ]),
          )
        : null;
    // „Abgerechnet" und „Offen" erst mit einer Rechnung, „Angefallen" schon mit einer buchbaren
    // Position: Zwei Fragen an denselben Stand, und die zweite haengt nicht an der ersten.
    const zusatz: Zusatzspalten = {
      angefallen:
        abrechnung.art === 'daten' &&
        abrechnung.abrechnung.positionen.some((position) => position.buchbar),
      stand: abrechnung.art === 'daten' && abrechnung.abrechnung.rechnungen.length > 0,
    };
    return (
      <>
        <Karte titel={ueberschriftZu(angebot)} titelEbene={1} werkzeug={aktionenZu(angebot)}>
          <Angaben angebot={angebot} />
        </Karte>
        {wahlOffen ? wahlZu(angebot) : null}
        {stornofrage ? (
          <Stornodialog
            onJa={(grund) => {
              void schalten((id) => angebotStornieren(id, grund), angebot, stornomeldung);
            }}
            onEnde={() => {
              setzeStornofrage(false);
            }}
          />
        ) : null}
        {angebot.beschreibung === null ? null : (
          <Karte titel="Beschreibung">
            <Typography sx={{ fontSize: 13.5, whiteSpace: 'pre-wrap' }}>
              {angebot.beschreibung}
            </Typography>
          </Karte>
        )}
        <Karte titel="Positionen" anzahl={angebot.positionen.length}>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            {angebot.positionen.length === 0 ? (
              <Typography
                role="status"
                sx={(theme) => ({
                  fontSize: 12.5,
                  color: theme.vars.palette.kupferwolke.textMatt,
                })}
              >
                {OHNE_POSITION}
              </Typography>
            ) : (
              <Tafel beschriftung="Positionen" spalten={spaltenZu(zusatz, angebot.intern)}>
                {angebot.positionen.map((position) => (
                  // Die Kennung ist der Schluessel: Seit Issue #171 traegt jede Position eine
                  // eigene und bleibt ueber ein Speichern hinweg dieselbe. Die Reihenfolge der
                  // Liste bleibt die gezeigte (E24) — sie ordnet, sie benennt nicht mehr.
                  <Positionszeile
                    key={position.id}
                    position={position}
                    stand={staende?.get(position.id)}
                    zusatz={zusatz}
                    intern={angebot.intern}
                  />
                ))}
              </Tafel>
            )}
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
              <Typography sx={{ fontSize: 13.5, fontWeight: 600 }}>
                {summenwortZu(angebot.intern)}
              </Typography>
              <Typography
                data-testid="angebot-summe"
                className={ZAHLEN_KLASSE}
                sx={{ fontSize: 17, fontWeight: 800 }}
              >
                {summeZu(angebot, abrechnung)}
              </Typography>
              {/* Der Hinweis gilt Betraegen; am internen Angebot gibt es keine (Issue #235). */}
              {angebot.intern ? null : (
                <Typography
                  sx={(theme) => ({
                    fontSize: 12.5,
                    marginLeft: 'auto',
                    color: theme.vars.palette.kupferwolke.textSchwach,
                  })}
                >
                  {NETTO}
                </Typography>
              )}
            </Box>
          </Box>
        </Karte>
        <Karte
          titel="Rechnungen"
          anzahl={abrechnung.art === 'daten' ? abrechnung.abrechnung.rechnungen.length : undefined}
        >
          {rechnungenInhalt(abrechnung)}
        </Karte>
        <Anlagen angebotId={angebot.id} />
        <Kommentare angebotId={angebot.id} />
      </>
    );
  }

  let inhalt: ReactNode;
  if (stand.art === 'daten') {
    inhalt = inhaltZu(stand.angebot);
  } else if (stand.art === 'laedt') {
    inhalt = (
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
    );
  } else {
    inhalt = (
      <Karte>
        <Alert severity="error">{stand.art === 'unbekannt' ? NICHT_GEFUNDEN : AUSFALL}</Alert>
      </Karte>
    );
  }

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        // Aussenabstand und Spalt bringt der Rahmen mit; hier bleibt nur der Abstand zwischen
        // den Bereichen der Buehne (CLAUDE-design.md, „Rahmen").
        gap: ABSTAND_BUEHNE,
      }}
    >
      {meldung === null ? null : <Alert severity="error">{meldung}</Alert>}
      {inhalt}
    </Box>
  );
}
