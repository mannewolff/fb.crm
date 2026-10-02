import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';

import { angeboteUebersicht } from '../api/angebote';
import type { AngeboteUebersicht, AngebotUebersichtZeile } from '../api/angebote';
import AngebotsstatusChip from '../components/AngebotsstatusChip';
import InternChip from '../components/InternChip';
import Karte from '../components/Karte';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import Tafel from '../components/Tafel';
import { ANGEBOTSSTATUS, alsAngebotsstatus, angebotsstatusBild } from '../lib/angebotsstatus';
import type { Angebotsstatus } from '../lib/angebotsstatus';
import { euro } from '../lib/geld';
import { tagWort } from '../lib/tag';
import { RADIUS_RUND, ZAHLEN_KLASSE } from '../theme';

/**
 * Die Uebersicht aller Angebote, nach Status filterbar (Issue #127, Kriterium 8).
 *
 * Aufbau wie die Firmenuebersicht: eine {@link Karte} mit dem Filter im Kopf und einer
 * {@link Tafel} darunter. Eine Kupfertaste gibt es hier nicht — ein Angebot entsteht an seiner
 * Firma (Issue #126), und eine Taste ohne Firma fuehrte in eine Wahl, die die Firmenseite schon ist.
 *
 * <ul>
 *   <li><b>Der Filter steht in der Adresse</b> (`?status=BESTELLT`): teilbar und beim Neuladen
 *       erhalten. Ein unbekannter Wert dort gilt als „alle", statt die Seite scheitern zu lassen.</li>
 *   <li><b>Sortiert und gefiltert wird am Server</b> — die Zeilen stehen in der Reihenfolge der
 *       Antwort, neueste zuerst.</li>
 *   <li><b>Das Kennzeichen „Intern" steht hinter dem Datum</b> (Issue #233), in der Spalte, die
 *       das Angebot benennt — keine eigene Spalte, die bei 768 px der Summe den Platz nimmt. In
 *       „Summe" steht dann ein Strich, und der haengt am Kennzeichen und nicht an einer 0 vom
 *       Server (Plan #218, E16): Menge und Preis bleiben beim Wechsel extern -> intern erhalten,
 *       eine 0 behauptete, es gaebe sie nicht.</li>
 *   <li><b>Veraltete Antworten fallen weg</b>: Wer schnell zwischen zwei Filtern wechselt, sieht
 *       die Antwort des letzten, auch wenn die des vorigen spaeter eintrifft.</li>
 * </ul>
 */

/** Die Uebersicht ist die erste Stufe des Pfades — ueber ihr steht nichts (E6). */
const KEIN_WEG: readonly PfadVerweis[] = [];

const PARAM_STATUS = 'status';
const ALLE = '';

const AUSFALL = 'Die Angebote sind gerade nicht zu erreichen. Bitte später erneut versuchen.';
const LEER_ALLE = 'Es gibt noch kein Angebot. Angebote entstehen auf der Seite ihrer Firma.';
const LEER_GEFILTERT = 'In diesem Status gibt es kein Angebot.';

const SPALTEN: readonly string[] = ['Datum', 'Firma', 'Status', 'Summe'];

/** Was in „Summe" steht, wenn es keinen Betrag gibt (Halbgeviertstrich). */
const OHNE_BETRAG = '\u2013';

/** Was die Ansicht gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly uebersicht: AngeboteUebersicht }
  | { readonly art: 'fehler' };

/** Holt die Uebersicht und macht auch aus dem Fehlschlag einen Stand. */
async function laden(status: Angebotsstatus | null): Promise<Stand> {
  try {
    return { art: 'daten', uebersicht: await angeboteUebersicht(status) };
  } catch {
    return { art: 'fehler' };
  }
}

