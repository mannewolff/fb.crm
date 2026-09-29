import { createTheme } from '@mui/material/styles';

/**
 * Kupferwolke — die einzige Wertequelle des Erscheinungsbildes.
 *
 * Alle Werte stammen aus der Palettentabelle in `CLAUDE-design.md` und aus der verbindlichen
 * Vorlage `docs/entwurf-kupferwolke.html` (`:root` Z. 10–21, `body` Z. 22–27,
 * `:focus-visible` Z. 103, `prefers-reduced-motion` Z. 104). Weichen Vorlage und Designquelle
 * voneinander ab, gilt die Designquelle — sie traegt die auf AA vertieften Toene.
 *
 * **Ein Erscheinungsbild: hell** (CLAUDE-design.md, „Erscheinungsbild", Issue #34). Es gibt kein
 * zweites Farbschema und kein CSS unter `prefers-color-scheme`; `theme.test.ts` haelt beides fest.
 *
 * **Tokens sind Verweise, keine Werte.** Ansichten lesen Flaechen, Linien, Toenungen und Melder
 * ueber `theme.vars.palette.kupferwolke.*`.
 */

/** Karte, Kopfkarte, Schiene. */
export const RADIUS_GROSS = 28;
/** Kachel, Fuss der Schiene. */
export const RADIUS_KACHEL = 24;
/** Innenkarte, Hinzufuegen-Kachel, Menue, Dialog. */
export const RADIUS_MITTEL = 22;
/** Das Mal einer Firma (84 px) — das abgerundete Quadrat neben dem Kreis der Personen. */
export const RADIUS_MAL = 26;
/**
 * Das Symbolfeld eines Zeitleisten-Eintrags (36 px).
 *
 * CLAUDE-design.md nennt den Wert zweimal: in der Radien-Tabelle als Spanne „14–16 px
 * (Symbolfelder 36–48 px)" und im Baustein „Zeitleiste" ausdruecklich mit 12 px. Die genauere
 * Angabe gilt — die Vorlage traegt denselben Wert (`.zeit .punkt` Z. 97).
 */
export const RADIUS_SYMBOL = 12;
/** Navigationseintrag, Zeile, Eingabefeld. */
export const RADIUS_KLEIN = 14;
/** Tasten, Chips, Suche, Zaehler — die runde Form. */
export const RADIUS_RUND = 999;

/** Fokusring: 2 px Kupfer mit 2 px Abstand (Vorlage Z. 103). */
export const FOCUS_RING_WIDTH = 2;
export const FOCUS_RING_OFFSET = 2;

/**
 * Die Klasse, die Zahlen untereinander stellt (CLAUDE-design.md, Typografie).
 *
 * Plus Jakarta Sans fuehrt die Tabellenziffern selbst (`tnum`) — es braucht keine zweite Schrift
 * mehr, nur noch `font-variant-numeric`. Die Regel steht zentral in `MuiCssBaseline`, damit keine
 * Ansicht sie abschreibt.
 */
export const ZAHLEN_KLASSE = 'zahlen';

/** Eine Pastellflaeche mit der Schrift, die auf ihr steht. */
export interface Toenung {
  readonly flaeche: string;
  readonly schrift: string;
}

/**
 * Die sechs Toenungen. Jede traegt eine feste Bedeutung (CLAUDE-design.md, „Toenungen") — sie
 * werden nicht der Reihe nach durchgefaerbt.
 */
export interface KupferwolkeToenungen {
  /** Kupfer-Familie: aktiver Navigationseintrag, Firmen-Mal, Hover der Icontaste, Vorgaenge. */
  readonly pfirsich: Toenung;
  /** erfolgreich, aktiv, bezahlt, Umsatz. */
  readonly salbei: Toenung;
  /** Information, laufend, versendet. */
  readonly himmel: Toenung;
  /** Warnung, Grenze erreicht, bald faellig. */
  readonly bernstein: Toenung;
  /** gescheitert, ueberfaellig, stillgelegt. */
  readonly rose: Toenung;
  /** neutrale Kategorie: Personen, Ansprechpartner, Rechnungen als Menge. */
  readonly flieder: Toenung;
}

