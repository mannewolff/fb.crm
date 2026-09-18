import { createTheme } from '@mui/material/styles';

/**
 * Kupferwarte — die einzige Wertequelle des Erscheinungsbildes.
 *
 * Alle Werte stammen aus der verbindlichen Vorlage `docs/entwurf-leitstand.html`
 * (`:root` Z. 11–60 hell, Z. 63–148 dunkel, `body` Z. 152–162, Schriften Z. 164–174,
 * Fokusring Z. 178–182, Etikett Z. 185–194, Bewegung Z. 1093–1096) und aus der
 * Palettentabelle in `CLAUDE-design.md`.
 *
 * Wo ein Wert von der Vorlage abweicht, steht der Grund an der Konstante. Abgewichen wird
 * ausschliesslich an den vier Stellen, die `CLAUDE-design.md` unter „Kontrast" als
 * AA-Verfehlung der Vorlage benennt — nachgezogen im selben Farbton, nie durch Senken der
 * Schwelle. Nachgerechnet wird das in `theme.test.ts` mit `lib/contrast.ts`.
 *
 * **Tokens sind Verweise, keine Werte.** Ansichten lesen die Flaechen, Linien und Melder
 * ueber `theme.vars.palette.kupferwarte.*`; die Konstanten unten liefern nur den jeweiligen
 * Wert eines Erscheinungsbildes und schalten nicht um.
 */

/** Platte, Kachel, Laufband. */
export const PANEL_RADIUS = 14;
/** Karte, Navigationseintrag. */
export const CARD_RADIUS = 10;
/** Bedienelement und Fokusring. */
export const CONTROL_RADIUS = 6;

/** Fokusring: 2 px Kupfer mit 2 px Abstand (Vorlage Z. 178–182). */
export const FOCUS_RING_WIDTH = 2;
export const FOCUS_RING_OFFSET = 2;

/** Die Rollen der Vorlage, die keine MUI-Rolle haben. */
export interface KupferwarteFarben {
  readonly grund: string;
  readonly grundTief: string;
  readonly nute: string;
  readonly platte: string;
  readonly platteFuss: string;
  readonly platteHoch: string;
  readonly rand: string;
  readonly randStark: string;
  readonly kante: string;
  readonly text: string;
  readonly textMatt: string;
  readonly textSchwach: string;
  readonly kupfer: string;
  readonly kupferHell: string;
  readonly kupferSchimmer: string;
  /** Schrift auf der Kupferfuellung. */
  readonly kupferSchrift: string;
  readonly gruen: string;
  readonly bernst: string;
  readonly zinnob: string;
  readonly stahl: string;
  readonly grau: string;
  /** Der Grund der Anwendung: Kupfer-Schimmer oben links ueber dem Grundton. */
  readonly grundVerlauf: string;
}

/** Die vier Tiefenstufen der Vorlage: Nut < Grund < Platte < Abgehoben. */
export interface KupferwarteSchatten {
  /** Innenschatten eingelassener Flaechen. */
  readonly nute: string;
  /** Lichtkante plus zwei Schattenebenen der Platte. */
  readonly platte: string;
  /** Abgehobene Flaeche, weiter geoeffnet. */
  readonly hoch: string;
  /** Lichtkante plus kurzer Schatten einer Taste. */
  readonly taste: string;
}

export interface KupferwartePalette extends KupferwarteFarben {
  readonly schatten: KupferwarteSchatten;
  /** IBM Plex Mono mit Tabellenziffern — Nummern, Betraege, Mengen, Datumsangaben. */
  readonly monoFontFamily: string;
}

declare module '@mui/material/styles' {
  interface Palette {
    kupferwarte: KupferwartePalette;
  }
  interface PaletteOptions {
    kupferwarte: KupferwartePalette;
  }
  // Blendet die Felder der CSS-Variablen (`vars`, `cssVarPrefix`, `colorSchemeSelector`)
  // am Theme-Typ ein. MUI 6 zeigt sie nur bei dieser Augmentation; ohne sie traegt das
  // Theme die Werte zur Laufzeit, der Typ kennt sie aber nicht.
  interface CssThemeVariables {
    enabled: true;
  }
}

const HELL_GRUND = '#E7E9ED';
const HELL_KANTE = 'rgba(255,255,255,.9)';
const HELL_KUPFER_SCHIMMER = 'rgba(168,95,44,.16)';

const DUNKEL_GRUND = '#0D1014';
const DUNKEL_KANTE = 'rgba(255,255,255,.075)';
const DUNKEL_KUPFER = '#D08A52';
const DUNKEL_KUPFER_SCHIMMER = 'rgba(208,138,82,.18)';

