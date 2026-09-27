import { apiJson } from './client';
import { inHundertsteln, liste, objekt, text, textOderNull, zahl, zahlOderNull } from './verengen';

/**
 * Der Weg zur Auswertung „Pipeline" (Kriterien 23, 24, 26).
 *
 * Die Typen sind die Gegenstuecke zu `PipelineResponse` und `PipelineZeileResponse` im Backend;
 * aendert sich dort ein Feld, aendert es sich hier mit (CLAUDE-react.md). Die Antwort geht durch
 * einen Parser: Was ueber das Netz kommt, ist `unknown`, bis es geprueft ist — kein `as`.
 *
 * <b>Geld traegt hier andere Namen als in der Antwort</b>, weil es eine andere Einheit traegt: Die
 * Antwort schickt `summe` und `gewichteteSumme` als Dezimalzahl, hier stehen sie als ganze Cent
 * (E5) — dieselbe Regel und dieselbe Begruendung wie in `api/angebote.ts`, und mit
 * {@link inHundertsteln} auch dieselbe Pruefung.
 *
 * <b>Die beiden Summen kommen vom Server</b> und werden nicht aus den Zeilen addiert: Die
 * Rundungsregel steht im Backend (E20), und sie hier ein zweites Mal zu schreiben liesse beide
 * Rechnungen auseinanderlaufen.
 *
 * <b>`wahrscheinlichkeit === null` ist die Kennzeichnung „nicht eingeschaetzt"</b> (F2). Es gibt
 * kein zweites Feld daneben — zwei Quellen fuer eine Aussage driften auseinander. Die Ansicht macht
 * daraus ein Wort in der Zeile.
 */

/** Eine Zeile der Pipeline: ein offenes Angebot mit seinem Vorgang (Kriterium 23). */
export interface PipelineZeile {
  readonly angebotId: number;
  /** Angebotsnummer; in der Pipeline steht nur Versendetes, und das traegt seine Nummer. */
  readonly nummer: string;
  /** Technische Id des Vorgangs — daran haengt der Sprung aus der Zeile. */
  readonly vorgangId: number;
  /** Fortlaufende Vorgangsnummer als Zahl; das `#` setzt die Oberflaeche. */
  readonly vorgangNummer: number;
  readonly vorgangTitel: string;
  readonly firma: string;
  /** Netto-Summe des Angebots in ganzen Cent. */
  readonly summeInCent: number;
  /** Abschlusswahrscheinlichkeit in Prozent, oder `null` fuer „nicht eingeschaetzt" (F2). */
  readonly wahrscheinlichkeit: number | null;
  /** Gewichtete Summe in ganzen Cent, vom Server gerechnet (E20). */
  readonly gewichteteSummeInCent: number;
  /** Erwarteter Entscheidungszeitpunkt als Tag (`YYYY-MM-DD`), oder `null`. */
  readonly entscheidungErwartetAm: string | null;
}

/** Die Auswertung als Ganzes: die offenen Angebote und die beiden Summen (Kriterium 24). */
export interface Pipeline {
  readonly zeilen: readonly PipelineZeile[];
  /** Ungewichtete Netto-Summe in ganzen Cent. */
  readonly summeInCent: number;
  /** Gewichtete Pipeline in ganzen Cent. */
  readonly gewichteteSummeInCent: number;
}

function parseZeile(wert: unknown): PipelineZeile {
  const zeile = objekt(wert);
  return {
    angebotId: zahl(zeile.angebotId),
    nummer: text(zeile.nummer),
    vorgangId: zahl(zeile.vorgangId),
    vorgangNummer: zahl(zeile.vorgangNummer),
    vorgangTitel: text(zeile.vorgangTitel),
    firma: text(zeile.firma),
    summeInCent: inHundertsteln(zeile.summe),
    wahrscheinlichkeit: zahlOderNull(zeile.wahrscheinlichkeit),
    gewichteteSummeInCent: inHundertsteln(zeile.gewichteteSumme),
    entscheidungErwartetAm: textOderNull(zeile.entscheidungErwartetAm),
  };
}

/** Verengt die Auswertung oder scheitert. */
export function parsePipeline(wert: unknown): Pipeline {
  const antwort = objekt(wert);
  return {
    zeilen: liste(antwort.zeilen).map(parseZeile),
    summeInCent: inHundertsteln(antwort.summe),
    gewichteteSummeInCent: inHundertsteln(antwort.gewichteteSumme),
  };
}

/**
 * Die offenen Angebote mit der ungewichteten und der gewichteten Summe (Kriterien 23, 24).
 *
 * Ohne Parameter: Die Pipeline zeigt immer alles Offene. Das Abbruchsignal nimmt sie trotzdem — die
 * Ansicht laesst einen laufenden Aufruf fallen, wenn sie verlassen wird.
 */
export function pipeline(signal?: AbortSignal): Promise<Pipeline> {
  return apiJson('/api/pipeline', { methode: 'GET', signal }, parsePipeline);
}