/**
 * Der Name einer Toenung — die Wahl, die ein Baustein von seinem Aufrufer annimmt.
 *
 * Bausteine nehmen den **Namen**, nicht das Farbpaar: So bleibt die Bedeutung („Rose heisst
 * stillgelegt") im Aufruf lesbar, und kein Aufrufer kann eine Flaeche mit der Schrift einer
 * anderen Toenung paaren.
 */
export type ToenungName = keyof KupferwolkeToenungen;

/**
 * Kraeftige Zustandsfarben fuer Symbole, Zahlen und schmale Markierungen.
 *
 * Sie stehen **auf Flaechen**, nie direkt auf dem Grund: Stahl und Grau verfehlen dort AA
 * (CLAUDE-design.md, „Bekannte Grenzen").
 */
export interface KupferwolkeMelder {
  readonly gruen: string;
  readonly bernstein: string;
  readonly zinnober: string;
  readonly stahl: string;
  readonly grau: string;
}

/** Drei Tiefenstufen: Grund < Karte < Abgehoben. Eingelassene Flaechen gibt es nicht mehr. */
export interface KupferwolkeSchatten {
  /** Karte, Schiene, Suche, runde Kopftasten. */
  readonly karte: string;
  /** Innenkarte im Hover, Menue, Dialog. */
  readonly hoch: string;
  /** Kupfertaste und Markenmal — der einzige farbige Schatten. */
  readonly kupfer: string;
}

/** Die Rollen der Designquelle, die keine MUI-Rolle haben. */
export interface KupferwolkeFarben {
  /** Grund der Anwendung, warmes Cremeweiss. */
  readonly grund: string;
  /** Karten, Schiene, Suche, Menues. */
  readonly flaeche: string;
  /** Innenkarten, weiche Tasten, Icontasten, Zaehler, Hover-Grund von Zeilen. */
  readonly flaecheWeich: string;
  /** Seltene Haarlinien — rein gliedernd, traegt nie eine Aussage. */
  readonly linie: string;
  /** Rand von Eingabefeldern und allem, dessen Umriss man erkennen muss (>= 3:1). */
  readonly randStark: string;
  readonly text: string;
  readonly textMatt: string;
  readonly textSchwach: string;
  /** Leitfarbe: Links, Fokusring, kupferne Schrift, aktive Zustaende. */
  readonly kupfer: string;
  /** Das helle Ende des Verlaufs der Kupfertaste. */
  readonly kupferTaste: string;
  /** Das tiefe Ende des Verlaufs der Kupfertaste. */
  readonly kupferTief: string;
  /** **Nur Schmuck** (2,65:1 gegen Weiss) — nie Schrift, nie Grund von Schrift. */
  readonly kupferGlanz: string;
  /** Der farbige Schatten der Kupfertaste und des Markenmals. */
  readonly kupferSchatten: string;
  /** Schrift auf der Kupfertaste. */
  readonly kupferSchrift: string;
  /** Der Grund der Anwendung: Cremeweiss mit Kupfer-Schimmer links und Flieder-Schimmer rechts. */
  readonly grundVerlauf: string;
  readonly toenung: KupferwolkeToenungen;
  readonly melder: KupferwolkeMelder;
}

export interface KupferwolkePalette extends KupferwolkeFarben {
  readonly schatten: KupferwolkeSchatten;
}

declare module '@mui/material/styles' {
  interface Palette {
    kupferwolke: KupferwolkePalette;
  }
  interface PaletteOptions {
    kupferwolke: KupferwolkePalette;
  }
  // Blendet die Felder der CSS-Variablen (`vars`, `cssVarPrefix`, `colorSchemeSelector`)
  // am Theme-Typ ein. MUI 6 zeigt sie nur bei dieser Augmentation; ohne sie traegt das
  // Theme die Werte zur Laufzeit, der Typ kennt sie aber nicht.
  interface CssThemeVariables {
    enabled: true;
  }
}

const GRUND = '#F6F3EF';
const KUPFER_GLANZ = '#E08A4F';
const KUPFER_SCHATTEN = 'rgba(184,97,42,.55)';

/** Eine Schrift: Plus Jakarta Sans (CLAUDE-design.md, Typografie). */
const JAKARTA = '"Plus Jakarta Sans Variable", "Plus Jakarta Sans", system-ui, sans-serif';

