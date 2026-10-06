import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { IconClock, IconFileInvoice, IconReceipt, IconTool } from '@tabler/icons-react';
import { useEffect, useState } from 'react';
import type { ChangeEvent, ReactNode } from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';

import { startseite } from '../api/startseite';
import type {
  Anteilszeile,
  Monatszeile,
  StartseiteAngebotszeile,
  Startseitenstand,
  Zeitraumart,
} from '../api/startseite';
import AngebotsstatusChip from '../components/AngebotsstatusChip';
import Karte from '../components/Karte';
import Kennzahlkachel from '../components/Kennzahlkachel';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import Tafel from '../components/Tafel';
import WeicheTaste from '../components/WeicheTaste';
import { monatWort, stundenWort } from '../lib/arbeitszeit';
import { alsZeitraum, zeitraumWort } from '../lib/zeitraum';
import { euro } from '../lib/geld';
import { tagWort } from '../lib/tag';
import { RADIUS_RUND, ZAHLEN_KLASSE } from '../theme';

/**
 * Die Startseite: der Geschaeftsstand auf `/` (Issue #216; #206 Kriterien 1, 3 bis 8).
 *
 * Sie setzt zusammen, was andere hergestellt haben — der Anwendungsfall rechnet (#213), der Weg
 * liefert (#214), die {@link Kennzahlkachel} zeigt (#215). <b>Keine Zahl entsteht hier.</b> Was
 * dasteht, steht in der Antwort; gezaehlt wird nur die Laenge der Liste, die daneben zu sehen ist
 * (Plan #208, E20).
 *
 * <b>Der Zeitraum steht in der Adresse</b> (`?zeitraum=JJJJ-MM` oder `?zeitraum=JJJJ`, E18; Plan
 * #274, E1): Er ist teilbar, uebersteht das Neuladen, und „zurueck" nimmt den Wechsel zurueck —
 * fuer ein Jahr genauso wie fuer einen Monat, dieselbe Entscheidung wie in {@link ArbeitszeitPage}.
 * Fehlt er oder ist er keiner, fragt die Ansicht ohne Parameter; welcher Monat der laufende ist,
 * entscheidet der Server an seiner Uhr in der Geschaeftszone (E8).
 *
 * <b>Der gezeigte Zeitraum kommt aus der Antwort</b> und nicht aus der Adresse: Der Server nimmt
 * einen Zeitraum ausserhalb der waehlbaren wie einen fehlenden (E18), und nur seine Antwort weiss,
 * welcher dann gilt. Ein hier gehaltener Zeitraum waere daneben eine zweite Wahrheit. Auch ob ein
 * Monat oder ein Jahr gilt, liest die Ansicht an der Art der Antwort ab und nicht am Wert.
 *
 * <b>Die Wahl bietet die Zeitraeume der Antwort an</b> (Feld `waehlbar`) und keine aus
 * {@link laufenderMonat} gerechneten (E17): Die liest die Browser-Uhr, und am Monatsersten stuende
 * in der Liste ein anderer Monat als in den Zahlen daneben. {@link Monatswahl} bleibt darum
 * unberuehrt — sie belegt eine Rechnung vor und fuehrt eine eigene, feste Liste.
 *
 * <b>Jede Kennzahl traegt ihre Liste</b>: Die Kachel nennt die Zahl, die Karte darunter sagt, woraus
 * sie entstanden ist. Bei „Abgerechnet" stehen die Rechnungen nicht in der Antwort — dort fuehrt
 * eine weiche Taste auf `/rechnungen`, statt sie hier ein zweites Mal zu holen. Bei Jahreswahl
 * stehen darueber die Monate des Jahres mit ihrer Summe (Plan #274, E11, E13, E14).
 *
 * <b>Die internen Stunden stehen unter den Kacheln und nicht darin</b> (#207, Kriterium 9): Die
 * Kachelreihe traegt Betraege, interne Arbeit traegt keinen Preis. Eine Stundenzahl zwischen drei
 * Euro-Kacheln laese sich wie eine vierte Kennzahl in Euro.
 *
 * <b>Der Weg liegt auf dem Datum</b>, nicht auf der Zeile — wie in {@link Angebotsliste}: Ein
 * Angebot hat keine Nummer, und ein `tr` mit `onClick` waere fuer Tastatur und Screenreader kein
 * Weg.
 */

