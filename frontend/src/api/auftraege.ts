import { apiJson, apiOhneInhalt } from './client';
import type { Abrechnungsmodus, Einheit } from './angebote';
import {
  FORMFEHLER,
  inHundertsteln,
  jaNein,
  liste,
  objekt,
  text,
  textOderNull,
  zahl,
} from './verengen';
import type { Auftragsstatus } from '../lib/auftragsstatus';

/**
 * Die Wege zum Auftrag: die Auskunft am Angebot, das Anlegen, das Lesen, die Pflege, das Loeschen
 * und die Liste am Vorgang (Plan #112, E3, E7, E8, E15).
 *
 * Die Typen sind die Gegenstuecke zu `AuftragResponse`, `AuftragPositionResponse`,
 * `AuftragZeileResponse`, `VorgangAuftraegeResponse` und `AngebotAuftragResponse` im Backend;
 * aendert sich dort ein Feld, aendert es sich hier mit (CLAUDE-react.md). Jede Antwort geht durch
 * einen Parser — was ueber das Netz kommt, ist `unknown`, bis es geprueft ist, kein `as`.
 *
 * <b>Geld und Menge</b> stehen wie in `api/angebote.ts` als ganze Zahl — die Menge und die Stunden
 * je Personentag in Hundertsteln, das Geld in Cent — und tragen deshalb andere Namen als in der
 * Antwort. Hinaus gehen sie als Dezimaltext mit Punkt.
 *
 * <b>Die Eingabe des Anlegens kennt keinen Preis</b> (Plan E7): Je uebernommener Position gehen nur
 * der Platz im Angebot, die vereinbarte Menge und die Stunden je Personentag hinaus. Bezeichnung,
 * Abrechnungsmodus, Einheit und Einzelpreis liest das Backend aus dem Angebot — was der Absender
 * nicht aendern darf, kommt im Rumpf gar nicht erst vor.
 */

/** Eine Position des Auftrags, wie die Ansicht sie zeigt (Kriterien 2, 4, 5). */
export interface AuftragPosition {
  readonly bezeichnung: string;
  readonly abrechnungsmodus: Abrechnungsmodus;
  /** Vereinbarte Menge in ganzen Hundertsteln. */
  readonly mengeInHundertsteln: number;
  readonly einheit: Einheit;
  /** Netto-Einzelpreis in ganzen Cent. */
  readonly einzelpreisInCent: number;
  /** Stunden je Personentag in Hundertsteln — `null` an einer Festpreisposition (Kriterium 4). */
  readonly stundenJePersonentagInHundertsteln: number | null;
  /** Netto-Betrag der Position in ganzen Cent, vom Server gerechnet (Kriterium 5). */
  readonly betragInCent: number;
}

/** Ein Auftrag mit seinen Positionen (Kriterium 3). */
export interface Auftrag {
  readonly id: number;
  readonly vorgangId: number;
  readonly angebotId: number;
  /** Die Nummer des Angebots, aus dem der Auftrag entstand — fuer den Verweis „Angebot A-…". */
  readonly angebotNummer: string | null;
  readonly nummer: string;
  readonly status: Auftragsstatus;
  /** Tag (`YYYY-MM-DD`), wie das Backend ein `LocalDate` liefert. */
  readonly auftragDatum: string;
  readonly kundenbestellnummer: string | null;
  readonly leistungAb: string | null;
  readonly leistungBis: string | null;
  readonly positionen: readonly AuftragPosition[];
  /** Netto-Summe in ganzen Cent, vom Server gerechnet (Kriterium 5). */
  readonly summeInCent: number;
}

/** Eine Zeile der Auftragsliste am Vorgang (Kriterium 9). */
export interface AuftragZeile {
  readonly id: number;
  readonly nummer: string;
  readonly status: Auftragsstatus;
  readonly auftragDatum: string;
  readonly leistungAb: string | null;
  readonly leistungBis: string | null;
  readonly summeInCent: number;
}

