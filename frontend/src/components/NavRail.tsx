import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import {
  IconBook,
  IconBuildingCommunity,
  IconClock,
  IconFileDescription,
  IconFileInvoice,
  IconId,
  IconLayoutSidebarLeftCollapse,
  IconLayoutSidebarLeftExpand,
  IconSettings,
} from '@tabler/icons-react';
import type { TablerIcon } from '@tabler/icons-react';
import { useEffect, useState } from 'react';
import { NavLink } from 'react-router-dom';

import { instance } from '../api/instance';
import { FUSS_EINTRAEGE, NAV_BLOECKE } from '../layout/navItems';
import type { NavEintrag, Symbolname } from '../layout/navItems';
import { liesEingeklappt, merkeEingeklappt } from '../lib/railState';
import { RADIUS_GROSS, RADIUS_KLEIN, RADIUS_RUND } from '../theme';
import BrandMark from './BrandMark';
import UserMenu from './UserMenu';

/**
 * Die Schiene: eine freistehende weisse Karte links, oben die Marke, darunter die
 * Navigationsbloecke, unten der Fuss (Vorlage `.schiene` Z. 30, `.marke` Z. 31–34,
 * `.gruppe` Z. 35, `.nav a` Z. 36–41, `.schiene .fuss` Z. 42).
 *
 * Radius gross, `schatten-karte`, und sie klebt oben — die Buehne rechts scrollt an ihr vorbei
 * (CLAUDE-design.md, „Rahmen").
 *
 * Der aktive Eintrag liegt auf Pfirsich mit Pfirsich-Schrift in 700; `aria-current="page"` setzt
 * `NavLink` selbst, und genau daran haengt auch die Gestalt — Ansicht und Zugaenglichkeit koennen
 * so nicht auseinanderlaufen. Weil `NavLink` ohne `end` auch auf die tieferen Pfade passt, bleibt
 * „Firmen" auf `/firmen/7` aktiv (CLAUDE-design.md: „der laengste passende Pfad").
 *
 * Eingeklappt (76 px) bleiben Markenmal, Symbole und das Kuerzel der Nutzerkarte; die
 * Beschriftung geht in `aria-label` ueber, damit jeder Eintrag seinen Namen behaelt. Der
 * Gruppentitel entfaellt dort ganz — auf 76 px ist kein Platz fuer ihn, und er benennt keinen
 * eigenen Tastaturweg.
 *
 * `onWahl` ist der Rueckruf fuer die Betriebsart hinter der Schaltflaeche ({@link AppShell}): Dort
 * liegt die Schiene ueber dem Inhalt und muss sich schliessen, sobald ein Ziel gewaehlt ist.
 */

/** Breite ausgeklappt und eingeklappt (CLAUDE-design.md, „Rahmen"). */
const BREITE = 260;
const BREITE_EINGEKLAPPT = 76;

/** Die Symbole der Vorlage — dieselbe Familie, dieselben Namen (E3). */
const SYMBOLE: Readonly<Record<Symbolname, TablerIcon>> = {
  'file-description': IconFileDescription,
  clock: IconClock,
  'file-invoice': IconFileInvoice,
  'building-community': IconBuildingCommunity,
  id: IconId,
  settings: IconSettings,
  book: IconBook,
};

/** 20 px, Strich in `currentColor` — die Farbe kommt damit vom Eintrag (Vorlage `.nav a i`). */
function NavSymbol({ name }: { readonly name: Symbolname }) {
  const Symbol = SYMBOLE[name];
  return (
    <Symbol
      size={20}
      stroke={1.6}
      aria-hidden
      // Der Strich selbst ist fuer Hilfsmittel unsichtbar; eingeklappt ist er aber das einzige
      // Unterscheidungsmerkmal zweier Eintraege. Der Griff macht ihn im Test pruefbar.
      data-testid={`nav-symbol-${name}`}
      style={{ flex: 'none' }}
    />
  );
}

/** Gestalt eines Eintrags (Vorlage `.nav a` Z. 36–41). */
const eintragStil = {
  display: 'flex',
  alignItems: 'center',
  gap: '12px',
  padding: '10px 12px',
  borderRadius: `${RADIUS_KLEIN}px`,
  fontSize: 14,
  fontWeight: 500,
  lineHeight: '20px',
  fontFamily: 'inherit',
  textAlign: 'left',
  textDecoration: 'none',
  border: 0,
  background: 'transparent',
  transition: 'background .15s ease, color .15s ease',
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
        color: theme.vars.palette.kupferwolke.textMatt,
        '&:hover': {
          background: theme.vars.palette.kupferwolke.flaecheWeich,
          color: theme.vars.palette.kupferwolke.text,
        },
        // Der aktive Eintrag liegt auf Pfirsich mit Pfirsich-Schrift in 700
        // (CLAUDE-design.md, „Rahmen").
        '&[aria-current="page"]': {
          background: theme.vars.palette.kupferwolke.toenung.pfirsich.flaeche,
          color: theme.vars.palette.kupferwolke.toenung.pfirsich.schrift,
          fontWeight: 700,
        },
      })}
    >
      <NavSymbol name={eintrag.symbol} />
      {eingeklappt ? null : eintrag.beschriftung}
    </Box>
  );
}

