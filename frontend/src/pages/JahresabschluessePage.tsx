import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Link as RouterLink } from 'react-router-dom';

import { jahresuebersicht } from '../api/jahresabschluesse';
import type { Jahresuebersichtszeile } from '../api/jahresabschluesse';
import Karte from '../components/Karte';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import Tafel, { type TafelSpalte } from '../components/Tafel';
import { euro } from '../lib/geld';
import { prozentWort } from '../lib/prozent';
import { ZAHLEN_KLASSE } from '../theme';

/**
 * Die Uebersicht der Jahresabschluesse (#287, Kriterien 1 bis 3; Plan #288, E15–E17, E21).
 *
 * Aufbau wie die Liste der Rechnungen: eine {@link Karte} mit einer {@link Tafel} darunter, kein
 * eigener Baustein (E17). Je Jahr stehen die drei Hauptzahlen — Einnahmen netto, Zahl der
 * Rechnungen, Annahmequote. Sortiert hat der Server, das juengste Jahr zuerst (E21); die Zeilen
 * stehen in der Reihenfolge der Antwort. Gerechnet hat er auch: Hier wird nur gesetzt.
 *
 * <b>Der Weg liegt auf dem Jahr</b> und fuehrt auf `/jahresabschluesse/<jahr>` (E16) — ein Link je
 * Zeile, wie die Nummer in {@link RechnungenPage}. Das laufende Jahr traegt daneben den Hinweis
 * „läuft noch", ausserhalb des Links: Der Name des Wegs bleibt das Jahr.
 *
 * <b>Eine nicht berechenbare Annahmequote</b> steht als Strich mit ihrem Grund (E11, Kriterium 11).
 * Der Grund steht als Wort neben dem Strich und nicht nur im Tooltip — so ist er ohne Maus und
 * vorgelesen zu erfahren. Eine Quote 0 ist dagegen eine Zahl und steht als „0,0 %".
 */

/** Die Uebersicht ist die erste Stufe des Pfades — ueber ihr steht nichts (E6). */
const KEIN_WEG: readonly PfadVerweis[] = [];

const TITEL = 'Jahresabschlüsse';
const LAEDT = 'Jahresabschlüsse werden geladen …';
const AUSFALL =
  'Die Jahresabschlüsse sind gerade nicht zu erreichen. Bitte später erneut versuchen.';
/** Der Satz des leeren Zustands im Wortlaut der Anforderung (#287, Kriterium 2). */
const LEER =
  'Hier erscheint ein Jahr, sobald es eine gestellte Rechnung oder ein abgegebenes Angebot hat.';

/** Der Hinweis am laufenden Jahr (#287, Kriterium 1). */
const LAEUFT_NOCH = 'läuft noch';
/** Was an der Stelle einer nicht berechenbaren Kennzahl steht (Kriterium 11). */
const STRICH = '—';
const OHNE_ANGEBOTE = 'keine abgegebenen Angebote';

const SPALTEN: readonly TafelSpalte[] = [
  'Jahr',
  { beschriftung: 'Einnahmen netto', zahl: true },
  { beschriftung: 'Rechnungen', zahl: true },
  { beschriftung: 'Annahmequote', zahl: true },
];

/** Dieselbe Gestalt wie eine Betragszelle in {@link StartseitePage}. */
const ZAHLENZELLE = { whiteSpace: 'nowrap', textAlign: 'right' } as const;

/** Was die Ansicht gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly jahre: readonly Jahresuebersichtszeile[] }
  | { readonly art: 'fehler' };

/** Holt die Uebersicht und macht auch aus dem Fehlschlag einen Stand. */
async function laden(): Promise<Stand> {
  try {
    return { art: 'daten', jahre: await jahresuebersicht() };
  } catch {
    return { art: 'fehler' };
  }
}

/** Ein beigestelltes Wort in schwacher Schrift: der Hinweis am Jahr, der Grund am Strich. */
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

/** Eine Zeile der Tafel; die Jahresspalte traegt den Weg zum Abschluss. */
function Zeile({ zeile }: { readonly zeile: Jahresuebersichtszeile }) {
  const quote = zeile.annahmequoteInHundertstelProzent;
  return (
    <Box component="tr">
      <Box component="td" sx={{ fontWeight: 500, whiteSpace: 'nowrap' }}>
        <Box
          component={RouterLink}
          to={`/jahresabschluesse/${zeile.jahr}`}
          className={ZAHLEN_KLASSE}
          sx={(theme) => ({
            color: 'inherit',
            textDecoration: 'none',
            '&:hover': { color: theme.vars.palette.kupferwolke.kupfer },
          })}
        >
          {zeile.jahr}
        </Box>
        {zeile.laeuftNoch ? <Beiwort>{LAEUFT_NOCH}</Beiwort> : null}
      </Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={{ ...ZAHLENZELLE, fontWeight: 600 }}>
        {euro(zeile.nettoInCent)}
      </Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={ZAHLENZELLE}>
        {zeile.anzahl}
      </Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={ZAHLENZELLE}>
        {quote === null ? (
          <>
            {STRICH}
            <Beiwort>{OHNE_ANGEBOTE}</Beiwort>
          </>
        ) : (
          prozentWort(quote)
        )}
      </Box>
    </Box>
  );
}

/** Was in der Karte steht: Ladehinweis, Meldung, Satz zur Leere oder die Tafel. */
function inhaltZu(stand: Stand): ReactNode {
  if (stand.art === 'laedt') {
    return (
      <Typography
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
      >
        {LAEDT}
      </Typography>
    );
  }
  if (stand.art === 'fehler') {
    return <Alert severity="error">{AUSFALL}</Alert>;
  }
  if (stand.jahre.length === 0) {
    return (
      <Typography
        role="status"
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textMatt })}
      >
        {LEER}
      </Typography>
    );
  }
  return (
    <Tafel beschriftung={TITEL} spalten={SPALTEN}>
      {stand.jahre.map((zeile) => (
        <Zeile key={zeile.jahr} zeile={zeile} />
      ))}
    </Tafel>
  );
}

export default function JahresabschluessePage() {
  useKopfPfad(KEIN_WEG, TITEL);
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });

  useEffect(() => {
    let gueltig = true;
    void laden().then((neu) => {
      if (gueltig) {
        setzeStand(neu);
      }
    });
    return () => {
      gueltig = false;
    };
  }, []);

  return (
    <Karte titel={TITEL} titelEbene={1}>
      {inhaltZu(stand)}
    </Karte>
  );
}
