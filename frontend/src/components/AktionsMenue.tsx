import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import { IconDots } from '@tabler/icons-react';
import type { ReactNode } from 'react';
import { useEffect, useId, useState } from 'react';

import { RADIUS_RUND } from '../theme';
import { rundeIcontaste } from './rundeIcontaste';
import TastenSymbol from './TastenSymbol';
import WeicheTaste from './WeicheTaste';

/**
 * Das ⋯-Menue: der Ort fuer seltene und folgenreiche Aktionen (Vorlage `.icontaste` Z. 67–68,
 * HTML Z. 148 und Z. 172).
 *
 * **Folgenreiches steht nicht gleichrangig daneben** (CLAUDE-design.md, „Tasten"): Stilllegen,
 * Loeschen und Archivieren stehen nie als eigene Taste in der Kopfkarte, sondern hier — in
 * Rose-Schrift und mit einer Rueckfrage vor der Ausfuehrung. Harmlose Aktionen („Bearbeiten",
 * „Wieder aktivieren") laufen ohne Zwischenschritt.
 *
 * Die Rueckfrage ist ein **eigener Dialog**, nicht das `confirm` des Browsers (E8): Das ist nicht
 * gestaltbar, blockiert den Hauptfaden und ist im Test nur ueber einen Eingriff am globalen
 * Objekt zu fassen.
 *
 * **Der Fokus kehrt zur ⋯-Taste zurueck**, wenn die Rueckfrage geschlossen ist — gleich ob durch
 * „Abbrechen", Escape oder die Aktion selbst (Plan E8). Wer nur die Tastatur benutzt, stuende
 * sonst am Anfang des Dokuments und muesste sich zurueckarbeiten.
 */

/** Ein Eintrag des Menues. */
export interface AktionsEintrag {
  /** Die Aufschrift — und bei einer Rueckfrage die Aufschrift der bestaetigenden Taste. */
  readonly titel: string;
  /** Was der Eintrag tut. */
  readonly onAuswahl: () => void;
  /** Das stuetzende Symbol links der Aufschrift. */
  readonly symbol?: ReactNode;
  /**
   * Macht den Eintrag folgenreich: Der Satz erklaert die Folge und steht in der Rueckfrage.
   *
   * Ein Feld statt eines Schalters mit zweitem Textfeld — so kann kein Aufrufer eine Rueckfrage
   * ohne Begruendung stellen, und kein Satz liegt unbenutzt daneben.
   */
  readonly rueckfrage?: string;
}

export interface AktionsMenueProps {
  /** Der zugaengliche Name der ⋯-Taste, etwa „Aktionen für Gabi Rosenbaum". */
  readonly name: string;
  /** Das betroffene Objekt — es steht neben der Aktion im Titel der Rueckfrage. */
  readonly objekt: string;
  readonly eintraege: readonly AktionsEintrag[];
}

/** Die offene Rueckfrage: der Eintrag, bereits auf seine belegten Felder verengt. */
interface OffeneRueckfrage {
  readonly titel: string;
  readonly frage: string;
  readonly ausfuehren: () => void;
}

/** Kantenlaenge der Icontaste (CLAUDE-design.md, „Tasten": Kreis 40 px). */

/**
 * Die Rueckfrage vor einer folgenreichen Aktion.
 *
 * Eigene Komponente, damit die ⋯-Taste als `HTMLElement` hereinkommt und nicht als etwas, das
 * auch `null` sein koennte: Der Dialog entsteht erst, wenn das Menue offen war — und dann gibt es
 * die Taste.
 *
 * Den Fokus gibt der **Abbau** der Komponente zurueck, nicht der Klick. Waehrend der Dialog noch
 * steht, haelt MUI den Fokus in ihm fest; ein `focus()` im Klickbehandler waere sofort wieder
 * eingefangen. `disableRestoreFocus` schaltet dafuer die eigene Rueckgabe von MUI ab — sie hat
 * sich beim Oeffnen den gerade schliessenden Menueeintrag gemerkt und liefe ins Leere.
 */