/** Eine Zeile der Tafel; das Datum traegt den Weg zum Angebot, der Firmenname den zur Firma. */
function Zeile({ angebot }: { readonly angebot: AngebotUebersichtZeile }) {
  const wegStil = {
    color: 'inherit',
    textDecoration: 'none',
  } as const;
  return (
    <Box component="tr">
      <Box
        component="td"
        sx={{
          fontWeight: 500,
          whiteSpace: 'nowrap',
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
        }}
      >
        <Box
          component={RouterLink}
          to={`/angebote/${String(angebot.id)}`}
          className={ZAHLEN_KLASSE}
          sx={(theme) => ({
            ...wegStil,
            '&:hover': { color: theme.vars.palette.kupferwolke.kupfer },
          })}
        >
          {tagWort(angebot.angebotDatum)}
        </Box>
        {angebot.intern ? <InternChip /> : null}
      </Box>
      <Box component="td">
        <Box
          component={RouterLink}
          to={`/firmen/${String(angebot.firmaId)}`}
          sx={(theme) => ({
            ...wegStil,
            '&:hover': { color: theme.vars.palette.kupferwolke.kupfer },
          })}
        >
          {angebot.firmaName}
        </Box>
      </Box>
      <Box component="td">
        <AngebotsstatusChip status={angebot.status} />
      </Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ fontWeight: 600, whiteSpace: 'nowrap', textAlign: 'right' }}
      >
        {angebot.intern ? OHNE_BETRAG : euro(angebot.summeInCent)}
      </Box>
    </Box>
  );
}

/** Was in der Karte steht: Ladehinweis, Meldung, Satz zur Leere oder die Tafel. */
function inhaltZu(stand: Stand, status: Angebotsstatus | null): ReactNode {
  if (stand.art === 'laedt') {
    return (
      <Typography
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
      >
        Angebote werden geladen …
      </Typography>
    );
  }
  if (stand.art === 'fehler') {
    return <Alert severity="error">{AUSFALL}</Alert>;
  }
  if (stand.uebersicht.angebote.length === 0) {
    return (
      <Typography
        role="status"
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textMatt })}
      >
        {status === null ? LEER_ALLE : LEER_GEFILTERT}
      </Typography>
    );
  }
  return (
    <Tafel beschriftung="Angebote" spalten={SPALTEN}>
      {stand.uebersicht.angebote.map((angebot) => (
        <Zeile key={angebot.id} angebot={angebot} />
      ))}
    </Tafel>
  );
}

export default function AngebotePage() {
  useKopfPfad(KEIN_WEG, 'Angebote');
  const [parameter, setzeParameter] = useSearchParams();
  const status = alsAngebotsstatus(parameter.get(PARAM_STATUS));
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });

  useEffect(() => {
    let gueltig = true;
    setzeStand({ art: 'laedt' });
    void laden(status).then((neu) => {
      // Ein juengerer Filter hat diesen Lauf abgeloest; seine Antwort ist die richtige.
      if (gueltig) {
        setzeStand(neu);
      }
    });
    return () => {
      gueltig = false;
    };
  }, [status]);

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '22px' }}>
      <Karte
        titel="Angebote"
        titelEbene={1}
        werkzeug={
          <TextField
            select
            size="small"
            label="Status"
            value={status ?? ALLE}
            onChange={(ereignis) => {
              const neu = ereignis.target.value;
              // Geschoben statt ersetzt: Der Filter ist eine Handlung, die „zurueck"
              // zuruecknehmen koennen soll.
              setzeParameter(neu === ALLE ? {} : { [PARAM_STATUS]: neu });
            }}
            slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
            sx={(theme) => ({
              minWidth: 180,
              '& .MuiOutlinedInput-root': {
                fontSize: 12.5,
                borderRadius: `${RADIUS_RUND}px`,
                background: theme.vars.palette.kupferwolke.flaecheWeich,
              },
            })}
          >
            <option value={ALLE}>alle</option>
            {ANGEBOTSSTATUS.map((eintrag) => (
              <option key={eintrag} value={eintrag}>
                {angebotsstatusBild(eintrag).wort}
              </option>
            ))}
          </TextField>
        }
      >
        {inhaltZu(stand, status)}
      </Karte>
    </Box>
  );
}
