import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { NavLink } from 'react-router-dom';

import { instance } from '../api/instance';
import { FUSS_EINTRAEGE, NAV_BLOECKE } from '../layout/navItems';
import type { NavEintrag, Symbolname } from '../layout/navItems';
import { liesEingeklappt, merkeEingeklappt } from '../lib/railState';
import { CARD_RADIUS } from '../theme';
import BrandMark from './BrandMark';

/**
 * Die Schiene: eingelassene Nut links, oben die Marke, darunter die Navigationsbloecke, unten der
 * Fuss (Vorlage `.schiene` Z. 205–214, `.nav-block` Z. 243–245, `.nav-eintrag` Z. 247–270,
 * `.schiene-fuss` Z. 279).
 *
 * Der aktive Eintrag ist die erhabene Taste mit kupfernem Symbol; `aria-current="page"` setzt
 * `NavLink` selbst, und genau daran haengt auch die Gestalt — Ansicht und Zugaenglichkeit koennen
 * so nicht auseinanderlaufen. Weil `NavLink` ohne `end` auch auf die tieferen Pfade passt, bleibt
 * „Firmen" auf `/firmen/7` aktiv (CLAUDE-design.md: „der laengste passende Pfad").
 *
 * Eingeklappt bleiben nur die Symbole; die Beschriftung geht in `aria-label` ueber, damit jeder
 * Eintrag seinen Namen behaelt. Das Etikett des Blocks entfaellt dort ganz — auf 64 px ist kein
 * Platz fuer Versalien mit 0,14 em Laufweite, und es benennt keinen eigenen Tastaturweg.
 *
 * `onWahl` ist der Rueckruf fuer die Betriebsart hinter der Schaltflaeche ({@link AppShell}): Dort
 * liegt die Schiene ueber dem Inhalt und muss sich schliessen, sobald ein Ziel gewaehlt ist.
 */

/** Breite ausgeklappt (Vorlage `.warte` Z. 201) und eingeklappt (CLAUDE-design.md). */
const BREITE = 224;
const BREITE_EINGEKLAPPT = 64;

/** Die Symbole der Vorlage: 16 px, Strich in `currentColor`. */
const SYMBOLE: Readonly<Record<Symbolname, ReactNode>> = {
  firmen: (
    <path
      d="M2.5 13.5V3a.5.5 0 0 1 .5-.5h5a.5.5 0 0 1 .5.5v10.5M8.5 6.5H13a.5.5 0 0 1 .5.5v6.5M1.5 13.5h13M4.5 5h2M4.5 7.5h2M4.5 10h2M10.5 9h1.5M10.5 11.5h1.5"
      stroke="currentColor"
      strokeWidth="1.3"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  ),
  administration: (
    <>
      <circle cx="8" cy="8" r="2.2" stroke="currentColor" strokeWidth="1.5" />
      <path
        d="M8 1.8v1.6M8 12.6v1.6M1.8 8h1.6M12.6 8h1.6M3.6 3.6l1.1 1.1M11.3 11.3l1.1 1.1M3.6 12.4l1.1-1.1M11.3 4.7l1.1-1.1"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
      />
    </>
  ),
  dokumentation: (
    <path
      d="M3 2.5h6.5L13 6v7.5H3zM9.5 2.5V6H13M5.5 9h5M5.5 11.5h3.5"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinejoin="round"
      strokeLinecap="round"
    />
  ),
  einklappen: (
    <path
      d="M10 3.5 5.5 8l4.5 4.5M13 3.5v9"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  ),
};

function NavSymbol({ name }: { readonly name: Symbolname }) {
  return (
    <Box
      component="svg"
      viewBox="0 0 16 16"
      fill="none"
      aria-hidden
      className="nav-symbol"
      sx={(theme) => ({
        width: 16,
        height: 16,
        flex: 'none',
        color: theme.vars.palette.kupferwarte.textSchwach,
      })}
    >
      {SYMBOLE[name]}
    </Box>
  );
}

/** Gestalt eines Eintrags — fuer Link und Taste dieselbe (Vorlage `.nav-eintrag`). */
const eintragStil = {
  display: 'flex',
  alignItems: 'center',
  gap: '10px',
  padding: '7px 10px',
  borderRadius: `${CARD_RADIUS}px`,
  fontSize: 13,
  fontWeight: 500,
  // Link und Taste tragen dieselbe Zeilenhoehe — die Taste erbt sonst keine, und der Eintrag
  // „Einklappen" stuende 3 px niedriger als seine Nachbarn.
  lineHeight: '20px',
  fontFamily: 'inherit',
  textAlign: 'left',
  textDecoration: 'none',
  cursor: 'pointer',
  border: '1px solid transparent',
  background: 'transparent',
  transition: 'background .14s ease, color .14s ease',
} as const;

/**
 * Ein Ziel der Schiene — dieselbe Gestalt in den Bloecken und im Fuss.
 *
 * Die Gestalt haengt an `aria-current="page"`, das `NavLink` setzt (siehe Klassenkommentar).
 */