/**
 * Die Icontaste neben der Marke, die die Schiene ein- und ausklappt (E7).
 *
 * `aria-expanded` sagt, ob die Schiene offen steht — das ist die Aussage der Taste, nicht ein
 * gedrueckter Zustand. Der Name bleibt darum in beiden Lagen derselbe; das Symbol zeigt die
 * Richtung.
 */
function EinklappTaste({
  eingeklappt,
  umschalten,
}: {
  readonly eingeklappt: boolean;
  readonly umschalten: () => void;
}) {
  const Symbol = eingeklappt ? IconLayoutSidebarLeftExpand : IconLayoutSidebarLeftCollapse;
  return (
    <Box
      component="button"
      type="button"
      aria-label="Einklappen"
      aria-expanded={!eingeklappt}
      onClick={umschalten}
      sx={(theme) => ({
        // Icontaste: Kreis 40 px auf „Flaeche weich", Symbol in Text matt, Hover Pfirsich
        // (CLAUDE-design.md, „Tasten").
        width: 40,
        height: 40,
        flex: 'none',
        display: 'grid',
        placeItems: 'center',
        padding: 0,
        border: 0,
        cursor: 'pointer',
        borderRadius: `${RADIUS_RUND}px`,
        background: theme.vars.palette.kupferwolke.flaecheWeich,
        color: theme.vars.palette.kupferwolke.textMatt,
        '&:hover': {
          background: theme.vars.palette.kupferwolke.toenung.pfirsich.flaeche,
          color: theme.vars.palette.kupferwolke.toenung.pfirsich.schrift,
        },
      })}
    >
      <Symbol size={20} stroke={1.7} aria-hidden />
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
        // Die Schiene ist eine freistehende weisse Karte (CLAUDE-design.md, „Rahmen").
        background: theme.vars.palette.kupferwolke.flaeche,
        borderRadius: `${RADIUS_GROSS}px`,
        boxShadow: theme.vars.palette.kupferwolke.schatten.karte,
        padding: '22px 16px',
        display: 'flex',
        flexDirection: 'column',
        gap: '6px',
        // Sie klebt oben, um den Aussenabstand des Rahmens versetzt (Vorlage `.schiene` Z. 30).
        // Die feste Hoehe ist dafuer die Voraussetzung und nicht Schmuck: Nur eine Karte, die
        // kuerzer ist als ihre Spalte, hat einen Weg, den sie kleben kann — und nur sie schiebt
        // ihren Fuss mit der Nutzerkarte nach unten. Was nicht hineinpasst, scrollt in ihr.
        position: 'sticky',
        top: '20px',
        height: 'calc(100vh - 40px)',
        overflowY: 'auto',
        transition: 'width .14s ease',
      })}
    >
      <Box
        data-testid="schiene-kopf"
        sx={{
          display: 'flex',
          // Eingeklappt stehen Marke und Taste uebereinander — nebeneinander reichten 76 px nicht.
          flexDirection: eingeklappt ? 'column' : 'row',
          alignItems: 'center',
          gap: '10px',
          padding: '4px 0 18px',
        }}
      >
        <BrandMark version={version} kompakt={eingeklappt} />
        <Box sx={{ marginLeft: eingeklappt ? 0 : 'auto' }}>
          <EinklappTaste eingeklappt={eingeklappt} umschalten={umschalten} />
        </Box>
      </Box>
      <Box
        data-testid="schiene-bloecke"
        sx={{ display: 'flex', flexDirection: 'column', gap: '2px' }}
      >
        {NAV_BLOECKE.map((block) => (
          <Box key={block.titel} sx={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
            {eingeklappt ? null : (
              <Typography
                variant="overline"
                component="div"
                sx={(theme) => ({
                  padding: '14px 12px 6px',
                  color: theme.vars.palette.kupferwolke.textSchwach,
                })}
              >
                {block.titel}
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
        sx={{
          marginTop: 'auto',
          paddingTop: '18px',
          display: 'flex',
          flexDirection: 'column',
          gap: '10px',
        }}
      >
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
          {FUSS_EINTRAEGE.map((eintrag) => (
            <NavZiel
              key={eintrag.ziel}
              eintrag={eintrag}
              eingeklappt={eingeklappt}
              onWahl={onWahl}
            />
          ))}
        </Box>
        <UserMenu kompakt={eingeklappt} />
      </Box>
    </Box>
  );
}
