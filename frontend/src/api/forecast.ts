import { apiJson } from './client';
import { inHundertsteln, liste, objekt, text, textOderNull, zahl } from './verengen';

/**
 * Der Forecast der Startseite: was aus Angeboten noch an Umsatz bevorsteht, je Jahr der Leistung
 * (#302; Issue #312).
 *
 * Die Typen sind die Gegenstuecke zu `ForecastResponse` samt seinen beiden inneren Records im
 * Backend; aendert sich dort ein Feld, aendert es sich hier mit (CLAUDE-react.md). Die Antwort geht
 * durch einen Parser: Was ueber das Netz kommt, ist `unknown`, bis es geprueft ist — kein `as`.
 *
 * <b>Die Betraege stehen in ganzen Cent</b> und tragen darum andere Namen als in der Antwort, aus
 * demselben Grund wie in `api/startseite.ts`: Verengt wird mit {@link inHundertsteln} ueber die
 * Ziffern, nicht ueber Gleitkomma. <b>Auch die Summe kommt aus der Antwort</b> — die Oberflaeche
 * rechnet nichts (#216).
 *
 * <b>Das Jahr steht als Zeichenkette `JJJJ`</b> (Plan #303, E9), wie die Jahre der Startseite.
 */

/** Eine offene Position unter einem Jahr (#302, Kriterium 5). */
export interface Forecastposition {
  /** Kennung des Angebots — der Weg dorthin. */
  readonly angebotId: number;
  readonly firmaName: string;
  /** Tag (`YYYY-MM-DD`), wie das Backend ein `LocalDate` liefert. */
  readonly angebotDatum: string;
  /** Titel des Angebots; `null` beim Angebot von vor #316 — dann traegt das Datum den Weg. */
  readonly angebotTitel: string | null;
  readonly bezeichnung: string;
  /** Ihr Restbetrag, netto und in ganzen Cent. */
  readonly nettoInCent: number;
}

/** Ein Jahr des Forecasts mit seinen drei Betraegen, netto und in ganzen Cent (#302, Kriterium 1). */
export interface Forecastjahr {
  /** Das Jahr der Leistung als `JJJJ`. */
  readonly jahr: string;
  /** Aus Angeboten auf „bestellt", „erledigt" und „abgerechnet". */
  readonly zugesagtInCent: number;
  /** Aus Angeboten auf „angelegt" und „abgegeben". */
  readonly angebotenInCent: number;
  /** Die Summe beider — aus der Antwort, nicht hier gerechnet. */
  readonly summeInCent: number;
  readonly positionen: readonly Forecastposition[];
}

/** Der ganze Forecast: je Jahr mit offener Position eine Zeile, aeltestes zuerst. */
export interface Forecast {
  /** Leer, wo gar keine Position offen ist (#302, Kriterium 6). */
  readonly jahre: readonly Forecastjahr[];
}

function parsePosition(wert: unknown): Forecastposition {
  const zeile = objekt(wert);
  return {
    angebotId: zahl(zeile.angebotId),
    firmaName: text(zeile.firmaName),
    angebotDatum: text(zeile.angebotDatum),
    angebotTitel: textOderNull(zeile.angebotTitel),
    bezeichnung: text(zeile.bezeichnung),
    nettoInCent: inHundertsteln(zeile.netto),
  };
}

function parseJahr(wert: unknown): Forecastjahr {
  const zeile = objekt(wert);
  return {
    jahr: text(zeile.jahr),
    zugesagtInCent: inHundertsteln(zeile.zugesagtNetto),
    angebotenInCent: inHundertsteln(zeile.angebotenNetto),
    summeInCent: inHundertsteln(zeile.summeNetto),
    positionen: liste(zeile.positionen).map(parsePosition),
  };
}

/** Verengt den Forecast samt Jahren und Positionen oder scheitert. */
export function parseForecast(wert: unknown): Forecast {
  const antwort = objekt(wert);
  return { jahre: liste(antwort.jahre).map(parseJahr) };
}

/**
 * Der Forecast, Stand von heute ueber alle Jahre.
 *
 * Ohne Parameter: Er haengt nicht an der Zeitraumwahl der Startseite (#302, Kriterium 7).
 */
export function forecast(): Promise<Forecast> {
  return apiJson('/api/startseite/forecast', { methode: 'GET' }, parseForecast);
}
