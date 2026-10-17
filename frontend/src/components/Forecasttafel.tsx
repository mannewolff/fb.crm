import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { IconChevronDown, IconChevronRight } from '@tabler/icons-react';
import { useState } from 'react';

import Angebotsweg from './Angebotsweg';
import Tafel, { type TafelSpalte } from './Tafel';
import type { Forecast, Forecastjahr, Forecastposition } from '../api/forecast';
import { euro } from '../lib/geld';
import { ZAHLEN_KLASSE } from '../theme';

/**
 * Der Forecast als Tafel: je Jahr eine Zeile mit „zugesagt", „angeboten" und ihrer Summe, darunter
 * aufklappbar die offenen Positionen des Jahres (#302, Kriterien 1, 5 und 6; Issue #312).
 *
 * <b>Hier entsteht keine Zahl</b> — alle vier Betraege kommen aus der Antwort (#216). Eine
 * Gesamtsumme ueber alle Jahre gibt es nicht (Plan #303, E13).
 *
 * <b>Aufgeklappt wird mit einer Taste</b>, die `aria-expanded` traegt, und die Positionen stehen in
 * einer Zeile mit `colSpan` als eigene, kleine Tabelle mit eigenen Spaltenkoepfen (E7): Eine
 * `Tafel` innen rollte mit ihrer Mindestbreite immer waagerecht, und Positionszeilen in den vier
 * Spalten der Jahreszeile widersprachen deren Koepfen. Die Innentabelle erbt die
 * Nachfahren-Selektoren der `Tafel` — Polster und Kopfgestalt stimmen damit mit der Jahreszeile
 * ueberein. Mehrere Jahre duerfen gleichzeitig offen stehen.
 */
const TITEL = 'Forecast';

const LEER = 'Nichts angeboten — alle Positionen sind abgerechnet.';

const SPALTEN: readonly TafelSpalte[] = [
  'Jahr',
  { beschriftung: 'Zugesagt', zahl: true },
  { beschriftung: 'Angeboten', zahl: true },
  { beschriftung: 'Summe', zahl: true },
];

/** Die Innentabelle: ihre Koepfe, in der Reihenfolge ihrer Zellen. */
const SPALTEN_POSITIONEN = ['Firma', 'Angebot', 'Position'] as const;
const SPALTE_RESTBETRAG = 'Restbetrag';

/** Die Gestalt einer Betragszelle — wie die Betraege der uebrigen Tafeln der Startseite. */
const BETRAG = { whiteSpace: 'nowrap', textAlign: 'right' } as const;

/** Die Symbolgroesse in der Taste eines Jahres. */
const SYMBOL_TASTE = 16;

/** Eine Betragszelle in Euro, untereinander gestellt. */
function Betrag({ cent, fett = false }: { readonly cent: number; readonly fett?: boolean }) {
  return (
    <Box
      component="td"
      className={ZAHLEN_KLASSE}
      sx={{ ...BETRAG, fontWeight: fett ? 600 : undefined }}
    >
      {euro(cent)}
    </Box>
  );
}

/** Die Taste in der ersten Zelle der Jahreszeile: sagt, ob die Positionen offen stehen. */
function Jahrestaste({
  jahr,
  offen,
  umschalten,
}: {
  readonly jahr: string;
  readonly offen: boolean;
  readonly umschalten: () => void;
}) {
  const Symbol = offen ? IconChevronDown : IconChevronRight;
  return (
    <Box
      component="button"
      type="button"
      aria-expanded={offen}
      aria-label={`Positionen ${jahr} ${offen ? 'verbergen' : 'anzeigen'}`}
      onClick={umschalten}
      className={ZAHLEN_KLASSE}
      sx={(theme) => ({
        display: 'inline-flex',
        alignItems: 'center',
        gap: '6px',
        padding: 0,
        border: 0,
        background: 'none',
        cursor: 'pointer',
        font: 'inherit',
        fontWeight: 600,
        color: 'inherit',
        '&:hover': { color: theme.vars.palette.kupferwolke.kupfer },
      })}
    >
      <Symbol size={SYMBOL_TASTE} stroke={1.8} aria-hidden />
      {jahr}
    </Box>
  );
}

