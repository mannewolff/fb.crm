/**
 * Die Groesse einer Datei in einer Form, die ein Mensch liest.
 *
 * Binaerpraefixe (KiB, MiB) und keine dezimalen (kB, MB): Die Grenze des Uploads ist im Backend
 * als 26 214 400 Byte gesetzt, also 25 · 1024 · 1024. In kB gerechnet stuende dort „26,2 MB",
 * und die Meldung „hoechstens 25 MB" passte nicht zu der Zahl, die daneben steht.
 *
 * Ueber MiB geht es nicht hinaus — mehr laesst die Grenze nicht zu, und eine Stufe, die nie
 * erreicht wird, waere eine Verzweigung ohne Fall.
 */

const KIB = 1024;
const MIB = KIB * KIB;

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
