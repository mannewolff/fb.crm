import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { Link as RouterLink } from 'react-router-dom';

import type { AngebotZeile } from '../api/angebote';
import { angebotsstandBild } from '../lib/angebotsstand';
import { euro } from '../lib/geld';
import { tagWort } from '../lib/tag';
import { ZAHLEN_KLASSE } from '../theme';
import Tafel from './Tafel';
import ZustandsChip from './ZustandsChip';

/**
 * Die Angebote einer Firma als Tafel (Kriterium 20).
 *
 * <b>Nicht sortiert.</b> Die Reihenfolge kommt vom Server — Entwuerfe zuerst, innerhalb jeder
 * Gruppe nach dem Zeitpunkt der Anlage (E25). Eine zweite Sortierung hier waere eine zweite
 * Wahrheit und liefe beim naechsten Zustand auseinander.
 *
 * <b>Der Weg liegt auf der Nummer, nicht auf der Zeile.</b> Eine ganze Tabellenzeile klickbar zu
 * machen hiesse, ein `tr` mit `onClick` zu versehen — kein Ziel des Tabulators und fuer den
 * Screenreader kein Weg. Die Nummer ist der Name des Angebots und damit der richtige Trager.
 *
 * Ein Entwurf hat keine Nummer (Kriterium 11); dann steht dort das Wort „Entwurf" — sichtbar und
 * als Name des Weges, nicht ein Gedankenstrich, den niemand anklicken kann.
 */

const SPALTEN = ['Nummer', 'Datum', 'Gültig bis', 'Zustand', 'Summe'] as const;
const LEER = 'Noch kein Angebot an diese Firma.';
const SYMBOL_CHIP = 13;

function Zeile({ angebot }: { readonly angebot: AngebotZeile }) {
  const bild = angebotsstandBild(angebot.stand);
  const Symbol = bild.symbol;
  return (
    <Box component="tr">
      <Box component="td" sx={{ fontWeight: 500 }}>
        <Box
          component={RouterLink}
          to={`/angebote/${String(angebot.id)}`}
          className={angebot.nummer === null ? undefined : ZAHLEN_KLASSE}
          sx={(theme) => ({
            color: 'inherit',
            textDecoration: 'none',
            '&:hover': { color: theme.vars.palette.kupferwolke.kupfer },
          })}
        >
          {angebot.nummer ?? 'Entwurf'}
        </Box>
      </Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={{ whiteSpace: 'nowrap' }}>
        {tagWort(angebot.angebotDatum)}
      </Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={(theme) => ({
          whiteSpace: 'nowrap',
          color: theme.vars.palette.kupferwolke.textMatt,
        })}
      >
        {tagWort(angebot.gueltigBis)}
      </Box>
      <Box component="td">
        <ZustandsChip
          wort={bild.wort}
          toenung={bild.toenung}
          symbol={<Symbol size={SYMBOL_CHIP} stroke={1.8} />}
        />
      </Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ fontWeight: 600, whiteSpace: 'nowrap', textAlign: 'right' }}
      >
        {euro(angebot.summeInCent)}
      </Box>
    </Box>
  );
}

export interface AngebotslisteProps {
  readonly angebote: readonly AngebotZeile[];
}

export default function Angebotsliste({ angebote }: AngebotslisteProps) {
  if (angebote.length === 0) {
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
    <Tafel beschriftung="Angebote" spalten={[...SPALTEN]}>
      {angebote.map((angebot) => (
        <Zeile key={angebot.id} angebot={angebot} />
      ))}
    </Tafel>
  );
}