/**
 * Der Grund traegt zwei weiche Schimmer (Vorlage Z. 22–27): Kupfer-Glanz oben links, Flieder
 * oben rechts. Beide sind Schmuck und tragen nie Schrift.
 */
const GRUND_VERLAUF = [
  'radial-gradient(900px 500px at 12% -10%, rgba(224,138,79,.18), transparent 60%)',
  'radial-gradient(700px 500px at 100% 0%, rgba(160,150,240,.12), transparent 60%)',
  GRUND,
].join(', ');

const farben: KupferwolkeFarben = {
  grund: GRUND,
  flaeche: '#FFFFFF',
  flaecheWeich: '#FBF9F6',
  linie: '#EFE9E3',
  randStark: '#928577',
  text: '#1F1B18',
  textMatt: '#6B625B',
  textSchwach: '#756C64',
  kupfer: '#A0521F',
  kupferTaste: '#AE5A24',
  kupferTief: '#8E4718',
  kupferGlanz: KUPFER_GLANZ,
  kupferSchatten: KUPFER_SCHATTEN,
  kupferSchrift: '#FFFFFF',
  grundVerlauf: GRUND_VERLAUF,
  toenung: {
    pfirsich: { flaeche: '#FDEBDD', schrift: '#8A4418' },
    salbei: { flaeche: '#E4F1E8', schrift: '#2E6B45' },
    himmel: { flaeche: '#E3EFFB', schrift: '#1F5A96' },
    bernstein: { flaeche: '#FBF0D9', schrift: '#7A5510' },
    rose: { flaeche: '#FBE4E4', schrift: '#A12D31' },
    flieder: { flaeche: '#ECEAFB', schrift: '#4B3FA0' },
  },
  melder: {
    gruen: '#277A42',
    bernstein: '#8F6410',
    zinnober: '#C8393E',
    stahl: '#2F6FC9',
    grau: '#6B737F',
  },
};

/** Die Schattenfarbe ist ein warmes Braun — kein Blaugrau, kein Schwarz. */
export const SCHATTEN: KupferwolkeSchatten = {
  karte: '0 1px 2px rgba(80,50,30,.04), 0 8px 24px -6px rgba(80,50,30,.10)',
  hoch: '0 2px 4px rgba(80,50,30,.05), 0 18px 40px -10px rgba(80,50,30,.18)',
  kupfer: `0 8px 20px -6px ${KUPFER_SCHATTEN}`,
};

export const KUPFERWOLKE: KupferwolkeFarben = farben;

function palette() {
  return {
    primary: {
      main: farben.kupfer,
      light: farben.kupferTaste,
      dark: farben.kupferTief,
      contrastText: farben.kupferSchrift,
    },
    background: { default: farben.grund, paper: farben.flaeche },
    text: { primary: farben.text, secondary: farben.textMatt, disabled: farben.textSchwach },
    divider: farben.linie,
    success: { main: farben.melder.gruen },
    warning: { main: farben.melder.bernstein },
    error: { main: farben.melder.zinnober },
    info: { main: farben.melder.stahl },
    kupferwolke: { ...farben, schatten: SCHATTEN },
  };
}

