/**
 * Die Groesse einer Datei in einer Form, die ein Mensch liest — und die eine Grenze dazu.
 *
 * **Beschriftet wird dezimal (KB, MB), gerechnet wird binaer** (1 MB = 1.048.576 Byte, Plan
 * #150 E12). Das ist bewusst die Schreibweise, die Betriebssysteme und Mailprogramme benutzen:
 * Die Grenze des Uploads ist ein glattes Vielfaches von 1024 ({@link MAX_UPLOAD_BYTE}), und nur
 * so steht neben der Meldung „hoechstens 25 MB" auch die Zahl „25 MB". Dezimal gerechnet
 * stuende dort „26,2 MB", binaer beschriftet „25 MiB" — beides liest niemand als dieselbe
 * Grenze, die die Meldung nennt.
 *
 * Ueber MB geht es nicht hinaus — mehr laesst die Grenze nicht zu, und eine Stufe, die nie
 * erreicht wird, waere eine Verzweigung ohne Fall.
 */

const KB = 1024;
const MB = KB * KB;

/**
 * Die Grenze fuer hochgeladene Dateien in Byte (Issue #148, Kriterium 6) — die eine Stelle im
 * Frontend, an der diese Zahl steht.
 *
 * Sie ist das Gegenstueck zu `Uploadgrenze.MAX_BYTE` im Backend und dient allein der Vorpruefung
 * in der Maske: Wer eine zu grosse Datei waehlt, soll sie nicht erst hochladen muessen, um die
 * Meldung zu sehen. Die Schranke bleibt serverseitig — die Oberflaeche fuehrt, sie sperrt nicht.
 */
export const MAX_UPLOAD_BYTE = 26214400;

/** Ohne Tausendertrennung: „1.024 KB" laese sich wie 1,024, und darum geht es hier nicht. */
function gerundet(wert: number): string {
  return wert.toLocaleString('de-DE', { maximumFractionDigits: 1, useGrouping: false });
}

export function dateigroesse(bytes: number): string {
  if (bytes < KB) {
    return `${String(bytes)} B`;
  }
  if (bytes < MB) {
    return `${gerundet(bytes / KB)} KB`;
  }
  return `${gerundet(bytes / MB)} MB`;
}