/** Die Ansicht ist die erste Stufe des Pfades — ueber ihr steht nichts. */
const KEIN_WEG: readonly PfadVerweis[] = [];

/** Der Name des Parameters, unter dem der Zeitraum in der Adresse steht (E18; Plan #274, E1). */
const PARAM_ZEITRAUM = 'zeitraum';

/** Der zugaengliche Name der Zeitraumwahl. Sie steht in einer Werkzeugleiste und ohne Etikett. */
const ZEITRAUM_NAME = 'Zeitraum';

/** Die Beschriftungen der beiden Gruppen der Wahl (Plan #274, E12). */
const GRUPPE_JAHRE = 'Jahre';
const GRUPPE_MONATE = 'Monate';

const LAEDT = 'Der Geschäftsstand wird geladen …';
const AUSFALL = 'Der Geschäftsstand ist gerade nicht zu erreichen. Bitte später erneut versuchen.';

const TITEL_IN_ARBEIT = 'Angebote in Arbeit';
const TITEL_OFFEN = 'Noch nicht abgerechnet';
const TITEL_ABGERECHNET = 'Abgerechnet';

/**
 * Was je Art des Zeitraums anders heisst (#273, Kriterien 5 und 7).
 *
 * Eine Tafel und keine Verzweigung an jeder Stelle: Die drei Beschriftungen wechseln immer
 * gemeinsam, und eine neue Art faellt dem Compiler hier auf und nicht erst beim Lesen der Seite.
 */
const WORTE: Readonly<
  Record<
    Zeitraumart,
    {
      /** Die Zweitzeile der Kachel „Noch nicht abgerechnet". */
      readonly erfasst: string;
      /** Die Zeile der internen Stunden, getrennt von allen Betraegen (#207, Kriterium 9). */
      readonly interneStunden: string;
      /** Der Satz der Karte „Abgerechnet" ohne Rechnung im Zeitraum. */
      readonly leerAbgerechnet: string;
    }
  >
> = {
  MONAT: {
    erfasst: 'Im Monat erfasst',
    interneStunden: 'Interne Stunden im gewählten Monat',
    leerAbgerechnet: 'Keine Rechnung in diesem Monat.',
  },
  JAHR: {
    erfasst: 'Im Jahr erfasst',
    interneStunden: 'Interne Stunden im gewählten Jahr',
    leerAbgerechnet: 'Keine Rechnung in diesem Jahr.',
  },
};

const LEER_IN_ARBEIT = 'Kein Angebot ist gerade in Arbeit.';
const LEER_OFFEN = 'Nichts offen — alle erfasste Zeit ist abgerechnet.';

const SPALTEN_IN_ARBEIT: readonly string[] = ['Firma', 'Angebot', 'Status'];
const SPALTEN_OFFEN: readonly string[] = ['Firma', 'Angebot', 'Anteil'];
const SPALTEN_ABGERECHNET: readonly string[] = ['Monat', 'Rechnungen', 'Netto', 'Brutto'];

/**
 * Die Gestalt der Kopfzelle der Summenzeile — wie `Monatssumme` in {@link ArbeitszeitPage} (E14).
 *
 * Das Polster steht hier und nicht in der {@link Tafel}: Die polstert ihre `td` ueber einen
 * Nachfahren-Selektor, und eine Kopfzelle im Rumpf faellt nicht darunter.
 */
const SUMMENKOPF = {
  textAlign: 'left',
  padding: '12px 14px',
  fontSize: 12.5,
  fontWeight: 700,
  whiteSpace: 'nowrap',
} as const;

/** Die Gestalt einer Betragszelle der Summenzeile. */
const SUMMENBETRAG = { fontWeight: 700, whiteSpace: 'nowrap', textAlign: 'right' } as const;

