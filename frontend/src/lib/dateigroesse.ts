/**
 * Die Groesse einer Datei in einer Form, die ein Mensch liest — und die eine Grenze dazu.
 *
 * Binaerpraefixe (KiB, MiB) und keine dezimalen (kB, MB): Die Grenze des Uploads ist ein
 * glattes Vielfaches von 1024 ({@link MAX_UPLOAD_BYTE}). In kB gerechnet stuende dort
 * „26,2 MB", und die Meldung „hoechstens 25 MB" passte nicht zu der Zahl, die daneben steht.
 *
 * Ueber MiB geht es nicht hinaus — mehr laesst die Grenze nicht zu, und eine Stufe, die nie
 * erreicht wird, waere eine Verzweigung ohne Fall.
 */

const KIB = 1024;
const MIB = KIB * KIB;

/**
 * Die Grenze fuer hochgeladene Dateien in Byte (E10, Kriterium 18) — die eine Stelle im
 * Frontend, an der diese Zahl steht.
 *
 * Sie ist das Gegenstueck zu `Uploadgrenze.MAX_BYTE` im Backend und dient allein der Vorpruefung
 * in der Maske: Wer eine zu grosse Datei waehlt, soll sie nicht erst hochladen muessen, um die
 * Meldung zu sehen. Die Schranke bleibt serverseitig — die Oberflaeche fuehrt, sie sperrt nicht.
 */
export const MAX_UPLOAD_BYTE = 26214400;

/** Ohne Tausendertrennung: „1.024 KiB" laese sich wie 1,024, und darum geht es hier nicht. */
function gerundet(wert: number): string {
  return wert.toLocaleString('de-DE', { maximumFractionDigits: 1, useGrouping: false });
}

export function dateigroesse(bytes: number): string {
  if (bytes < KIB) {
    return `${bytes} B`;
  }
  if (bytes < MIB) {
    return `${gerundet(bytes / KIB)} KiB`;
  }
  return `${gerundet(bytes / MIB)} MiB`;
}