/** Die Auftraege eines Vorgangs, der juengste oben (Kriterium 9, Plan E13). */
export interface VorgangAuftraege {
  readonly auftraege: readonly AuftragZeile[];
}

/** Was die Angebotsansicht ueber den Auftrag wissen muss (Kriterium 1, F9, Plan E3). */
export interface AngebotAuftrag {
  /** Der Auftrag zu diesem Angebot, oder `null`, solange keiner besteht. */
  readonly auftrag: Auftrag | null;
  /** Ob Angebotszustand und Abschlussstand des Vorgangs das Anlegen heute zulassen. */
  readonly anlegbar: boolean;
}

/** Eine uebernommene Position, wie die Maske sie einreicht — die Felder von `AuftragPositionRequest`. */
export interface AuftragPositionwahl {
  /** Die Stelle im Angebot, 1-basiert: `index + 1` der Liste in der Angebotsantwort. */
  readonly platz: number;
  /** Vereinbarte Menge als Dezimaltext mit Punkt — „2.50". */
  readonly menge: string;
  /** Stunden je Personentag als Dezimaltext mit Punkt, oder `null` an einer Festpreisposition. */
  readonly stundenJePersonentag: string | null;
}

/**
 * Die Eingabe des Anlegens — die Felder von `AuftragAnlegenRequest` (Plan E7).
 *
 * `auftragDatum` fehlt, wenn das Feld leer bleibt: Dann setzt das Backend den heutigen Tag in der
 * Geschaeftszone. Ein im Browser gesetzter Tag waere zwischen 22:00 UTC und Mitternacht der falsche.
 */
export interface AuftragAnlegenEingabe {
  readonly auftragDatum?: string;
  readonly kundenbestellnummer: string | null;
  readonly leistungAb: string | null;
  readonly leistungBis: string | null;
  readonly positionen: readonly AuftragPositionwahl[];
}

/** Die Eingabe der Pflege — die Felder von `AuftragPflegeRequest` (Kriterium 7, Plan E8). */
export interface AuftragPflegeEingabe {
  readonly auftragDatum: string;
  readonly kundenbestellnummer: string | null;
  readonly leistungAb: string | null;
  readonly leistungBis: string | null;
  readonly status: Auftragsstatus;
}

function auftragsstatus(wert: unknown): Auftragsstatus {
  if (wert !== 'OFFEN' && wert !== 'IN_ARBEIT' && wert !== 'ABGESCHLOSSEN') {
    throw new TypeError(FORMFEHLER);
  }
  return wert;
}

function abrechnungsmodus(wert: unknown): Abrechnungsmodus {
  if (wert !== 'AUFWAND' && wert !== 'FESTPREIS') {
    throw new TypeError(FORMFEHLER);
  }
  return wert;
}

function einheit(wert: unknown): Einheit {
  if (wert !== 'STUNDE' && wert !== 'PERSONENTAG' && wert !== 'PAUSCHAL') {
    throw new TypeError(FORMFEHLER);
  }
  return wert;
}

/** Eine Dezimalzahl, die fehlen darf — dann steht dort `null`, nie eine Null. */
function inHundertstelnOderNull(wert: unknown): number | null {
  return wert === null ? null : inHundertsteln(wert);
}

function parsePosition(wert: unknown): AuftragPosition {
  const position = objekt(wert);
  return {
    bezeichnung: text(position.bezeichnung),
    abrechnungsmodus: abrechnungsmodus(position.abrechnungsmodus),
    mengeInHundertsteln: inHundertsteln(position.menge),
    einheit: einheit(position.einheit),
    einzelpreisInCent: inHundertsteln(position.einzelpreis),
    stundenJePersonentagInHundertsteln: inHundertstelnOderNull(position.stundenJePersonentag),
    betragInCent: inHundertsteln(position.betrag),
  };
}

