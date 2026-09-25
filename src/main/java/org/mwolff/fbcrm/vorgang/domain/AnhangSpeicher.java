package org.mwolff.fbcrm.vorgang.domain;

import java.io.InputStream;
import java.util.Optional;

/**
 * Port auf den Objektspeicher der Anhaenge; die Umsetzung liegt in {@code vorgang.infrastructure}
 * und spricht MinIO (E7).
 *
 * <p>Der Speicher kennt nur Schluessel und Bytes. Dateiname und Groesse stehen allein in der
 * Datenbank — der Name kommt von aussen und waere im Schluessel eine Pfadangabe (E9).
 */
public interface AnhangSpeicher {

  /**
   * Legt den Inhalt ab und liefert den Schluessel, unter dem er wiederzufinden ist.
   *
   * <p><b>Zusage:</b> Der Schluessel hat die Form {@code vorgang/<vorgangId>/<uuid>} und traegt
   * keinen Teil des Dateinamens (E9). Geschrieben wird vor der Zeile in der Datenbank; ein Rollback
   * laesst das Objekt als Waise liegen (E8).
   *
   * @param vorgangId Kennung des Vorgangs, zu dem der Anhang gehoert
   * @param inhalt der Datenstrom; der Aufrufer schliesst ihn
   * @param groesse Zahl der Byte, die zu lesen sind
   */
  String ablegen(long vorgangId, InputStream inhalt, long groesse);

  /**
   * Der Inhalt zu einem Schluessel, oder leer, wenn der Speicher ihn nicht kennt.
   *
   * <p>Ein unbekannter Schluessel ist kein Fehler des Speichers, sondern eine Auskunft an den
   * Aufrufer — der Aufrufer schliesst den Datenstrom.
   *
   * @param objektSchluessel der Schluessel aus {@link #ablegen}
   */
  Optional<InputStream> lesen(String objektSchluessel);
}
