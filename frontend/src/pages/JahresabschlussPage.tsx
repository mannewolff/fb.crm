import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { IconCoinEuro, IconReceipt, IconReceiptTax } from '@tabler/icons-react';
import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { useParams } from 'react-router-dom';

import { jahresabschluss } from '../api/jahresabschluesse';
import type {
  Angebotsbilanz,
  Jahresabschluss,
  Jahresarbeitszeit,
  Kundenzeile,
  Steuerzeile,
} from '../api/jahresabschluesse';
import Karte from '../components/Karte';
import Kennzahlkachel from '../components/Kennzahlkachel';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import Tafel, { type TafelSpalte } from '../components/Tafel';
import { nichtGefunden } from '../lib/apifehler';
import { stundenWort } from '../lib/arbeitszeit';
import { euro } from '../lib/geld';
import { prozentWort } from '../lib/prozent';
import { ZAHLEN_KLASSE } from '../theme';

/**
 * Der Jahresabschluss eines Jahres auf `/jahresabschluesse/<jahr>` (#287, Kriterien 4 bis 11;
 * Plan #288, E3, E10, E11, E16, E17).
 *
 * Aufbau wie die Startseite: eine Kopfkarte mit der Kachelreihe, darunter je Thema eine
 * {@link Karte} mit {@link Tafel} — kein eigener Baustein (E17). <b>Keine Zahl entsteht hier.</b>
 * Gerechnet und gerundet hat der Server; die Ansicht setzt nur, was in der Antwort steht.
 *
 * <b>Die Toenungen der Kacheln</b> folgen ihrer festen Bedeutung (CLAUDE-design.md, „Toenungen"):
 * netto auf Salbei („Umsatz"), brutto auf Himmel („Information"), die Umsatzsteuer auf Flieder
 * („neutrale Kategorie"). Bernstein hiesse Warnung oder Grenze — beides ist die Umsatzsteuer eines
 * abgelaufenen Jahres nicht.
 *
 * <b>Ob die Steuertafel dasteht, entscheidet die Antwort</b> (E14, Kriterium 6): Der Server
 * schickt eine leere Liste, wo die Aufteilung nur eine Zeile ergaebe. Die Ansicht laesst die Karte
 * dann weg und prueft keine eigene Bedingung.
 *
 * <b>Eine nicht berechenbare Kennzahl</b> kommt als `null` (E11, Kriterium 11): Die Ansicht setzt
 * den Strich und daneben den Grund der Stelle im Wortlaut — als Wort, nicht nur im Tooltip, wie in
 * {@link JahresabschluessePage}. Eine 0 ist dagegen eine Zahl und steht als Zahl. Die Anteile am
 * Jahresumsatz summieren sich nicht zwingend auf 100,0 % (E10); die Ansicht gleicht nicht aus und
 * sortiert nicht nach — Reihenfolge und Anteile stehen fertig in der Antwort.
 *
 * <b>Ein Jahr ohne Daten hat keinen Abschluss</b> (E3): Der Weg antwortet mit 404, und die Ansicht
 * sagt das in einem Satz statt einer Seite voller Nullen. Eine Adresse, die kein Jahr ist, geht
 * gar nicht erst ans Netz — wie eine Kennung, die keine ist (`lib/kennung.ts`).
 */

/** Ueber dem Abschluss steht die Uebersicht der Jahre (E6). */
const ZU_JAHRESABSCHLUESSEN: PfadVerweis = { titel: 'Jahresabschlüsse', ziel: '/jahresabschluesse' };

/** Ein Jahr in der Adresse: vier Ziffern, wie der Server es schreibt. */
const JAHR = /^\d{4}$/u;

const LAEDT = 'Der Jahresabschluss wird geladen …';
const AUSFALL =
  'Der Jahresabschluss ist gerade nicht zu erreichen. Bitte später erneut versuchen.';

/** Der Hinweis am laufenden Jahr (#287, Kriterium 1). */
const LAEUFT_NOCH = 'läuft noch';

/** Der Satz unter den Kacheln im Wortlaut (#287, Kriterium 4). */
const ZAEHLWEISE = 'Gezählt nach Rechnungsdatum, nicht nach Zahlungseingang.';