/** Verengt einen Auftrag samt Positionen oder scheitert. */
export function parseAuftrag(wert: unknown): Auftrag {
  const auftrag = objekt(wert);
  return {
    id: zahl(auftrag.id),
    vorgangId: zahl(auftrag.vorgangId),
    angebotId: zahl(auftrag.angebotId),
    angebotNummer: textOderNull(auftrag.angebotNummer),
    nummer: text(auftrag.nummer),
    status: auftragsstatus(auftrag.status),
    auftragDatum: text(auftrag.auftragDatum),
    kundenbestellnummer: textOderNull(auftrag.kundenbestellnummer),
    leistungAb: textOderNull(auftrag.leistungAb),
    leistungBis: textOderNull(auftrag.leistungBis),
    positionen: liste(auftrag.positionen).map(parsePosition),
    summeInCent: inHundertsteln(auftrag.summe),
  };
}

function parseZeile(wert: unknown): AuftragZeile {
  const zeile = objekt(wert);
  return {
    id: zahl(zeile.id),
    nummer: text(zeile.nummer),
    status: auftragsstatus(zeile.status),
    auftragDatum: text(zeile.auftragDatum),
    leistungAb: textOderNull(zeile.leistungAb),
    leistungBis: textOderNull(zeile.leistungBis),
    summeInCent: inHundertsteln(zeile.summe),
  };
}

/** Verengt die Auftragsliste eines Vorgangs oder scheitert. */
export function parseVorgangAuftraege(wert: unknown): VorgangAuftraege {
  const antwort = objekt(wert);
  return { auftraege: liste(antwort.auftraege).map(parseZeile) };
}

/** Verengt die Auskunft am Angebot oder scheitert. */
export function parseAngebotAuftrag(wert: unknown): AngebotAuftrag {
  const antwort = objekt(wert);
  return {
    auftrag: antwort.auftrag === null ? null : parseAuftrag(antwort.auftrag),
    anlegbar: jaNein(antwort.anlegbar),
  };
}

/** Ob zu diesem Angebot ein Auftrag besteht und ob einer angelegt werden darf (Plan E3). */
export function auftragAmAngebot(angebotId: number): Promise<AngebotAuftrag> {
  return apiJson(
    `/api/angebote/${String(angebotId)}/auftrag`,
    { methode: 'GET' },
    parseAngebotAuftrag,
  );
}

/** Legt aus dem angenommenen Angebot den Auftrag an (Kriterien 1 bis 4, Plan E7). */
export function auftragAnlegen(angebotId: number, eingabe: AuftragAnlegenEingabe): Promise<Auftrag> {
  return apiJson(
    `/api/angebote/${String(angebotId)}/auftrag`,
    { methode: 'POST', rumpf: eingabe },
    parseAuftrag,
  );
}

/** Liest einen Auftrag samt Positionen (Kriterium 3). */
export function auftragLesen(id: number): Promise<Auftrag> {
  return apiJson(`/api/auftraege/${String(id)}`, { methode: 'GET' }, parseAuftrag);
}

/** Schreibt Datum, Bestellnummer, Leistungszeitraum und Status als Ganzes (Kriterium 7, Plan E8). */
export function auftragPflegen(id: number, eingabe: AuftragPflegeEingabe): Promise<Auftrag> {
  return apiJson(`/api/auftraege/${String(id)}`, { methode: 'PUT', rumpf: eingabe }, parseAuftrag);
}

/** Loescht den Auftrag samt Positionen (Kriterium 15, Plan E15); die Antwort traegt nichts. */
export function auftragLoeschen(id: number): Promise<void> {
  return apiOhneInhalt(`/api/auftraege/${String(id)}`, { methode: 'DELETE' });
}

/** Die Auftraege des Vorgangs, der juengste oben (Kriterium 9). */
export function auftraegeDesVorgangs(vorgangId: number): Promise<VorgangAuftraege> {
  return apiJson(
    `/api/vorgaenge/${String(vorgangId)}/auftraege`,
    { methode: 'GET' },
    parseVorgangAuftraege,
  );
}