/** Die Symbolgroesse in den Kacheln (CLAUDE-design.md, „Bausteine": Symbolfeld 48 px). */
const SYMBOL_KACHEL = 22;

/** Die Symbolgroesse in den Tasten (wie in {@link RechnungenPage}). */
const SYMBOL_TASTE = 16;

/** Die kleinste Breite einer Kachel, unter der das Raster umbricht (Kriterium: 768 px). */
const KACHEL_MINDESTBREITE = 240;

/** Was die Ansicht gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly geschaeft: Startseitenstand }
  | { readonly art: 'fehler' };

/** Holt den Stand und macht auch aus dem Fehlschlag einen Stand. */
async function laden(zeitraum: string | null): Promise<Stand> {
  try {
    return { art: 'daten', geschaeft: await startseite(zeitraum ?? undefined) };
  } catch {
    return { art: 'fehler' };
  }
}

/** Ein kurzer Satz an der Stelle einer leeren Liste — eine Einladung, keine Stoerung. */
function Leersatz({ children }: { readonly children: string }) {
  return (
    <Typography
      role="status"
      sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textMatt })}
    >
      {children}
    </Typography>
  );
}

/**
 * Die Wahl des Zeitraums im Kartenkopf: die Jahre und die Monate der Antwort, je neuestes zuerst.
 *
 * Ein natives `select` und kein Feld mit Etikett: In einer Werkzeugleiste traegt der Waehler seinen
 * Namen fuer Hilfsmittel und nicht sichtbar (CLAUDE-design.md, „Felder"), und die Liste ist
 * abgeschlossen — keine freie Eingabe.
 *
 * <b>Zwei Gruppen und keine flache Liste</b> (Plan #274, E12): „2026" und „Oktober 2026" stuenden
 * flach ohne erkennbaren Unterschied nebeneinander; die Beschriftung der `optgroup` liest der
 * Screenreader beim Durchgehen mit.
 */
function Zeitraumwahl({
  zeitraum,
  jahre,
  monate,
  waehlen,
}: {
  readonly zeitraum: string;
  readonly jahre: readonly string[];
  readonly monate: readonly string[];
  readonly waehlen: (neu: string) => void;
}) {
  return (
    <Box
      component="select"
      aria-label={ZEITRAUM_NAME}
      value={zeitraum}
      onChange={(ereignis: ChangeEvent<HTMLSelectElement>) => {
        waehlen(ereignis.target.value);
      }}
      sx={(theme) => ({
        font: 'inherit',
        fontSize: 13.5,
        fontWeight: 600,
        cursor: 'pointer',
        padding: '10px 14px',
        borderRadius: `${RADIUS_RUND}px`,
        border: 0,
        boxShadow: `inset 0 0 0 1px ${theme.vars.palette.kupferwolke.linie}`,
        color: theme.vars.palette.kupferwolke.text,
        background: theme.vars.palette.kupferwolke.flaecheWeich,
      })}
    >
      <optgroup label={GRUPPE_JAHRE}>
        {jahre.map((wert) => (
          <option key={wert} value={wert}>
            {wert}
          </option>
        ))}
      </optgroup>
      <optgroup label={GRUPPE_MONATE}>
        {monate.map((wert) => (
          <option key={wert} value={wert}>
            {monatWort(wert)}
          </option>
        ))}
      </optgroup>
    </Box>
  );
}