const TITEL_NETTO = 'Einnahmen netto';
const TITEL_BRUTTO = 'Einnahmen brutto';
const TITEL_UMSATZSTEUER = 'Enthaltene Umsatzsteuer';
const TITEL_RECHNUNGEN = 'Rechnungen';
const TITEL_STEUER = 'Umsatzsteuer je Steuersatz';
const TITEL_KUNDEN = 'Umsatz je Kunde';
const TITEL_ANGEBOTE = 'Angebote';
const TITEL_ARBEITSZEIT = 'Arbeitszeit';

/** Der Satz der leeren Kundenkarte — ein Jahr nur mit Angeboten hat keinen Kunden mit Umsatz. */
const LEER_KUNDEN = 'Keine Rechnung an einen Kunden in diesem Jahr.';

/** Was an der Stelle einer nicht berechenbaren Kennzahl steht, und je Stelle ihr Grund (Kriterium 11). */
const STRICH = '—';
const OHNE_UMSATZ = 'kein Umsatz';
const OHNE_ANGEBOTE = 'keine abgegebenen Angebote';
const OHNE_KUNDENSTUNDEN = 'keine Kundenstunden';

/** Die Zeile der nachgetragenen Rechnungen, deren Satz nicht erfasst ist (Kriterium 6). */
const OHNE_SATZ = 'Steuersatz nicht erfasst';

const SPALTEN_RECHNUNGEN: readonly TafelSpalte[] = [
  'Rechnungen',
  { beschriftung: 'Anzahl', zahl: true },
  { beschriftung: 'Netto', zahl: true },
];
const SPALTEN_STEUER: readonly TafelSpalte[] = [
  'Steuersatz',
  { beschriftung: 'Netto', zahl: true },
  { beschriftung: 'Umsatzsteuer', zahl: true },
];
const SPALTEN_KUNDEN: readonly TafelSpalte[] = [
  'Firma',
  { beschriftung: 'Netto', zahl: true },
  { beschriftung: 'Anteil am Jahresumsatz', zahl: true },
];
/** Angebote und Arbeitszeit sind je eine Handvoll Kennzahlen: Wort und Wert. */
const SPALTEN_KENNZAHLEN: readonly TafelSpalte[] = ['Kennzahl', { beschriftung: 'Wert', zahl: true }];

/** Dieselbe Gestalt wie eine Betragszelle in {@link StartseitePage}. */
const ZAHLENZELLE = { whiteSpace: 'nowrap', textAlign: 'right' } as const;

/** Die Gestalt der Kopfzelle einer Zeile — wie die Summenzeile in {@link StartseitePage}. */
const ZEILENKOPF = {
  textAlign: 'left',
  padding: '12px 14px',
  fontSize: 13.5,
  fontWeight: 600,
  whiteSpace: 'nowrap',
} as const;

/** Die Symbolgroesse in den Kacheln (CLAUDE-design.md, „Bausteine": Symbolfeld 48 px). */
const SYMBOL_KACHEL = 22;

/** Die kleinste Breite einer Kachel, unter der das Raster umbricht — wie auf der Startseite. */
const KACHEL_MINDESTBREITE = 240;

/** Was die Ansicht gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly abschluss: Jahresabschluss }
  | { readonly art: 'unbekannt' }
  | { readonly art: 'fehler' };

/** Holt den Abschluss und macht auch aus dem Fehlschlag einen Stand. */
async function laden(jahr: string): Promise<Stand> {
  try {
    return { art: 'daten', abschluss: await jahresabschluss(jahr) };
  } catch (ursache: unknown) {
    return nichtGefunden(ursache) ? { art: 'unbekannt' } : { art: 'fehler' };
  }
}

/** Ein Satz in schwacher Schrift: der Ladehinweis und die Zaehlweise unter den Kacheln. */
function Beisatz({ children }: { readonly children: string }) {
  return (
    <Typography
      sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
    >
      {children}
    </Typography>
  );
}