function NavZiel({
  eintrag,
  eingeklappt,
  onWahl,
}: {
  readonly eintrag: NavEintrag;
  readonly eingeklappt: boolean;
  readonly onWahl?: () => void;
}) {
  return (
    <Box
      component={NavLink}
      to={eintrag.ziel}
      onClick={onWahl}
      aria-label={eingeklappt ? eintrag.beschriftung : undefined}
      sx={(theme) => ({
        ...eintragStil,
        color: theme.vars.palette.kupferwarte.textMatt,
        '&:hover': {
          background: `color-mix(in srgb, ${theme.vars.palette.kupferwarte.platte} 70%, transparent)`,
          color: theme.vars.palette.kupferwarte.text,
        },
        '&[aria-current="page"]': {
          background: `linear-gradient(180deg, ${theme.vars.palette.kupferwarte.platteHoch}, ${theme.vars.palette.kupferwarte.platte})`,
          borderColor: theme.vars.palette.kupferwarte.rand,
          boxShadow: theme.vars.palette.kupferwarte.schatten.taste,
          color: theme.vars.palette.kupferwarte.text,
        },
        '&[aria-current="page"] .nav-symbol': {
          color: theme.vars.palette.kupferwarte.kupfer,
        },
      })}
    >
      <NavSymbol name={eintrag.symbol} />
      {eingeklappt ? null : eintrag.beschriftung}
    </Box>
  );
}

export default function NavRail({ onWahl }: { readonly onWahl?: () => void }) {
  const [eingeklappt, setEingeklappt] = useState(liesEingeklappt);
  const [version, setVersion] = useState<string | undefined>(undefined);

  useEffect(() => {
    instance()
      .then((antwort) => {
        setVersion(`v${antwort.version}`);
      })
      .catch(() => {
        // Ohne Versionsstand bleibt die Zeile unter dem Namen leer; die Schiene arbeitet weiter.
      });
  }, []);

  const umschalten = () => {
    merkeEingeklappt(!eingeklappt);
    setEingeklappt(!eingeklappt);
  };

  return (
    <Box
      component="nav"
      aria-label="Hauptnavigation"
      data-eingeklappt={String(eingeklappt)}
      sx={(theme) => ({
        width: eingeklappt ? BREITE_EINGEKLAPPT : BREITE,
        background: `linear-gradient(180deg, ${theme.vars.palette.kupferwarte.grundTief}, ${theme.vars.palette.kupferwarte.nute})`,
        borderRight: `1px solid ${theme.vars.palette.kupferwarte.rand}`,
        boxShadow: theme.vars.palette.kupferwarte.schatten.nute,
        paddingBlock: '18px 24px',
        paddingInline: '14px',
        display: 'flex',
        flexDirection: 'column',
        gap: '22px',
        transition: 'width .14s ease',
      })}
    >
      <Box data-testid="schiene-kopf">
        <BrandMark version={version} kompakt={eingeklappt} />
      </Box>
      <Box
        data-testid="schiene-bloecke"
        sx={{ display: 'flex', flexDirection: 'column', gap: '2px' }}
      >
        {NAV_BLOECKE.map((block) => (
          <Box
            key={block.etikett}
            sx={{ display: 'flex', flexDirection: 'column', gap: '3px' }}
          >
            {eingeklappt ? null : (
              <Typography variant="overline" component="div" sx={{ padding: '0 8px 7px' }}>
                {block.etikett}
              </Typography>
            )}
            {block.eintraege.map((eintrag) => (
              <NavZiel
                key={eintrag.ziel}
                eintrag={eintrag}
                eingeklappt={eingeklappt}
                onWahl={onWahl}
              />
            ))}
          </Box>
        ))}
      </Box>
      <Box
        data-testid="schiene-fuss"
        sx={{ marginTop: 'auto', display: 'flex', flexDirection: 'column', gap: '3px' }}
      >
        {FUSS_EINTRAEGE.map((fuss) =>
          fuss.art === 'ziel' ? (
            <NavZiel
              key={fuss.beschriftung}
              eintrag={fuss}
              eingeklappt={eingeklappt}
              onWahl={onWahl}
            />
          ) : (
            <Box
              key={fuss.beschriftung}
              component="button"
              type="button"
              aria-pressed={eingeklappt}
              aria-label={eingeklappt ? fuss.beschriftung : undefined}
              onClick={umschalten}
              sx={(theme) => ({
                ...eintragStil,
                color: theme.vars.palette.kupferwarte.textMatt,
                '&:hover': {
                  background: `color-mix(in srgb, ${theme.vars.palette.kupferwarte.platte} 70%, transparent)`,
                  color: theme.vars.palette.kupferwarte.text,
                },
                '& .nav-symbol': { transform: eingeklappt ? 'scaleX(-1)' : 'none' },
              })}
            >
              <NavSymbol name={fuss.symbol} />
              {eingeklappt ? null : fuss.beschriftung}
            </Box>
          ),
        )}
      </Box>
    </Box>
  );
}