/** Die drei Kacheln im Raster; es bricht um, sobald eine Kachel ihre Mindestbreite verliert. */
function Kacheln({ geschaeft }: { readonly geschaeft: Startseitenstand }) {
  return (
    <Box
      sx={{
        display: 'grid',
        gap: '18px',
        gridTemplateColumns: `repeat(auto-fit, minmax(${String(KACHEL_MINDESTBREITE)}px, 1fr))`,
      }}
    >
      <Kennzahlkachel
        toenung="pfirsich"
        symbol={<IconTool size={SYMBOL_KACHEL} stroke={1.8} aria-hidden />}
        beschriftung={TITEL_IN_ARBEIT}
        zahl={String(geschaeft.inArbeit.length)}
      />
      <Kennzahlkachel
        toenung="bernstein"
        symbol={<IconClock size={SYMBOL_KACHEL} stroke={1.8} aria-hidden />}
        beschriftung={TITEL_OFFEN}
        zahl={euro(geschaeft.nichtAbgerechnet.nettoInCent)}
        zweitzeile={`${WORTE[geschaeft.zeitraum.art].erfasst}: ${euro(geschaeft.nichtAbgerechnet.erfasstImZeitraumInCent)}`}
      />
      <Kennzahlkachel
        toenung="salbei"
        symbol={<IconReceipt size={SYMBOL_KACHEL} stroke={1.8} aria-hidden />}
        beschriftung={TITEL_ABGERECHNET}
        zahl={euro(geschaeft.abgerechnet.nettoInCent)}
        zweitzeile={`${euro(geschaeft.abgerechnet.bruttoInCent)} brutto`}
      />
    </Box>
  );
}

/**
 * Die internen Stunden des gewaehlten Zeitraums als eigene Zeile unter der Kachelreihe.
 *
 * <b>Hier entsteht keine Zahl.</b> Der Wert kommt vom Server ({@code Startseitenstand}); die
 * Ansicht setzt nur die Einheit daran ({@link stundenWort}, wie in {@link ArbeitszeitPage}).
 */
function InterneStunden({
  art,
  stundenInHundertsteln,
}: {
  readonly art: Zeitraumart;
  readonly stundenInHundertsteln: number;
}) {
  return (
    <Typography
      className={ZAHLEN_KLASSE}
      sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
    >
      {`${WORTE[art].interneStunden}: ${stundenWort(stundenInHundertsteln)}`}
    </Typography>
  );
}

/** Der Weg zum Angebot, auf seinem Datum — die Gestalt der Zeilenverweise aller Tafeln. */
function Angebotsweg({ angebotId, tag }: { readonly angebotId: number; readonly tag: string }) {
  return (
    <Box
      component={RouterLink}
      to={`/angebote/${String(angebotId)}`}
      className={ZAHLEN_KLASSE}
      sx={(theme) => ({
        color: 'inherit',
        textDecoration: 'none',
        '&:hover': { color: theme.vars.palette.kupferwolke.kupfer },
      })}
    >
      {tagWort(tag)}
    </Box>
  );
}

/** Eine Zeile der Angebote in Arbeit: Firma, Angebotsdatum, Status (Kriterium 4). */
function ArbeitZeile({ zeile }: { readonly zeile: StartseiteAngebotszeile }) {
  return (
    <Box component="tr">
      <Box component="td" sx={{ fontWeight: 600 }}>
        {zeile.firmaName}
      </Box>
      <Box component="td" sx={{ fontWeight: 500, whiteSpace: 'nowrap' }}>
        <Angebotsweg angebotId={zeile.angebotId} tag={zeile.angebotDatum} />
      </Box>
      <Box component="td">
        <AngebotsstatusChip status={zeile.status} />
      </Box>
    </Box>
  );
}

/** Eine Zeile der nicht abgerechneten Angebote: Firma, Angebotsdatum, Anteil (Kriterium 5). */
function OffenZeile({ zeile }: { readonly zeile: Anteilszeile }) {
  return (
    <Box component="tr">
      <Box component="td" sx={{ fontWeight: 600 }}>
        {zeile.firmaName}
      </Box>
      <Box component="td" sx={{ fontWeight: 500, whiteSpace: 'nowrap' }}>
        <Angebotsweg angebotId={zeile.angebotId} tag={zeile.angebotDatum} />
      </Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ fontWeight: 600, whiteSpace: 'nowrap', textAlign: 'right' }}
      >
        {euro(zeile.nettoInCent)}
      </Box>
    </Box>
  );
}