/** Die drei Kacheln der Einnahmen; das Raster bricht um, sobald eine ihre Mindestbreite verliert. */
function Kacheln({ abschluss }: { readonly abschluss: Jahresabschluss }) {
  const { einnahmen } = abschluss;
  return (
    <Box
      sx={{
        display: 'grid',
        gap: '18px',
        gridTemplateColumns: `repeat(auto-fit, minmax(${String(KACHEL_MINDESTBREITE)}px, 1fr))`,
      }}
    >
      <Kennzahlkachel
        toenung="salbei"
        symbol={<IconReceipt size={SYMBOL_KACHEL} stroke={1.8} aria-hidden />}
        beschriftung={TITEL_NETTO}
        zahl={euro(einnahmen.nettoInCent)}
      />
      <Kennzahlkachel
        toenung="himmel"
        symbol={<IconCoinEuro size={SYMBOL_KACHEL} stroke={1.8} aria-hidden />}
        beschriftung={TITEL_BRUTTO}
        zahl={euro(einnahmen.bruttoInCent)}
      />
      <Kennzahlkachel
        toenung="flieder"
        symbol={<IconReceiptTax size={SYMBOL_KACHEL} stroke={1.8} aria-hidden />}
        beschriftung={TITEL_UMSATZSTEUER}
        zahl={euro(einnahmen.umsatzsteuerInCent)}
      />
    </Box>
  );
}

/** Eine Zeile der Rechnungen: Wort als Zeilenkopf, Anzahl, Netto (Kriterium 5). */
function Rechnungszeile({
  wort,
  anzahl,
  nettoInCent,
}: {
  readonly wort: string;
  readonly anzahl: number;
  readonly nettoInCent: number;
}) {
  return (
    <Box component="tr">
      <Box component="th" scope="row" sx={ZEILENKOPF}>
        {wort}
      </Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={ZAHLENZELLE}>
        {String(anzahl)}
      </Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={{ ...ZAHLENZELLE, fontWeight: 600 }}>
        {euro(nettoInCent)}
      </Box>
    </Box>
  );
}

/**
 * Die gestellten Rechnungen und davon die heute offenen und die abgeschriebenen (Kriterium 5).
 *
 * Das Netto der gestellten ist die Kennzahl „Einnahmen netto": Kriterium 5 zaehlt dieselbe Menge
 * wie Kriterium 4. Es steht in der Zeile, damit die Spalte ohne Luecke bleibt.
 */
function Rechnungen({ abschluss }: { readonly abschluss: Jahresabschluss }) {
  const stand = abschluss.rechnungsstand;
  return (
    <Karte titel={TITEL_RECHNUNGEN}>
      <Tafel beschriftung={TITEL_RECHNUNGEN} spalten={SPALTEN_RECHNUNGEN}>
        <Rechnungszeile
          wort="Gestellt"
          anzahl={stand.anzahl}
          nettoInCent={abschluss.einnahmen.nettoInCent}
        />
        <Rechnungszeile
          wort="davon heute offen"
          anzahl={stand.offenAnzahl}
          nettoInCent={stand.offenNettoInCent}
        />
        <Rechnungszeile
          wort="davon abgeschrieben"
          anzahl={stand.abgeschriebenAnzahl}
          nettoInCent={stand.abgeschriebenNettoInCent}
        />
      </Tafel>
    </Karte>
  );
}

/** Eine Zeile der Steuertafel; ohne Satz steht das Wort der Nachtragszeile (Kriterium 6). */
function Steuerzeile({ zeile }: { readonly zeile: Steuerzeile }) {
  const satz = zeile.satzInHundertstelProzent;
  return (
    <Box component="tr">
      <Box
        component="td"
        className={satz === null ? undefined : ZAHLEN_KLASSE}
        sx={{ fontWeight: 600, whiteSpace: 'nowrap' }}
      >
        {satz === null ? OHNE_SATZ : prozentWort(satz)}
      </Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={{ ...ZAHLENZELLE, fontWeight: 600 }}>
        {euro(zeile.nettoInCent)}
      </Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={ZAHLENZELLE}>
        {euro(zeile.umsatzsteuerInCent)}
      </Box>
    </Box>
  );
}

