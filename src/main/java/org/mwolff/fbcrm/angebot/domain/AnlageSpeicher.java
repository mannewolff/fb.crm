package org.mwolff.fbcrm.angebot.domain;

import java.io.InputStream;
import java.util.Optional;

/**
 * Port auf den Objektspeicher der Anlagen am Angebot; die Umsetzung liegt in {@code
 * angebot.infrastructure} und spricht MinIO (Plan #150, E3).
 *
 * <p>Ein eigener Port neben {@link DokumentSpeicher} und nicht dessen Erweiterung: Der
 * Belegspeicher fuehrt ganze Belege als Byte-Feld im Arbeitsspeicher und haengt {@code .pdf} an den
 * Schluessel — beides passt nicht zu einer Anlage, die bis zur Upload-Grenze gross sein darf und
 * jede Dateiart tragen kann. Dieser Port arbeitet deshalb durchgehend mit Datenstroemen.
 *
 * <p>Der Speicher kennt nur Schluessel und Bytes. Dateiname, Groesse und Vorschauart stehen allein
 * in der Datenbank — der Name kommt von aussen und waere im Schluessel eine Pfadangabe.
 *
 * <p>In welcher Reihenfolge Objekt und Zeile geschrieben und geloescht werden, entscheidet der
 * Anwendungsfall (E9); {@link Angebotsanlage#objektSchluessel()} verbindet beide.
 */
public interface AnlageSpeicher {

  /**
   * Legt den Inhalt ab und liefert den Schluessel, unter dem er wiederzufinden ist.
   *
   * <p><b>Zusage:</b> Der Schluessel hat die Form {@code angebot/<angebotId>/anlage/<uuid>} und
   * traegt <b>keinen Teil des Dateinamens</b> — ein Name von aussen waere im Schluessel eine
   * Pfadangabe. Das {@code anlage/} trennt die Anlagen von den archivierten Belegen desselben
   * Angebots, die unter demselben {@code angebot/<angebotId>/} liegen. Der Schluesselraum gehoert
   * dem Adapter, damit kein Aufrufer die Ablagestruktur in die Anwendungsschicht zieht.
   *
   * <p>Jede Ablage bekommt ihren eigenen Schluessel: Zweimal dieselbe Datei am selben Angebot sind
   * zwei Anlagen, und die zweite ueberschreibt die erste nicht.
   *
   * @param angebotId Kennung des Angebots, zu dem die Anlage gehoert
   * @param inhalt der Datenstrom; der Aufrufer schliesst ihn
   * @param groesse Zahl der Byte, die zu lesen sind
   * @return der Schluessel des abgelegten Objekts
   */
  String ablegen(long angebotId, InputStream inhalt, long groesse);

  /**
   * Der Inhalt zu einem Schluessel, oder leer, wenn der Speicher ihn nicht kennt.
   *
   * <p>Ein unbekannter Schluessel ist hier kein Widerspruch im Bestand, sondern eine Auskunft an
   * den Aufrufer: Das Objekt kann nach dem Loeschen der Zeile fehlen, und die Auslieferung soll
   * daraufhin 404 melden statt zu scheitern. Der Aufrufer schliesst den Datenstrom.
   *
   * @param objektSchluessel der Schluessel aus {@link #ablegen}
   * @return der Datenstrom des Inhalts, oder leer
   */
  Optional<InputStream> lesen(String objektSchluessel);

  /**
   * Loescht das Objekt zu einem Schluessel.
   *
   * <p>Ein unbekannter Schluessel ist <b>kein Fehler</b>: Das Loeschen laeuft erst nach dem Commit
   * der geloeschten Zeile (E9) und darf nicht daran scheitern, dass es schon einmal lief oder das
   * Objekt nie entstand. Nach dem Aufruf liefert {@link #lesen} zu diesem Schluessel leer.
   *
   * @param objektSchluessel der Schluessel aus {@link #ablegen}
   */
  void loeschen(String objektSchluessel);
}