/**
 * Ein Monat des gewaehlten Jahres: Monatsname, Zahl der Rechnungen, netto, brutto (#273, 7).
 *
 * <b>Der Monatsname ist ein Link</b> auf `/?zeitraum=JJJJ-MM` und kein Schalter (Plan #274, E13) —
 * dieselbe Entscheidung wie beim Weg auf dem Datum in allen Tafeln: Ein Link ist teilbar, mit der
 * Tastatur erreichbar und nennt sein Ziel.
 */
function AbrechnungsmonatZeile({ zeile }: { readonly zeile: Monatszeile }) {
  return (
    <Box component="tr">
      <Box component="td" sx={{ fontWeight: 600, whiteSpace: 'nowrap' }}>
        <Box
          component={RouterLink}
          to={`/?${PARAM_ZEITRAUM}=${zeile.monat}`}
          sx={(theme) => ({
            color: 'inherit',
            textDecoration: 'none',
            '&:hover': { color: theme.vars.palette.kupferwolke.kupfer },
          })}
        >
          {monatWort(zeile.monat)}
        </Box>
      </Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ whiteSpace: 'nowrap', textAlign: 'right' }}
      >
        {String(zeile.anzahl)}
      </Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ fontWeight: 600, whiteSpace: 'nowrap', textAlign: 'right' }}
      >
        {euro(zeile.nettoInCent)}
      </Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ whiteSpace: 'nowrap', textAlign: 'right' }}
      >
        {euro(zeile.bruttoInCent)}
      </Box>
    </Box>
  );
}

/**
 * Die abschliessende Zeile der Liste der Monate: die Summe des Jahres (Plan #274, E14).
 *
 * Eine Zeile im Rumpf in der Gestalt von `Monatssumme` aus {@link ArbeitszeitPage} und kein
 * `tfoot`: Ein Fuss an der {@link Tafel} aenderte den geteilten Baustein fuer alle Aufrufer, um
 * einem einzigen zu dienen. <b>Hier entsteht keine Zahl</b> — die Summe ist die Kennzahl selbst.
 */
function Jahressumme({ geschaeft }: { readonly geschaeft: Startseitenstand }) {
  return (
    <Box component="tr">
      <Box component="th" scope="row" sx={SUMMENKOPF}>
        {`Summe ${zeitraumWort(geschaeft.zeitraum.wert)}`}
      </Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={SUMMENBETRAG}>
        {String(geschaeft.abgerechnet.anzahl)}
      </Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={SUMMENBETRAG}>
        {euro(geschaeft.abgerechnet.nettoInCent)}
      </Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={SUMMENBETRAG}>
        {euro(geschaeft.abgerechnet.bruttoInCent)}
      </Box>
    </Box>
  );
}

/**
 * Die Monate des gewaehlten Jahres samt Summe ueber der weichen Taste (#273, Kriterium 7).
 *
 * Unterschieden wird an der Art des Zeitraums und nicht daran, ob Monatszeilen kommen (Plan #274,
 * E11): Bei Monatswahl ist ihre Liste immer leer, und die Karte bleibt, wie sie war. Ein Jahr ohne
 * Rechnung traegt statt der Tafel seinen Satz neben der Taste.
 */
function Jahresliste({ geschaeft }: { readonly geschaeft: Startseitenstand }) {
  if (geschaeft.zeitraum.art === 'MONAT' || geschaeft.abgerechnet.anzahl === 0) {
    return null;
  }
  return (
    <Tafel beschriftung={TITEL_ABGERECHNET} spalten={SPALTEN_ABGERECHNET}>
      {geschaeft.abgerechnet.monate.map((zeile) => (
        <AbrechnungsmonatZeile key={zeile.monat} zeile={zeile} />
      ))}
      <Jahressumme geschaeft={geschaeft} />
    </Tafel>
  );
}