/** Titel und Anzeige (Vorlage Z. 164–169). */
const ARCHIVO = '"Archivo Variable", Archivo, system-ui, sans-serif';
/** Fliesstext (Vorlage Z. 158). */
const PLEX_SANS = '"IBM Plex Sans", system-ui, -apple-system, "Segoe UI", sans-serif';
/** Zahlen und Kennungen (Vorlage Z. 171–174). */
const PLEX_MONO = '"IBM Plex Mono", ui-monospace, "SF Mono", Menlo, monospace';

/**
 * Der Grund traegt in beiden Erscheinungsbildern denselben Schimmer oben links
 * (Vorlage Z. 152–162).
 */
function grundVerlauf(schimmer: string, grundton: string): string {
  return `radial-gradient(1100px 600px at 18% -8%, ${schimmer}, transparent 62%), ${grundton}`;
}

const hellFarben: KupferwarteFarben = {
  grund: HELL_GRUND,
  grundTief: '#D8DBE2',
  nute: '#D5D9E0',
  platte: '#FDFDFE',
  platteFuss: '#F2F4F7',
  platteHoch: '#FFFFFF',
  rand: '#CDD2DA',
  randStark: '#B7BEC9',
  kante: HELL_KANTE,
  text: '#14181E',
  // Vorlage: #58606C — auf der Nut nur 4,49:1 (CLAUDE-design.md, Verfehlung 2). Nachgedunkelt
  // im selben Ton auf 5,92:1. Weiter als noetig (4,5:1 haelt schon bei #575F6B), weil
  // textSchwach unten auf 4,54:1 gehoben werden muss und sonst mit dieser Stufe zusammenfiele.
  textMatt: '#484E58',
  // Vorlage: #868E9B — als Schrift nur 2,33–3,30:1 (CLAUDE-design.md, Verfehlung 1).
  // Nachgedunkelt im selben Ton auf 4,54:1 gegen die Nut, die schwaechste helle Flaeche.
  textSchwach: '#585F6B',
  kupfer: '#A85F2C',
  kupferHell: '#C2743C',
  kupferSchimmer: HELL_KUPFER_SCHIMMER,
  // Vorlage `.taste-kupfer` Z. 322–345: weisse Schrift auf der Kupferfuellung, 4,84:1.
  kupferSchrift: '#FFFFFF',
  // Melder: Vorlage #2F8F4E — auf der Nut nur 2,87:1 (CLAUDE-design.md, Verfehlung 4).
  gruen: '#2E8B4C',
  // Melder: Vorlage #B07C15 — auf der Nut nur 2,58:1 (CLAUDE-design.md, Verfehlung 4).
  bernst: '#A17213',
  zinnob: '#C8393E',
  stahl: '#2F6FC9',
  // Melder: Vorlage #8A929E — auf der Nut nur 2,22:1 (CLAUDE-design.md, Verfehlung 4).
  grau: '#727B8A',
  grundVerlauf: grundVerlauf(HELL_KUPFER_SCHIMMER, HELL_GRUND),
};

const dunkelFarben: KupferwarteFarben = {
  grund: DUNKEL_GRUND,
  grundTief: '#090B0E',
  nute: '#080A0D',
  platte: '#171B22',
  platteFuss: '#12151B',
  platteHoch: '#1E242D',
  rand: '#262C36',
  randStark: '#333B47',
  kante: DUNKEL_KANTE,
  text: '#E7EAEF',
  textMatt: '#98A1AE',
  // Vorlage: #69717E — als Schrift nur 3,17–4,02:1 (CLAUDE-design.md, Verfehlung 1).
  // Aufgehellt im selben Ton auf 4,54:1 gegen „Platte hoch", die hellste dunkle Flaeche.
  textSchwach: '#838B97',
  kupfer: DUNKEL_KUPFER,
  kupferHell: '#E3A26C',
  kupferSchimmer: DUNKEL_KUPFER_SCHIMMER,
  // Vorlage `.taste-kupfer`: weisse Schrift ergaebe hier nur 2,82:1 (CLAUDE-design.md,
  // Verfehlung 3). Dunkel traegt die Kupfertaste deshalb die Grundtinte — 6,75:1.
  kupferSchrift: DUNKEL_GRUND,
  gruen: '#46C46F',
  bernst: '#E0AE49',
  zinnob: '#F0575C',
  stahl: '#5B96F0',
  grau: '#6E7681',
  grundVerlauf: grundVerlauf(DUNKEL_KUPFER_SCHIMMER, DUNKEL_GRUND),
};