export const theme = createTheme({
  // Ein Erscheinungsbild, hell (CLAUDE-design.md, „Erscheinungsbild"). Ohne ein zweites
  // Farbschema erzeugt MUI kein CSS unter prefers-color-scheme — die Oberflaeche bleibt hell,
  // gleich was der Rechner einstellt.
  cssVariables: { cssVarPrefix: 'fb' },
  colorSchemes: {
    light: { palette: palette() },
  },
  shape: { borderRadius: RADIUS_KLEIN },
  breakpoints: {
    // Unterhalb von 760 px entfaellt die Schiene (E14); die uebrigen Umbruchpunkte bleiben bei
    // den MUI-Vorgaben.
    values: { xs: 0, sm: 760, md: 900, lg: 1200, xl: 1536 },
  },
  typography: {
    fontFamily: JAKARTA,
    fontSize: 14.5,
    // Ansichtstitel: Name in der Kopfkarte.
    h1: { fontSize: 28, fontWeight: 800, letterSpacing: '-.02em', textWrap: 'balance' },
    // Kartentitel.
    h2: { fontSize: 18, fontWeight: 700, textWrap: 'balance' },
    // Markenname „fb.crm" in der Schiene.
    h3: { fontSize: 17, fontWeight: 800 },
    // Name in Liste: Person, Firma, Vorgang in Karten und Zeilen.
    h4: { fontSize: 15, fontWeight: 700 },
    h5: { fontSize: 15, fontWeight: 700 },
    h6: { fontSize: 15, fontWeight: 700 },
    body1: { fontSize: 14.5, fontWeight: 400, lineHeight: 1.5 },
    body2: { fontSize: 13.5, fontWeight: 500 },
    caption: { fontSize: 12.5, fontWeight: 600 },
    button: { fontSize: 14, fontWeight: 600, textTransform: 'none' },
    // Gruppentitel der Schiene: Satzschreibung, keine Versalien mit Laufweite
    // (CLAUDE-design.md, Typografie).
    overline: {
      fontSize: 12,
      fontWeight: 600,
      letterSpacing: 'normal',
      textTransform: 'none',
      lineHeight: 1.4,
    },
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: {
          background: GRUND_VERLAUF,
          minHeight: '100vh',
          lineHeight: 1.5,
          WebkitFontSmoothing: 'antialiased',
        },
        // Zahlen stehen untereinander in einer Spalte (CLAUDE-design.md, Typografie).
        [`.${ZAHLEN_KLASSE}`]: { fontVariantNumeric: 'tabular-nums' },
        // Fokusring: 2 px Kupfer mit 2 px Abstand an jedem Tastaturziel. Den Radius nimmt der
        // Umriss vom Element selbst — er wird hier nicht gesetzt, sonst veraenderte die Regel
        // die Form des Elements statt nur seinen Ring.
        ':is(button, a, [tabindex]):focus-visible': {
          outline: `${FOCUS_RING_WIDTH}px solid var(--fb-palette-primary-main)`,
          outlineOffset: `${FOCUS_RING_OFFSET}px`,
        },
        // Bewegung reduzieren: eine zentrale Regel, auch fuer das Anheben im Hover
        // (Vorlage Z. 104).
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
        root: { backgroundImage: 'none', borderRadius: RADIUS_GROSS },
      },
    },
    // Eingabefeld: weisse Flaeche, 1 px Rand stark, Radius klein, Fokus als 2 px Kupferrand
    // (CLAUDE-design.md, „Eingabefelder").
    MuiOutlinedInput: {
      styleOverrides: {
        root: ({ theme: t }) => ({
          borderRadius: RADIUS_KLEIN,
          backgroundColor: t.vars.palette.kupferwolke.flaeche,
          '& .MuiOutlinedInput-notchedOutline': {
            borderWidth: 1,
            borderColor: t.vars.palette.kupferwolke.randStark,
          },
          '&:hover .MuiOutlinedInput-notchedOutline': {
            borderColor: t.vars.palette.kupferwolke.randStark,
          },
          '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
            borderWidth: FOCUS_RING_WIDTH,
            borderColor: t.vars.palette.kupferwolke.kupfer,
          },
          '&.Mui-error .MuiOutlinedInput-notchedOutline': {
            borderColor: t.vars.palette.kupferwolke.melder.zinnober,
          },
        }),
      },
    },
    // Was ueber allem liegt, hebt sich weiter ab (CLAUDE-design.md, „Tiefe").
    MuiMenu: {
      styleOverrides: {
        paper: ({ theme: t }) => ({
          borderRadius: RADIUS_MITTEL,
          boxShadow: t.vars.palette.kupferwolke.schatten.hoch,
        }),
      },
    },
    MuiPopover: {
      styleOverrides: {
        paper: ({ theme: t }) => ({
          borderRadius: RADIUS_MITTEL,
          boxShadow: t.vars.palette.kupferwolke.schatten.hoch,
        }),
      },
    },
    MuiDialog: {
      styleOverrides: {
        paper: ({ theme: t }) => ({
          borderRadius: RADIUS_MITTEL,
          boxShadow: t.vars.palette.kupferwolke.schatten.hoch,
        }),
      },
    },
    // Tasten sind Pillen (CLAUDE-design.md, „Radien": rund).
    MuiButton: {
      styleOverrides: {
        root: { borderRadius: RADIUS_RUND },
      },
    },
  },
});
