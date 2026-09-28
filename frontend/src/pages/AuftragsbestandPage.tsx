import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { IconCoinEuro, IconFileInvoice } from '@tabler/icons-react';
import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Link as RouterLink } from 'react-router-dom';

import { auftragsbestand } from '../api/auftragsbestand';
import type { Auftragsbestand, AuftragsbestandZeile } from '../api/auftragsbestand';
import Karte from '../components/Karte';
import Kennzahl from '../components/Kennzahl';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import Tafel from '../components/Tafel';
import ZustandsChip from '../components/ZustandsChip';
import { auftragsstatusBild } from '../lib/auftragsstatus';
import { euro } from '../lib/geld';
import { ZAHLEN_KLASSE } from '../theme';

/**
 * Die Auswertung „Auftragsbestand" (Kriterien 12, 13, 14; Plan #112, E12, E13, E19).
 *
 * Oben zwei {@link Kennzahl}-Kacheln im Raster der Pipeline, darunter die nicht abgeschlossenen
 * Auftraege offener Vorgaenge als {@link Tafel}. **Zwei Kacheln** (Entscheidung Manne, 2026-09-28):
 * „Beauftragt" auf Flieder mit `file-invoice`, „Noch offen" auf Salbei mit `coin-euro` — beide Paare
 * stehen in der Vorlage (`.zahlen` Z. 153–154), es entsteht kein neuer Ton. Bis zur Rechnung zeigen
 * beide denselben Betrag; danach zeigt ihr Abstand, was abgerechnet ist.
 *
 * **Beide Summen kommen aus der Antwort**, nicht aus den Zeilen: Die Rundungsregel steht im Backend.
 * Der leere Bestand zeigt darum zwei Nullsummen und einen Hinweis statt einer leeren Tafel.
 *
 * **Der Weg liegt auf der Auftragsnummer**, nicht auf der Zeile — dasselbe Muster wie in der
 * {@link Auftragsliste}; er fuehrt auf den Auftrag unter seinem Vorgang (Kriterium 14).
 */

/** Die Auswertung ist die erste Stufe des Pfades — ueber ihr steht nichts. */
const KEIN_WEG: readonly PfadVerweis[] = [];

const AUSFALL = 'Der Auftragsbestand ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const LAEDT = 'Der Auftragsbestand wird geladen …';
const LEER =
  'Kein Auftrag liegt vor Ihnen. Beauftragte, noch nicht abgeschlossene Aufträge erscheinen hier.';
const SYMBOL_CHIP = 13;

const SPALTEN = [
  'Vorgang',
  'Firma',
  'Auftrag',
  'Status',
  'Auftragssumme',
  'Abgerechnet',
  'Offener Rest',
] as const;

/** Was die Ansicht gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly auswertung: Auftragsbestand }
  | { readonly art: 'fehler' };

/** Holt die Auswertung und macht auch aus dem Fehlschlag einen Stand. */
async function laden(signal: AbortSignal): Promise<Stand> {
  try {
    return { art: 'daten', auswertung: await auftragsbestand(signal) };
  } catch {
    // Jeder Grund fuehrt zur selben Meldung; welcher es war, hilft dem Benutzer nicht.
    return { art: 'fehler' };
  }
}

/** Eine Zeile der Tafel; der Weg haengt an der Auftragsnummer. */
function Zeile({ zeile }: { readonly zeile: AuftragsbestandZeile }) {
  const bild = auftragsstatusBild(zeile.status);
  const Symbol = bild.symbol;
  const betrag = { whiteSpace: 'nowrap', textAlign: 'right' } as const;
  return (
    <Box component="tr">
      <Box component="td" sx={{ fontWeight: 500 }}>
        {`#${String(zeile.vorgangNummer)} ${zeile.vorgangTitel}`}
      </Box>
      <Box component="td">{zeile.firma}</Box>
      <Box component="td">
        <Box
          component={RouterLink}
          to={`/vorgaenge/${String(zeile.vorgangId)}/auftraege/${String(zeile.auftragId)}`}
          className={ZAHLEN_KLASSE}
          sx={(theme) => ({
            color: 'inherit',
            fontWeight: 500,
            textDecoration: 'none',
            '&:hover': { color: theme.vars.palette.kupferwolke.kupfer },
          })}
        >
          {zeile.nummer}
        </Box>
      </Box>
      <Box component="td">
        <ZustandsChip
          wort={bild.wort}
          toenung={bild.toenung}
          symbol={<Symbol size={SYMBOL_CHIP} stroke={1.8} />}
        />
      </Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={betrag}>
        {euro(zeile.auftragssummeInCent)}
      </Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={(theme) => ({ ...betrag, color: theme.vars.palette.kupferwolke.textMatt })}
      >
        {euro(zeile.abgerechnetInCent)}
      </Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={{ ...betrag, fontWeight: 600 }}>
        {euro(zeile.offenerRestInCent)}
      </Box>
    </Box>
  );
}

/** Die beiden Kacheln ueber der Tafel (Vorlage `.zahlen` Z. 151–155, `.zahl-karte` Z. 71–74). */
function Kacheln({ auswertung }: { readonly auswertung: Auftragsbestand }) {
  return (
    <Box
      sx={{
        display: 'grid',
        // Zwei Kacheln nebeneinander wie in der Pipeline, auf schmalem Raum untereinander.
        gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)' },
        gap: '18px',
      }}
    >
      <Kennzahl
        beschriftung="Beauftragt"
        zahl={euro(auswertung.beauftragtInCent)}
        toenung="flieder"
        symbol={<IconFileInvoice size={22} stroke={1.7} />}
      />
      <Kennzahl
        beschriftung="Noch offen"
        zahl={euro(auswertung.nochOffenInCent)}
        toenung="salbei"
        symbol={<IconCoinEuro size={22} stroke={1.7} />}
      />
    </Box>
  );
}

/** Was in der Karte steht: Ladehinweis, Meldung, Hinweis zur Leere oder die Tafel. */
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
  if (stand.auswertung.zeilen.length === 0) {
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
    <Tafel beschriftung="Auftragsbestand" spalten={[...SPALTEN]}>
      {stand.auswertung.zeilen.map((zeile) => (
        <Zeile key={zeile.auftragId} zeile={zeile} />
      ))}
    </Tafel>
  );
}

export default function AuftragsbestandPage() {
  useKopfPfad(KEIN_WEG, 'Auftragsbestand');
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });

  useEffect(() => {
    const steuerung = new AbortController();
    void laden(steuerung.signal).then((neu) => {
      // Abgebrochen heisst: Die Ansicht ist gegangen. Ihr Stand interessiert dann niemanden mehr.
      if (!steuerung.signal.aborted) {
        setzeStand(neu);
      }
    });
    return () => {
      steuerung.abort();
    };
  }, []);

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '22px' }}>
      {stand.art === 'daten' ? <Kacheln auswertung={stand.auswertung} /> : null}
      <Karte titel="Auftragsbestand" titelEbene={1}>
        {inhaltZu(stand)}
      </Karte>
    </Box>
  );
}
