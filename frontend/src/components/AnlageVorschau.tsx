import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Typography from '@mui/material/Typography';
import type { Theme } from '@mui/material/styles';
import { IconDownload, IconX } from '@tabler/icons-react';
import { useEffect, useId, useState } from 'react';
import type { ReactNode } from 'react';

import { anlageInhalt, anlageInhaltPfad } from '../api/anlagen';
import type { Anlage, Vorschauart } from '../api/anlagen';
import { RADIUS_RUND } from '../theme';
import TastenSymbol from './TastenSymbol';
import { weichSx } from './WeicheTaste';

/**
 * Das Vorschaufenster einer Anlage: ein Bild oder ein PDF ueber der Angebotsansicht
 * (Issue #148, Kriterium 15; Plan #150, E6, E7, E11).
 *
 * <b>Der Inhaltsweg wird nicht eingebettet.</b> Er antwortet mit `Content-Disposition: attachment`,
 * `Content-Security-Policy: sandbox` und `X-Frame-Options: DENY` — ein Rahmen darauf zeigte nichts,
 * und die Kopfzeilen fuer diesen einen Weg zu lockern waere der falsche Preis (E6). Statt dessen
 * holt das Fenster die Bytes per `fetch` und zeigt sie aus einer Objekt-URL: Die traegt keine
 * Antwort-Kopfzeilen, und die Auslieferung bleibt unveraendert streng.
 *
 * <b>Die Art setzt das Frontend, nicht die Antwort.</b> Der `Content-Type` des Inhaltswegs folgt
 * der am Inhalt erkannten Vorschauart und ist kein Versprechen ueber die Bytes (`apiBlob`). Welche
 * Art die Objekt-URL bekommt, entscheidet darum {@link ANZEIGE_ART} — der Betrachter des Browsers
 * folgt ihr, und ein PDF soll als PDF geoeffnet werden, nicht als das, was die Antwort behauptet.
 * Gesetzt wird sie mit `slice`: Das gibt denselben Inhalt unter neuer Art heraus, ohne ihn zu
 * kopieren.
 *
 * <b>Ein PDF zeigt der Browser selbst</b> (E7, Entscheid Manne 2026-09-30): kein eigener
 * Betrachter, kein pdf.js nahe der Chunk-Grenze. Ein beschaedigtes oder geschuetztes PDF meldet
 * sich im Rahmen mit der eigenen Meldung oder Passwortabfrage — darum traegt das Fenster
 * <b>immer</b> „Herunterladen", auch waehrend des Ladens und nach einem Ausfall: Was sich hier
 * nicht ansehen laesst, laesst sich wenigstens sichern.
 *
 * <b>Ohne Vorschauart gibt es die Komponente nicht</b> ({@link AnlageMitVorschau}). Die Art der
 * Anzeige folgt allein ihr; so gibt es keinen Zweig „Vorschau ohne Vorschauart", der im Fenster
 * eine leere Flaeche zeigte.
 *
 * <b>Die Objekt-URL wird freigegeben</b>, wenn das Fenster abgebaut wird — und der Aufrufer baut es
 * beim Schliessen ab ({@link Anlagen}). Eine Antwort, die erst danach eintrifft, legt keine URL
 * mehr an: Sie haette niemanden mehr, der sie freigibt, und der Inhalt bliebe bis zum Neuladen der
 * Seite im Speicher des Browsers.
 */

/**
 * Eine Anlage, zu der es eine Vorschau gibt — `vorschauArt` ist belegt.
 *
 * Der Typ traegt die Bedingung, nicht der Rumpf der Komponente: Der Aufrufer entscheidet ohnehin
 * schon, ob er „Anzeigen" anbietet, und eine zweite Pruefung hier waere ein Zweig ohne Fall.
 */
export interface AnlageMitVorschau extends Omit<Anlage, 'vorschauArt'> {
  readonly vorschauArt: Vorschauart;
}

export interface AnlageVorschauProps {
  /** Das Angebot, an dem die Anlage haengt. */
  readonly angebotId: number;
  readonly anlage: AnlageMitVorschau;
  /** Ruft der Aufrufer auf, um das Fenster abzubauen — Escape und die Schliessen-Taste loesen es. */
  readonly onSchliessen: () => void;
}

const LAEDT = 'Die Datei wird geladen …';
const AUSFALL = 'Die Datei ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const BILD_AUSFALL = 'Das Bild lässt sich nicht anzeigen.';

/** Die Art, unter der der Inhalt angezeigt wird — je Vorschauart eine, fest im Frontend. */
const ANZEIGE_ART: Readonly<Record<Vorschauart, string>> = {
  PNG: 'image/png',
  JPEG: 'image/jpeg',
  GIF: 'image/gif',
  WEBP: 'image/webp',
  PDF: 'application/pdf',
};

/**
 * Die Hoehe des Inhaltsbereichs — fest, damit das Fenster beim Wechsel der Anlage nicht springt.
 *
 * Ein Anteil der Sichthoehe mit Deckel: Auf einem kleinen Schirm bleibt Platz fuer Titel und
 * Tastenleiste, auf einem grossen waechst das Fenster nicht ins Unangenehme.
 */
const INHALT_HOEHE = 'min(72vh, 720px)';

