import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { Link as RouterLink } from 'react-router-dom';

import type { AuftragZeile } from '../api/auftraege';
import { auftragsstatusBild } from '../lib/auftragsstatus';
import { euro } from '../lib/geld';
import { tagWort, zeitraumWort } from '../lib/tag';
import { ZAHLEN_KLASSE } from '../theme';
import Tafel from './Tafel';
import ZustandsChip from './ZustandsChip';

/**
 * Die Auftraege eines Vorgangs als Tafel (Kriterium 9).
 *
 * <b>Nicht sortiert.</b> Die Reihenfolge kommt vom Server — Auftragsdatum absteigend, bei gleichem
 * Tag die hoehere Kennung zuerst (Plan E13). Eine zweite Sortierung hier waere eine zweite Wahrheit.
 *
 * <b>Der Weg liegt auf der Nummer, nicht auf der Zeile</b> — dasselbe Muster wie
 * {@link Angebotsliste}: Eine klickbare Tabellenzeile ist fuer die Tastatur kein Ziel und fuer den
 * Screenreader kein Weg.
 *
 * Der Status kommt aus {@link auftragsstatusBild} und steht als Wort; die Toenung stuetzt ihn nur.
 */

const SPALTEN = ['Nummer', 'Datum', 'Status', 'Leistungszeitraum', 'Summe'] as const;
const LEER = 'Noch kein Auftrag zu diesem Vorgang.';
const SYMBOL_CHIP = 13;

function Zeile({ vorgangId, auftrag }: { readonly vorgangId: number; readonly auftrag: AuftragZeile }) {
  const bild = auftragsstatusBild(auftrag.status);
  const Symbol = bild.symbol;
  return (
    <Box component="tr">
      <Box component="td" sx={{ fontWeight: 500 }}>
        <Box
          component={RouterLink}
          to={`/vorgaenge/${String(vorgangId)}/auftraege/${String(auftrag.id)}`}
          className={ZAHLEN_KLASSE}
          sx={(theme) => ({
            color: 'inherit',
            textDecoration: 'none',
            '&:hover': { color: theme.vars.palette.kupferwolke.kupfer },
          })}
        >
          {auftrag.nummer}
        </Box>
      </Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={{ whiteSpace: 'nowrap' }}>
        {tagWort(auftrag.auftragDatum)}
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
        sx={(theme) => ({ whiteSpace: 'nowrap', color: theme.vars.palette.kupferwolke.textMatt })}
      >
        {zeitraumWort(auftrag.leistungAb, auftrag.leistungBis)}
      </Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ fontWeight: 600, whiteSpace: 'nowrap', textAlign: 'right' }}
      >
        {euro(auftrag.summeInCent)}
      </Box>
    </Box>
  );
}

export interface AuftragslisteProps {
  readonly vorgangId: number;
  readonly auftraege: readonly AuftragZeile[];
}

export default function Auftragsliste({ vorgangId, auftraege }: AuftragslisteProps) {
  if (auftraege.length === 0) {
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
    <Tafel beschriftung="Aufträge" spalten={[...SPALTEN]}>
      {auftraege.map((auftrag) => (
        <Zeile key={auftrag.id} vorgangId={vorgangId} auftrag={auftrag} />
      ))}
    </Tafel>
  );
}