/** Die Umsatzsteuer je Satz — nur, wo die Antwort Zeilen schickt (E14, Kriterium 6). */
function Steuersaetze({ zeilen }: { readonly zeilen: readonly Steuerzeile[] }) {
  if (zeilen.length === 0) {
    return null;
  }
  return (
    <Karte titel={TITEL_STEUER}>
      <Tafel beschriftung={TITEL_STEUER} spalten={SPALTEN_STEUER}>
        {zeilen.map((zeile) => (
          <Steuerzeile key={zeile.satzInHundertstelProzent ?? OHNE_SATZ} zeile={zeile} />
        ))}
      </Tafel>
    </Karte>
  );
}

/** Ein beigestelltes Wort in schwacher Schrift: der Grund am Strich — wie in der Uebersicht. */
function Beiwort({ children }: { readonly children: string }) {
  return (
    <Box
      component="span"
      sx={(theme) => ({
        marginLeft: '8px',
        fontSize: 12.5,
        fontWeight: 400,
        color: theme.vars.palette.kupferwolke.textSchwach,
      })}
    >
      {children}
    </Box>
  );
}

/** Ein Wert oder, wo die Antwort keinen hat, der Strich mit dem Grund der Stelle (Kriterium 11). */
function WertOderStrich({
  wert,
  setzen,
  grund,
}: {
  readonly wert: number | null;
  readonly setzen: (wert: number) => string;
  readonly grund: string;
}) {
  if (wert === null) {
    return (
      <>
        {STRICH}
        <Beiwort>{grund}</Beiwort>
      </>
    );
  }
  return <>{setzen(wert)}</>;
}

/** Eine Zeile eines Kunden: Firma, Netto, Anteil (Kriterium 9). */
function Kundenzeile({ zeile }: { readonly zeile: Kundenzeile }) {
  return (
    <Box component="tr">
      <Box component="td" sx={{ fontWeight: 600, whiteSpace: 'nowrap' }}>
        {zeile.firmaName}
      </Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={{ ...ZAHLENZELLE, fontWeight: 600 }}>
        {euro(zeile.nettoInCent)}
      </Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={ZAHLENZELLE}>
        <WertOderStrich
          wert={zeile.anteilInHundertstelProzent}
          setzen={prozentWort}
          grund={OHNE_UMSATZ}
        />
      </Box>
    </Box>
  );
}

/** Die Kunden des Jahres in der Reihenfolge der Antwort; ohne Kunden ein Satz statt der Tafel. */
function Kunden({ zeilen }: { readonly zeilen: readonly Kundenzeile[] }) {
  return (
    <Karte titel={TITEL_KUNDEN}>
      {zeilen.length === 0 ? (
        <Typography
          role="status"
          sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textMatt })}
        >
          {LEER_KUNDEN}
        </Typography>
      ) : (
        <Tafel beschriftung={TITEL_KUNDEN} spalten={SPALTEN_KUNDEN}>
          {zeilen.map((zeile, stelle) => (
            // Zwei Firmen koennen gleich heissen; erst die Stelle macht den Schluessel eindeutig.
            <Kundenzeile key={`${String(stelle)}-${zeile.firmaName}`} zeile={zeile} />
          ))}
        </Tafel>
      )}
    </Karte>
  );
}

/** Eine Zeile aus Wort und Wert — fuer Angebote und Arbeitszeit. */
function Kennzahlzeile({ wort, children }: { readonly wort: string; readonly children: ReactNode }) {
  return (
    <Box component="tr">
      <Box component="th" scope="row" sx={ZEILENKOPF}>
        {wort}
      </Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={ZAHLENZELLE}>
        {children}
      </Box>
    </Box>
  );
}

/** Die Angebote des Jahres: Stueckzahlen, Quote und Volumen (Kriterien 7 und 8). */
function Angebote({ bilanz }: { readonly bilanz: Angebotsbilanz }) {
  return (
    <Karte titel={TITEL_ANGEBOTE}>
      <Tafel beschriftung={TITEL_ANGEBOTE} spalten={SPALTEN_KENNZAHLEN}>
        <Kennzahlzeile wort="Abgegeben">{String(bilanz.abgegeben)}</Kennzahlzeile>
        <Kennzahlzeile wort="Angenommen">{String(bilanz.angenommen)}</Kennzahlzeile>
        <Kennzahlzeile wort="Heute noch offen">{String(bilanz.offen)}</Kennzahlzeile>
        <Kennzahlzeile wort="Annahmequote">
          <WertOderStrich
            wert={bilanz.annahmequoteInHundertstelProzent}
            setzen={prozentWort}
            grund={OHNE_ANGEBOTE}
          />
        </Kennzahlzeile>
        <Kennzahlzeile wort="Volumen netto abgegeben">
          {euro(bilanz.volumenAbgegebenInCent)}
        </Kennzahlzeile>
        <Kennzahlzeile wort="Volumen netto angenommen">
          {euro(bilanz.volumenAngenommenInCent)}
        </Kennzahlzeile>
      </Tafel>
    </Karte>
  );
}