/** Die drei Karten unter den Kacheln: je Kennzahl, woraus sie entstanden ist. */
function Listen({ geschaeft }: { readonly geschaeft: Startseitenstand }) {
  return (
    <>
      <Karte titel={TITEL_IN_ARBEIT} anzahl={geschaeft.inArbeit.length}>
        {geschaeft.inArbeit.length === 0 ? (
          <Leersatz>{LEER_IN_ARBEIT}</Leersatz>
        ) : (
          <Tafel beschriftung={TITEL_IN_ARBEIT} spalten={SPALTEN_IN_ARBEIT}>
            {geschaeft.inArbeit.map((zeile) => (
              <ArbeitZeile key={zeile.angebotId} zeile={zeile} />
            ))}
          </Tafel>
        )}
      </Karte>
      <Karte titel={TITEL_OFFEN} anzahl={geschaeft.nichtAbgerechnet.angebote.length}>
        {geschaeft.nichtAbgerechnet.angebote.length === 0 ? (
          <Leersatz>{LEER_OFFEN}</Leersatz>
        ) : (
          <Tafel beschriftung={TITEL_OFFEN} spalten={SPALTEN_OFFEN}>
            {geschaeft.nichtAbgerechnet.angebote.map((zeile) => (
              <OffenZeile key={zeile.angebotId} zeile={zeile} />
            ))}
          </Tafel>
        )}
      </Karte>
      <Karte titel={TITEL_ABGERECHNET} anzahl={geschaeft.abgerechnet.anzahl}>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          <Jahresliste geschaeft={geschaeft} />
          <Box sx={{ display: 'flex', alignItems: 'center', gap: '14px', flexWrap: 'wrap' }}>
            {geschaeft.abgerechnet.anzahl === 0 ? (
              <Leersatz>{WORTE[geschaeft.zeitraum.art].leerAbgerechnet}</Leersatz>
            ) : null}
            <WeicheTaste
              to="/rechnungen"
              symbol={<IconFileInvoice size={SYMBOL_TASTE} stroke={1.8} />}
            >
              Zu den Rechnungen
            </WeicheTaste>
          </Box>
        </Box>
      </Karte>
    </>
  );
}

export default function StartseitePage() {
  useKopfPfad(KEIN_WEG, 'Start');
  const [parameter, setzeParameter] = useSearchParams();
  const zeitraum = alsZeitraum(parameter.get(PARAM_ZEITRAUM));
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });

  useEffect(() => {
    let gueltig = true;
    setzeStand({ art: 'laedt' });
    void laden(zeitraum).then((neu) => {
      // Ein juengerer Zeitraum hat diesen Lauf abgeloest; seine Antwort ist die richtige.
      if (gueltig) {
        setzeStand(neu);
      }
    });
    return () => {
      gueltig = false;
    };
  }, [zeitraum]);

  let kopfinhalt: ReactNode;
  if (stand.art === 'laedt') {
    kopfinhalt = (
      <Typography
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
      >
        {LAEDT}
      </Typography>
    );
  } else if (stand.art === 'fehler') {
    kopfinhalt = <Alert severity="error">{AUSFALL}</Alert>;
  } else {
    kopfinhalt = (
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
        <Kacheln geschaeft={stand.geschaeft} />
        <InterneStunden
          art={stand.geschaeft.zeitraum.art}
          stundenInHundertsteln={stand.geschaeft.interneStundenInHundertsteln}
        />
      </Box>
    );
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '22px' }}>
      <Karte
        titel="Start"
        titelEbene={1}
        werkzeug={
          stand.art === 'daten' ? (
            <Zeitraumwahl
              zeitraum={stand.geschaeft.zeitraum.wert}
              jahre={stand.geschaeft.waehlbar.jahre}
              monate={stand.geschaeft.waehlbar.monate}
              waehlen={(neu) => {
                // Geschoben statt ersetzt: Der Wechsel des Zeitraums ist eine Handlung, die
                // „zurueck" zuruecknehmen koennen soll.
                setzeParameter({ [PARAM_ZEITRAUM]: neu });
              }}
            />
          ) : undefined
        }
      >
        {kopfinhalt}
      </Karte>
      {stand.art === 'daten' ? <Listen geschaeft={stand.geschaeft} /> : null}
    </Box>
  );
}
