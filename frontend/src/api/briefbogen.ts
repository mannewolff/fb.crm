import { apiBlob, apiJson } from './client';
import { jaNein, objekt, text, zahl } from './verengen';

/**
 * Die fuenf Wege des Briefbogens unter `/api/briefpapier` (fachliche Quelle #361; Plan #363):
 * lesen, Schalter und Raender pflegen, das Briefpapier hochladen und entfernen, den Probedruck
 * holen.
 *
 * Der Typ ist das Gegenstueck zu `BriefbogenResponse`, die Eingabe zu `BriefbogenRequest`; aendert
 * sich dort ein Feld, aendert es sich hier mit (CLAUDE-react.md). Jede Antwort geht durch einen
 * Parser — was ueber das Netz kommt, ist `unknown`, bis es geprueft ist.
 *
 * <b>Die Raender stehen in ganzen Zehntel-Millimetern</b>, wie der Weg sie spricht (E8). In
 * Millimeter mit einer Nachkommastelle wandelt erst die Karte — die Schnittstelle kennt keine
 * Gleitkommazahl.
 *
 * Hochladen, Entfernen und Pflegen antworten mit dem ganzen neuen Stand; die Karte braucht danach
 * keinen zweiten Leseaufruf.
 *
 * **Alle drei schreibenden Wege legen den Stand vor** (#411, Kriterium 4; Plan #415, E2, E11): Der
 * Briefbogen traegt eine Version fuer die ganze Zeile. Wer Raender auf dem Stand vor einem Hochladen
 * speichert, wird mit einem Bearbeitungskonflikt abgewiesen.
 */

/** Schalter und Raender — was die Maske schickt und was jeder Stand traegt. */
export interface BriefbogenEingabe {
  /** Ob die Absenderzeile eingedruckt wird (Kriterium 7). */
  readonly absenderzeile: boolean;
  /** Ob die Steuer- und Bankzeile eingedruckt wird (Kriterium 6). */
  readonly steuerBankZeile: boolean;
  /** Linker Rand in Zehntel-Millimetern. */
  readonly links: number;
  /** Rechter Rand in Zehntel-Millimetern. */
  readonly rechts: number;
  /** Beginn des Inhalts auf Seite 1 in Zehntel-Millimetern. */
  readonly inhaltOben: number;
  /** Unterer Rand auf Seite 1 in Zehntel-Millimetern. */
  readonly unten: number;
  /** Oberer Rand auf Folgeseiten in Zehntel-Millimetern. */
  readonly folgeOben: number;
  /** Unterer Rand auf Folgeseiten in Zehntel-Millimetern. */
  readonly folgeUnten: number;
}

/** Was ueber das hinterlegte Briefpapier bekannt ist; der Schluessel im Speicher bleibt innen. */
export interface Briefpapier {
  /** Name der hochgeladenen Datei. */
  readonly dateiname: string;
  /** Groesse in Byte. */
  readonly groesse: number;
  /** Seitenzahl, 1 oder 2 (Kriterium 4). */
  readonly seiten: number;
}

/** Der Stand des Briefbogens: das hinterlegte Briefpapier, die Schalter und die Raender. */
export interface Briefbogen extends BriefbogenEingabe {
  /** Das hinterlegte Briefpapier, oder `null`, solange keines hinterlegt ist. */
  readonly papier: Briefpapier | null;
  /** Der Stand der ganzen Zeile, den jeder schreibende Weg vorlegt (Plan #415, E11). */
  readonly version: number;
}

/**
 * Die Angaben zum Briefpapier, wenn `briefpapier` wahr ist — dann muessen alle drei da sein.
 *
 * Die Antwort fuehrt sie flach und als `null` ohne Briefpapier (`BriefbogenResponse`). Hier werden
 * sie zu einem Teil, der ganz da ist oder ganz fehlt: Ein Briefpapier ohne Namen gibt es nicht,
 * und die Karte soll keinen Zweig fuer einen Fall tragen, den der Server nicht liefert.
 */
function papier(stand: Record<string, unknown>): Briefpapier | null {
  if (!jaNein(stand.briefpapier)) {
    return null;
  }
  return {
    dateiname: text(stand.dateiname),
    groesse: zahl(stand.groesse),
    seiten: zahl(stand.seiten),
  };
}

/** Verengt den Stand oder scheitert. */
export function parseBriefbogen(wert: unknown): Briefbogen {
  const stand = objekt(wert);
  return {
    papier: papier(stand),
    absenderzeile: jaNein(stand.absenderzeile),
    steuerBankZeile: jaNein(stand.steuerBankZeile),
    links: zahl(stand.links),
    rechts: zahl(stand.rechts),
    inhaltOben: zahl(stand.inhaltOben),
    unten: zahl(stand.unten),
    folgeOben: zahl(stand.folgeOben),
    folgeUnten: zahl(stand.folgeUnten),
    version: zahl(stand.version),
  };
}

const WURZEL = '/api/briefpapier';
const DATEI = `${WURZEL}/datei`;

/** Liest den Stand; auf einer frischen Instanz die Vorgaben ohne Briefpapier. */
export function briefbogenLesen(): Promise<Briefbogen> {
  return apiJson(WURZEL, { methode: 'GET' }, parseBriefbogen);
}

/**
 * Schreibt Schalter und Raender fort (Kriterien 6, 7, 8, 13). Ein Rand, der dem Inhalt keinen
 * Platz laesst, kommt als 422 mit der Meldung an seinem Feld zurueck.
 *
 * @param version der Stand, auf dem die Aenderung beruht
 */
export function briefbogenPflegen(version: number, eingabe: BriefbogenEingabe): Promise<Briefbogen> {
  return apiJson(WURZEL, { methode: 'PUT', rumpf: eingabe, version }, parseBriefbogen);
}

/**
 * Hinterlegt das Briefpapier oder ersetzt es (Kriterien 1, 2).
 *
 * Der Teil heisst `datei`, wie ihn `BriefbogenController` erwartet. Ob die Datei angenommen wird,
 * entscheidet der Server am Inhalt; eine Abweisung kommt als 422 mit der Meldung zu `datei`.
 *
 * @param version der Stand, auf dem das Hochladen beruht
 */
export function briefpapierHochladen(version: number, datei: File): Promise<Briefbogen> {
  const formular = new FormData();
  formular.append('datei', datei);
  return apiJson(DATEI, { methode: 'POST', formular, version }, parseBriefbogen);
}

/**
 * Entfernt das Briefpapier; Schalter und Raender bleiben (Kriterium 16).
 *
 * @param version der Stand, auf dem das Entfernen beruht
 */
export function briefpapierEntfernen(version: number): Promise<Briefbogen> {
  return apiJson(DATEI, { methode: 'DELETE', version }, parseBriefbogen);
}

/** Der Probedruck als PDF (Kriterium 9); ohne Briefpapier antwortet der Weg mit 404. */
export function probedruckHolen(): Promise<Blob> {
  return apiBlob(`${WURZEL}/probedruck`, { methode: 'GET' });
}