/** Kantenlaenge der Icontaste (CLAUDE-design.md, „Tasten": Kreis 40 px). */
const ICONTASTE = 40;

/** Die Gestalt der Schliessen-Taste (Vorlage `.icontaste` Z. 67–68). */
function icontasteSx(theme: Theme) {
  return {
    width: ICONTASTE,
    height: ICONTASTE,
    flex: 'none',
    borderRadius: `${RADIUS_RUND}px`,
    border: 0,
    cursor: 'pointer',
    display: 'grid',
    placeItems: 'center',
    color: theme.vars.palette.kupferwolke.textMatt,
    background: theme.vars.palette.kupferwolke.flaecheWeich,
    transition: 'background .15s ease, color .15s ease',
    '&:hover': {
      background: theme.vars.palette.kupferwolke.toenung.pfirsich.flaeche,
      color: theme.vars.palette.kupferwolke.toenung.pfirsich.schrift,
    },
  };
}

/** Was das Fenster ueber den Inhalt weiss. Die URL haengt am Stand, nicht neben ihm. */
type Inhalt =
  | { readonly stand: 'laedt' }
  | { readonly stand: 'ausfall' }
  | { readonly stand: 'da'; readonly url: string };

/**
 * Der Abrufversuch als Ergebnis: der Inhalt oder `null` fuer einen Ausfall.
 *
 * Ein Ergebnis und kein Setzen von Zustand — so gibt es im Effekt genau eine Stelle, die nach dem
 * Abbau nichts mehr schreibt (CLAUDE-react.md, Race-Conditions).
 */
async function holen(angebotId: number, anlageId: number): Promise<Blob | null> {
  try {
    return await anlageInhalt(angebotId, anlageId);
  } catch {
    return null;
  }
}

export default function AnlageVorschau({
  angebotId,
  anlage,
  onSchliessen,
}: AnlageVorschauProps) {
  const titelId = useId();
  const [inhalt, setzeInhalt] = useState<Inhalt>({ stand: 'laedt' });
  const [bildAusfall, setzeBildAusfall] = useState(false);

  const { id: anlageId, dateiName, vorschauArt } = anlage;

  useEffect(() => {
    let aktuell = true;
    let angelegt: string | null = null;
    void holen(angebotId, anlageId).then((bytes) => {
      // Nach dem Abbau legt dieser Lauf keine Objekt-URL mehr an — sie bliebe sonst ungenutzt
      // und ungeloescht im Speicher des Browsers liegen.
      if (!aktuell) {
        return;
      }
      if (bytes === null) {
        setzeInhalt({ stand: 'ausfall' });
        return;
      }
      // `slice` gibt denselben Inhalt unter neuer Art heraus, ohne die Bytes zu kopieren.
      angelegt = URL.createObjectURL(bytes.slice(0, bytes.size, ANZEIGE_ART[vorschauArt]));
      setzeInhalt({ stand: 'da', url: angelegt });
    });
    return () => {
      aktuell = false;
      if (angelegt !== null) {
        URL.revokeObjectURL(angelegt);
      }
    };
  }, [angebotId, anlageId, vorschauArt]);

  let ansicht: ReactNode;
  if (inhalt.stand === 'laedt') {
    ansicht = (
      <Typography
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
      >
        {LAEDT}
      </Typography>
    );
  } else if (inhalt.stand === 'ausfall') {
    ansicht = <Alert severity="error">{AUSFALL}</Alert>;
  } else if (vorschauArt === 'PDF') {
    ansicht = (
      <Box
        component="iframe"
        src={inhalt.url}
        title={dateiName}
        sx={{ width: '100%', height: '100%', border: 0 }}
      />
    );
  } else if (bildAusfall) {
    ansicht = <Alert severity="error">{BILD_AUSFALL}</Alert>;
  } else {
    ansicht = (
      <Box
        component="img"
        src={inhalt.url}
        alt={dateiName}
        onError={() => {
          setzeBildAusfall(true);
        }}
        sx={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }}
      />
    );
  }

  return (
    <Dialog open onClose={onSchliessen} fullWidth maxWidth="lg" aria-labelledby={titelId}>
      <Box
        sx={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '20px 20px 4px 28px' }}
      >
        <DialogTitle
          id={titelId}
          sx={{ flex: '1 1 auto', padding: 0, fontSize: 16, wordBreak: 'break-word' }}
        >
          {dateiName}
        </DialogTitle>
        <Box
          component="button"
          type="button"
          aria-label="Schließen"
          onClick={onSchliessen}
          sx={icontasteSx}
        >
          <IconX size={18} stroke={1.8} aria-hidden />
        </Box>
      </Box>
      <DialogContent
        sx={{ height: INHALT_HOEHE, display: 'grid', placeItems: 'center', overflow: 'hidden' }}
      >
        {ansicht}
      </DialogContent>
      <DialogActions sx={{ padding: '4px 24px 20px' }}>
        <Button
          component="a"
          href={anlageInhaltPfad(angebotId, anlageId)}
          download
          sx={weichSx}
        >
          <TastenSymbol>
            <IconDownload size={16} stroke={1.8} />
          </TastenSymbol>
          Herunterladen
        </Button>
      </DialogActions>
    </Dialog>
  );
}