/** Eine Position unter ihrem Jahr: Firma, Angebot als Weg, Bezeichnung, Restbetrag (Kriterium 5). */
function Positionszeile({ position }: { readonly position: Forecastposition }) {
  return (
    <Box component="tr">
      <Box component="td" sx={{ fontWeight: 600 }}>
        {position.firmaName}
      </Box>
      <Box component="td" sx={{ fontWeight: 500 }}>
        <Angebotsweg
          angebotId={position.angebotId}
          tag={position.angebotDatum}
          titel={position.angebotTitel}
        />
      </Box>
      <Box component="td">{position.bezeichnung}</Box>
      <Betrag cent={position.nettoInCent} />
    </Box>
  );
}

/**
 * Die aufgeklappten Positionen eines Jahres: eine Zeile ueber alle vier Spalten mit einer kleinen
 * Tabelle darin (E7).
 *
 * <b>Die Zelle selbst leuchtet beim Ueberfahren nicht auf.</b> Der Hover der `Tafel` faerbt jede
 * `td` einer ueberfahrenen Zeile — auch die eine weite Zelle hier, sobald eine Positionszeile darin
 * ueberfahren wird. Zurueckgesetzt wird darum nur sie; die Positionszeilen behalten ihren Hover. Der
 * doppelte Selektor schlaegt den Nachfahren-Selektor der `Tafel`, den ein einfacher verloere.
 */
function Positionen({ jahr }: { readonly jahr: Forecastjahr }) {
  return (
    <Box component="tr" sx={{ '&&:hover > td': { background: 'transparent' } }}>
      <Box component="td" colSpan={4} sx={{ paddingTop: 0 }}>
        <Box
          component="table"
          aria-label={`Positionen ${jahr.jahr}`}
          sx={{ width: '100%', borderCollapse: 'separate', borderSpacing: 0 }}
        >
          <Box component="thead">
            <Box component="tr">
              {SPALTEN_POSITIONEN.map((spalte) => (
                <Box component="th" scope="col" key={spalte}>
                  {spalte}
                </Box>
              ))}
              <Box component="th" scope="col" data-zahl="">
                {SPALTE_RESTBETRAG}
              </Box>
            </Box>
          </Box>
          <Box component="tbody">
            {jahr.positionen.map((position, stelle) => (
              // Eine Position traegt keine eigene Kennung in der Antwort, und die Liste ist fest:
              // Sie wird nur ersetzt, nie umsortiert.
              <Positionszeile key={stelle} position={position} />
            ))}
          </Box>
        </Box>
      </Box>
    </Box>
  );
}

/** Die Zeile eines Jahres samt, wenn aufgeklappt, seinen Positionen darunter. */
function Jahreszeile({
  jahr,
  offen,
  umschalten,
}: {
  readonly jahr: Forecastjahr;
  readonly offen: boolean;
  readonly umschalten: () => void;
}) {
  return (
    <>
      <Box component="tr">
        <Box component="td" sx={{ whiteSpace: 'nowrap' }}>
          <Jahrestaste jahr={jahr.jahr} offen={offen} umschalten={umschalten} />
        </Box>
        <Betrag cent={jahr.zugesagtInCent} />
        <Betrag cent={jahr.angebotenInCent} />
        <Betrag cent={jahr.summeInCent} fett />
      </Box>
      {offen ? <Positionen jahr={jahr} /> : null}
    </>
  );
}

export default function Forecasttafel({ forecast }: { readonly forecast: Forecast }) {
  const [offen, setOffen] = useState<ReadonlySet<string>>(new Set());

  if (forecast.jahre.length === 0) {
    return (
      <Typography
        role="status"
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textMatt })}
      >
        {LEER}
      </Typography>
    );
  }

  const umschalten = (jahr: string) => {
    setOffen((vorher) => {
      const neu = new Set(vorher);
      if (!neu.delete(jahr)) {
        neu.add(jahr);
      }
      return neu;
    });
  };

  return (
    <Tafel beschriftung={TITEL} spalten={SPALTEN}>
      {forecast.jahre.map((jahr) => (
        <Jahreszeile
          key={jahr.jahr}
          jahr={jahr}
          offen={offen.has(jahr.jahr)}
          umschalten={() => {
            umschalten(jahr.jahr);
          }}
        />
      ))}
    </Tafel>
  );
}