export const SCHATTEN: {
  readonly hell: KupferwarteSchatten;
  readonly dunkel: KupferwarteSchatten;
} = {
  hell: {
    nute: `0 2px 5px rgba(18,24,33,.14) inset, 0 -1px 0 ${HELL_KANTE} inset`,
    platte: `0 1px 0 ${HELL_KANTE} inset, 0 1px 2px rgba(18,24,33,.10), 0 10px 24px -14px rgba(18,24,33,.35)`,
    hoch: `0 1px 0 ${HELL_KANTE} inset, 0 2px 4px rgba(18,24,33,.10), 0 18px 34px -16px rgba(18,24,33,.42)`,
    taste: `0 1px 0 ${HELL_KANTE} inset, 0 1px 2px rgba(18,24,33,.18)`,
  },
  dunkel: {
    nute: '0 3px 7px rgba(0,0,0,.6) inset, 0 -1px 0 rgba(255,255,255,.05) inset',
    platte: `0 1px 0 ${DUNKEL_KANTE} inset, 0 1px 2px rgba(0,0,0,.5), 0 12px 28px -16px rgba(0,0,0,.85)`,
    hoch: '0 1px 0 rgba(255,255,255,.11) inset, 0 2px 6px rgba(0,0,0,.55), 0 22px 40px -18px rgba(0,0,0,.95)',
    taste: '0 1px 0 rgba(255,255,255,.08) inset, 0 1px 2px rgba(0,0,0,.6)',
  },
};

export const KUPFERWARTE = { hell: hellFarben, dunkel: dunkelFarben } as const;

/** Ueberschriften der Vorlage: Archivo mit gedehnter Breite. */
const ueberschrift = { fontFamily: ARCHIVO, fontStretch: '112%', textWrap: 'balance' } as const;

function palette(farben: KupferwarteFarben, schatten: KupferwarteSchatten) {
  return {
    primary: { main: farben.kupfer, light: farben.kupferHell, contrastText: farben.kupferSchrift },
    background: { default: farben.grund, paper: farben.platte },
    text: { primary: farben.text, secondary: farben.textMatt, disabled: farben.textSchwach },
    divider: farben.rand,
    success: { main: farben.gruen },
    warning: { main: farben.bernst },
    error: { main: farben.zinnob },
    info: { main: farben.stahl },
    kupferwarte: { ...farben, schatten, monoFontFamily: PLEX_MONO },
  };
}

export const theme = createTheme({
  // Zwei Erscheinungsbilder, kein Schalter: die dunklen Werte stehen unter
  // @media (prefers-color-scheme: dark) (CLAUDE-design.md, „Erscheinungsbilder").
  cssVariables: { colorSchemeSelector: 'media', cssVarPrefix: 'fb' },
  colorSchemes: {
    light: { palette: palette(hellFarben, SCHATTEN.hell) },
    dark: { palette: palette(dunkelFarben, SCHATTEN.dunkel) },
  },
  shape: { borderRadius: CONTROL_RADIUS },
  breakpoints: {
    // Unterhalb von 760 px liegt die Schiene hinter einer Schaltflaeche (E14); die uebrigen
    // Umbruchpunkte bleiben bei den MUI-Vorgaben.
    values: { xs: 0, sm: 760, md: 900, lg: 1200, xl: 1536 },
  },
  typography: {
    fontFamily: PLEX_SANS,
    fontSize: 14,
    h1: ueberschrift,
    h2: ueberschrift,
    h3: ueberschrift,
    h4: ueberschrift,
    h5: ueberschrift,
    h6: ueberschrift,
    button: { fontFamily: PLEX_SANS, fontWeight: 600, textTransform: 'none' },
    // Etikett (Vorlage Z. 185–194): Archivo, 10 px, 600, Versalien, Laufweite 0,14 em.
    overline: {
      fontFamily: ARCHIVO,
      fontStretch: '118%',
      fontSize: 10,
      fontWeight: 600,
      letterSpacing: '.14em',
      textTransform: 'uppercase',
      lineHeight: 1,
    },
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: {
          background: hellFarben.grundVerlauf,
          lineHeight: 1.5,
          WebkitFontSmoothing: 'antialiased',
          '@media (prefers-color-scheme: dark)': {
            background: dunkelFarben.grundVerlauf,
          },
        },
        // Zahlen stehen untereinander in einer Spalte (CLAUDE-design.md, Typografie).
        'code, kbd, samp, .mono': {
          fontFamily: PLEX_MONO,
          fontVariantNumeric: 'tabular-nums',
        },
        // Fokusring: 2 px Kupfer mit 2 px Abstand an jedem Tastaturziel.
        ':is(button, a, [tabindex]):focus-visible': {
          outline: `${FOCUS_RING_WIDTH}px solid var(--fb-palette-primary-main)`,
          outlineOffset: `${FOCUS_RING_OFFSET}px`,
          borderRadius: CONTROL_RADIUS,
        },
        // Bewegung reduzieren: eine zentrale Regel, auch fuer das Pulsieren der LED
        // (Vorlage Z. 1093–1096).
        '@media (prefers-reduced-motion: reduce)': {
          '*, *::before, *::after': {
            animationDuration: '0s !important',
            animationIterationCount: '1 !important',
            transitionDuration: '0s !important',
            scrollBehavior: 'auto !important',
          },
        },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: { backgroundImage: 'none', borderRadius: PANEL_RADIUS },
      },
    },
  },
});