function Rueckfrage({
  rueckfrage,
  objekt,
  taste,
  onEnde,
}: {
  readonly rueckfrage: OffeneRueckfrage;
  readonly objekt: string;
  readonly taste: HTMLElement;
  readonly onEnde: () => void;
}) {
  const titelId = useId();

  useEffect(
    () => () => {
      taste.focus();
    },
    [taste],
  );

  return (
    <Dialog open onClose={onEnde} aria-labelledby={titelId} disableRestoreFocus>
      <DialogTitle id={titelId}>{`${rueckfrage.titel}: ${objekt}`}</DialogTitle>
      <DialogContent>
        <DialogContentText>{rueckfrage.frage}</DialogContentText>
      </DialogContent>
      <DialogActions sx={{ padding: '4px 24px 20px', gap: '10px' }}>
        <WeicheTaste onClick={onEnde}>Abbrechen</WeicheTaste>
        <Button
          type="button"
          onClick={() => {
            onEnde();
            rueckfrage.ausfuehren();
          }}
          sx={(theme) => ({
            borderRadius: `${RADIUS_RUND}px`,
            padding: '11px 20px',
            color: theme.vars.palette.kupferwolke.toenung.rose.schrift,
            background: theme.vars.palette.kupferwolke.toenung.rose.flaeche,
            '&:hover': {
              background: theme.vars.palette.kupferwolke.toenung.rose.flaeche,
              transform: 'translateY(-1px)',
            },
            '&:active': { transform: 'none' },
          })}
        >
          {rueckfrage.titel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default function AktionsMenue({ name, objekt, eintraege }: AktionsMenueProps) {
  const menueId = useId();
  const [anker, setAnker] = useState<HTMLElement | null>(null);
  const [offen, setOffen] = useState(false);
  const [rueckfrage, setRueckfrage] = useState<OffeneRueckfrage | null>(null);

  const waehle = (eintrag: AktionsEintrag) => {
    setOffen(false);
    const { rueckfrage: frage } = eintrag;
    if (frage === undefined) {
      eintrag.onAuswahl();
      return;
    }
    setRueckfrage({ titel: eintrag.titel, frage, ausfuehren: eintrag.onAuswahl });
  };

  return (
    <>
      <Box
        component="button"
        type="button"
        aria-label={name}
        aria-haspopup="menu"
        aria-expanded={offen}
        aria-controls={offen ? menueId : undefined}
        onClick={(ereignis) => {
          setAnker(ereignis.currentTarget);
          setOffen(true);
        }}
        sx={rundeIcontaste}
      >
        <IconDots size={18} stroke={1.8} aria-hidden />
      </Box>
      {anker === null ? null : (
        <>
          <Menu
            id={menueId}
            anchorEl={anker}
            open={offen}
            onClose={() => {
              setOffen(false);
            }}
            anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
            transformOrigin={{ vertical: 'top', horizontal: 'right' }}
          >
            {eintraege.map((eintrag) => (
              <MenuItem
                key={eintrag.titel}
                onClick={() => {
                  waehle(eintrag);
                }}
                sx={(theme) => ({
                  gap: '10px',
                  // Folgenreiches traegt Rose-Schrift (CLAUDE-design.md, „Tasten").
                  color:
                    eintrag.rueckfrage === undefined
                      ? theme.vars.palette.kupferwolke.text
                      : theme.vars.palette.kupferwolke.toenung.rose.schrift,
                })}
              >
                {eintrag.symbol === undefined ? null : (
                  <TastenSymbol>{eintrag.symbol}</TastenSymbol>
                )}
                {eintrag.titel}
              </MenuItem>
            ))}
          </Menu>
          {rueckfrage === null ? null : (
            <Rueckfrage
              rueckfrage={rueckfrage}
              objekt={objekt}
              taste={anker}
              onEnde={() => {
                setRueckfrage(null);
              }}
            />
          )}
        </>
      )}
    </>
  );
}
