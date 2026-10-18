/**
 * Schmaler Client der Toolbox-API fuer das Mutationsgate (Plan #348): genau die Routen, die
 * die Auswertung braucht — Karten lesen, anlegen, ergaenzen, ihren Verlauf lesen, kommentieren.
 *
 * Das Kit bringt einen eigenen Client mit, aber `.claude/kit/` ist in fb.crm nicht versioniert
 * und steht einem CI-Lauf nicht zur Verfuegung. Dieser hier folgt den Mustern des Kit-Adapters
 * und vermeidet seine bekannten Fallen:
 * - `GET /api/kanban/items` liefert ein Objekt `Spalte → Karten[]`, kein Array.
 * - Schreib- und Verlaufsrouten adressieren die interne ID `item.id`, nie die Kartennummer.
 * - Geaendert wird per `PUT` mit `{ title, body }` — das PUT koennte ein Vollersatz sein, und
 *   ohne Titel ginge er verloren.
 * - Ein `429` mit `Retry-After` wird genau einmal abgewartet und wiederholt.
 *
 * Sicherheit (E15): Das Token kommt allein aus `TBX_TOKEN`, der Host aus `toolbox.host` der
 * versionierten `.claude/workflow.config.json`. Keine Fehlermeldung enthaelt das Token oder
 * einen rohen Antworttext — das Repository ist oeffentlich, und CI-Protokolle sind lesbar.
 *
 * `fetch` und die Warte-Funktion werden eingesetzt, damit die Tests ohne Netz und ohne echtes
 * Warten laufen. Laeuft mit nichts als Node.
 */

/** Die Datei, aus der der Host stammt — sie steht in der Meldung, wenn er fehlt. */
const KONFIG_DATEI = '.claude/workflow.config.json';

const ITEMS = '/api/kanban/items';

function standardWarte(ms) {
  return new Promise((fertig) => setTimeout(fertig, ms));
}

/**
 * Die Wartezeit aus `Retry-After` in Millisekunden, oder null, wenn der Kopf fehlt oder
 * keine ganze Zahl von Sekunden traegt.
 */
function wartezeitMs(antwort) {
  const wert = antwort.headers.get('retry-after');
  if (wert === null || !/^\s*\d+\s*$/.test(wert)) return null;
  return Number(wert) * 1000;
}

/** Ein Fehler, der Methode, Pfad und Grund nennt — nie das Token, nie die rohe Antwort. */
function boardFehler(methode, pfad, grund, cause) {
  return new Error(`Board-Aufruf ${methode} ${pfad} gescheitert: ${grund}`, { cause });
}

/**
 * Baut den Client.
 *
 * @param {object} abh
 * @param {typeof fetch} abh.fetch  der Transport
 * @param {Record<string, string|undefined>} abh.env  die Umgebung; gelesen wird nur `TBX_TOKEN`
 * @param {object} abh.konfig  der Inhalt von `.claude/workflow.config.json`
 * @param {(ms: number) => Promise<void>} [abh.warte]  wartet vor der Wiederholung eines `429`
 */
export function kanbanClient({ fetch, env, konfig, warte = standardWarte }) {
  const host = konfig?.toolbox?.host;
  if (typeof host !== 'string' || host === '') {
    throw new Error(`Kein Board-Host: ${KONFIG_DATEI} nennt kein Feld toolbox.host.`);
  }
  const token = env?.TBX_TOKEN;
  if (typeof token !== 'string' || token === '') {
    throw new Error('Kein Board-Token: Die Umgebungsvariable TBX_TOKEN ist nicht gesetzt.');
  }
  const basis = host.replace(/\/+$/, '');

  async function aufruf(methode, pfad, { inhalt, koepfe = {} } = {}) {
    const init = {
      method: methode,
      headers: { ...koepfe, 'X-Kanban-Token': token },
    };
    if (inhalt !== undefined) {
      init.headers['Content-Type'] = 'application/json';
      init.body = JSON.stringify(inhalt);
    }

    let antwort = await senden(methode, pfad, init);
    if (antwort.status === 429) {
      const ms = wartezeitMs(antwort);
      if (ms !== null) {
        await warte(ms);
        antwort = await senden(methode, pfad, init);
      }
    }
    if (!antwort.ok) {
      throw boardFehler(methode, pfad, `Status ${antwort.status}`);
    }

    const text = await antwort.text();
    if (text === '') return null;
    try {
      return JSON.parse(text);
    } catch (e) {
      throw boardFehler(methode, pfad, `Antwort mit Status ${antwort.status} ist kein JSON`, e);
    }
  }

  async function senden(methode, pfad, init) {
    try {
      return await fetch(`${basis}${pfad}`, init);
    } catch (e) {
      // Die Ursache haengt nur als `cause` an: Ihre Meldung stammt vom Transport, nicht von uns.
      throw boardFehler(methode, pfad, 'Board nicht erreichbar', e);
    }
  }

  const karte = (id) => `${ITEMS}/${encodeURIComponent(String(id))}`;

  return {
    /** Alle Karten als flache Liste; jede traegt ihre Spalte in `column`. */
    async karten() {
      const spalten = (await aufruf('GET', ITEMS)) ?? {};
      return Object.entries(spalten).flatMap(([spalte, liste]) =>
        liste.map((item) => ({ ...item, column: spalte })),
      );
    },

    /** Legt eine Karte im Backlog an; `schluessel` macht eine Wiederholung unschaedlich (E13). */
    anlegen(titel, body, schluessel) {
      return aufruf('POST', ITEMS, {
        inhalt: { title: titel, body, column: 'BACKLOG', direct: true },
        koepfe: { 'Idempotency-Key': schluessel },
      });
    },

    /** Schreibt den Body der Karte mit der internen ID `id`; der Titel geht mit (E12). */
    bodySetzen(id, titel, body) {
      return aufruf('PUT', karte(id), { inhalt: { title: titel, body } });
    },

    /** Der Verlauf der Karte mit der internen ID `id`. */
    verlauf(id) {
      return aufruf('GET', `${karte(id)}/activity`);
    },

    /** Haengt einen Kommentar an die Karte mit der internen ID `id` (E22). */
    kommentieren(id, text) {
      return aufruf('POST', `${karte(id)}/comments`, { inhalt: { body: text } });
    },
  };
}
