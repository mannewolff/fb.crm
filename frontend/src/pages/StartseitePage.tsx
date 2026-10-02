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
  StartseiteAngebotszeile,
  Startseitenstand,
} from '../api/startseite';
import AngebotsstatusChip from '../components/AngebotsstatusChip';
import Karte from '../components/Karte';
import Kennzahlkachel from '../components/Kennzahlkachel';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import Tafel from '../components/Tafel';
import WeicheTaste from '../components/WeicheTaste';
import { alsMonat, monatWort, stundenWort } from '../lib/arbeitszeit';
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
 * <b>Der Monat steht in der Adresse</b> (`?monat=JJJJ-MM`, E18): Er ist teilbar, uebersteht das
 * Neuladen, und „zurueck" nimmt den Wechsel zurueck — dieselbe Entscheidung wie in
 * {@link ArbeitszeitPage}. Fehlt er oder ist er keiner, fragt die Ansicht ohne Parameter; welcher
 * Monat der laufende ist, entscheidet der Server an seiner Uhr in der Geschaeftszone (E8).
 *
 * <b>Der gezeigte Monat kommt aus der Antwort</b> und nicht aus der Adresse: Der Server nimmt einen
 * Monat ausserhalb der zwoelf waehlbaren wie einen fehlenden (E18), und nur seine Antwort weiss,
 * welcher dann gilt. Ein hier gehaltener Monat waere daneben eine zweite Wahrheit.
 *
 * <b>Die Wahl bietet die Monate der Antwort an</b> (Feld `monate`) und keine aus
 * {@link laufenderMonat} gerechneten (E17): Die liest die Browser-Uhr, und am Monatsersten stuende
 * in der Liste ein anderer Monat als in den Zahlen daneben. {@link Monatswahl} bleibt darum
 * unberuehrt — sie belegt eine Rechnung vor und fuehrt eine eigene, feste Liste.
 *
 * <b>Jede Kennzahl traegt ihre Liste</b>: Die Kachel nennt die Zahl, die Karte darunter sagt, woraus
 * sie entstanden ist. Bei „Abgerechnet" stehen die Rechnungen nicht in der Antwort — dort fuehrt
 * eine weiche Taste auf `/rechnungen`, statt sie hier ein zweites Mal zu holen.
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

/** Der Name des Parameters, unter dem der Monat in der Adresse steht (E18). */
const PARAM_MONAT = 'monat';

/** Der zugaengliche Name der Monatswahl. Sie steht in einer Werkzeugleiste und ohne Etikett. */
const MONAT_NAME = 'Monat';

const LAEDT = 'Der Geschäftsstand wird geladen …';
const AUSFALL = 'Der Geschäftsstand ist gerade nicht zu erreichen. Bitte später erneut versuchen.';

const TITEL_IN_ARBEIT = 'Angebote in Arbeit';
const TITEL_OFFEN = 'Noch nicht abgerechnet';
const TITEL_ABGERECHNET = 'Abgerechnet';

/** Die Zeile der internen Stunden, getrennt von allen Betraegen (#207, Kriterium 9). */
const TITEL_INTERNE_STUNDEN = 'Interne Stunden im gewählten Monat';

const LEER_IN_ARBEIT = 'Kein Angebot ist gerade in Arbeit.';
const LEER_OFFEN = 'Nichts offen — alle erfasste Zeit ist abgerechnet.';
const LEER_ABGERECHNET = 'Keine Rechnung in diesem Monat.';

const SPALTEN_IN_ARBEIT: readonly string[] = ['Firma', 'Angebot', 'Status'];
const SPALTEN_OFFEN: readonly string[] = ['Firma', 'Angebot', 'Anteil'];

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
async function laden(monat: string | null): Promise<Stand> {
  try {
    return { art: 'daten', geschaeft: await startseite(monat ?? undefined) };
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
 * Die Wahl des Monats im Kartenkopf: die zwoelf Monate der Antwort, neuester zuerst.
 *
 * Ein natives `select` und kein Feld mit Etikett: In einer Werkzeugleiste traegt der Waehler seinen
 * Namen fuer Hilfsmittel und nicht sichtbar (CLAUDE-design.md, „Felder"), und die Liste ist
 * abgeschlossen — zwoelf Monate, keine freie Eingabe.
 */
function Monatsliste({
  monat,
  monate,
  waehlen,
}: {
  readonly monat: string;
  readonly monate: readonly string[];
  readonly waehlen: (neu: string) => void;
}) {
  return (
    <Box
      component="select"
      aria-label={MONAT_NAME}
      value={monat}
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
      {monate.map((wert) => (
        <option key={wert} value={wert}>
          {monatWort(wert)}
        </option>
      ))}
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
        zweitzeile={`Im Monat erfasst: ${euro(geschaeft.nichtAbgerechnet.erfasstImMonatInCent)}`}
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
 * Die internen Stunden des gewaehlten Monats als eigene Zeile unter der Kachelreihe.
 *
 * <b>Hier entsteht keine Zahl.</b> Der Wert kommt vom Server ({@code Startseitenstand}); die
 * Ansicht setzt nur die Einheit daran ({@link stundenWort}, wie in {@link ArbeitszeitPage}).
 */
function InterneStunden({ stundenInHundertsteln }: { readonly stundenInHundertsteln: number }) {
  return (
    <Typography
      className={ZAHLEN_KLASSE}
      sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
    >
      {`${TITEL_INTERNE_STUNDEN}: ${stundenWort(stundenInHundertsteln)}`}
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
        <Box sx={{ display: 'flex', alignItems: 'center', gap: '14px', flexWrap: 'wrap' }}>
          {geschaeft.abgerechnet.anzahl === 0 ? (
            <Leersatz>{LEER_ABGERECHNET}</Leersatz>
          ) : null}
          <WeicheTaste
            to="/rechnungen"
            symbol={<IconFileInvoice size={SYMBOL_TASTE} stroke={1.8} />}
          >
            Zu den Rechnungen
          </WeicheTaste>
        </Box>
      </Karte>
    </>
  );
}

export default function StartseitePage() {
  useKopfPfad(KEIN_WEG, 'Start');
  const [parameter, setzeParameter] = useSearchParams();
  const monat = alsMonat(parameter.get(PARAM_MONAT));
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });

  useEffect(() => {
    let gueltig = true;
    setzeStand({ art: 'laedt' });
    void laden(monat).then((neu) => {
      // Ein juengerer Monat hat diesen Lauf abgeloest; seine Antwort ist die richtige.
      if (gueltig) {
        setzeStand(neu);
      }
    });
    return () => {
      gueltig = false;
    };
  }, [monat]);

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
        <InterneStunden stundenInHundertsteln={stand.geschaeft.interneStundenInHundertsteln} />
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
            <Monatsliste
              monat={stand.geschaeft.monat}
              monate={stand.geschaeft.monate}
              waehlen={(neu) => {
                // Geschoben statt ersetzt: Der Monatswechsel ist eine Handlung, die „zurueck"
                // zuruecknehmen koennen soll.
                setzeParameter({ [PARAM_MONAT]: neu });
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