/** Die Arbeitszeit des Jahres und der Erloes je Kundenstunde (Kriterium 10). */
function Arbeitszeit({ arbeitszeit }: { readonly arbeitszeit: Jahresarbeitszeit }) {
  return (
    <Karte titel={TITEL_ARBEITSZEIT}>
      <Tafel beschriftung={TITEL_ARBEITSZEIT} spalten={SPALTEN_KENNZAHLEN}>
        <Kennzahlzeile wort="Kundenarbeit">
          {stundenWort(arbeitszeit.kundenStundenInHundertsteln)}
        </Kennzahlzeile>
        <Kennzahlzeile wort="Interne Projekte">
          {stundenWort(arbeitszeit.interneStundenInHundertsteln)}
        </Kennzahlzeile>
        <Kennzahlzeile wort="Erlös je Stunde">
          <WertOderStrich
            wert={arbeitszeit.erloesJeStundeInCent}
            setzen={euro}
            grund={OHNE_KUNDENSTUNDEN}
          />
        </Kennzahlzeile>
      </Tafel>
    </Karte>
  );
}

/** Was in der Kopfkarte steht: Ladehinweis, Meldung oder die Kacheln mit ihrer Zaehlweise. */
function kopfinhaltZu(stand: Stand, jahr: string): ReactNode {
  if (stand.art === 'laedt') {
    return <Beisatz>{LAEDT}</Beisatz>;
  }
  if (stand.art === 'fehler') {
    return <Alert severity="error">{AUSFALL}</Alert>;
  }
  if (stand.art === 'unbekannt') {
    return (
      <Typography
        role="status"
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textMatt })}
      >
        {`Für ${jahr} gibt es keinen Jahresabschluss.`}
      </Typography>
    );
  }
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
      <Kacheln abschluss={stand.abschluss} />
      <Beisatz>{ZAEHLWEISE}</Beisatz>
    </Box>
  );
}

export default function JahresabschlussPage() {
  // Unter seiner Route ist der Parameter immer gesetzt; `String` macht daraus den Typ, ohne einen
  // Zweig, den keine Adresse erreicht. Was kein Jahr ist, faengt `JAHR` unten ab.
  const jahr = String(useParams().jahr);
  const titel = `Jahresabschluss ${jahr}`;
  useKopfPfad([ZU_JAHRESABSCHLUESSEN], titel);
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });

  useEffect(() => {
    if (!JAHR.test(jahr)) {
      setzeStand({ art: 'unbekannt' });
      return;
    }
    let gueltig = true;
    setzeStand({ art: 'laedt' });
    void laden(jahr).then((neu) => {
      // Ein juengeres Jahr hat diesen Lauf abgeloest; seine Antwort ist die richtige.
      if (gueltig) {
        setzeStand(neu);
      }
    });
    return () => {
      gueltig = false;
    };
  }, [jahr]);

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '22px' }}>
      <Karte
        titel={titel}
        titelEbene={1}
        notiz={stand.art === 'daten' && stand.abschluss.laeuftNoch ? LAEUFT_NOCH : undefined}
      >
        {kopfinhaltZu(stand, jahr)}
      </Karte>
      {stand.art === 'daten' ? (
        <>
          <Rechnungen abschluss={stand.abschluss} />
          <Steuersaetze zeilen={stand.abschluss.steuerzeilen} />
          <Kunden zeilen={stand.abschluss.kunden} />
          <Angebote bilanz={stand.abschluss.angebotsbilanz} />
          <Arbeitszeit arbeitszeit={stand.abschluss.arbeitszeit} />
        </>
      ) : null}
    </Box>
  );
}
