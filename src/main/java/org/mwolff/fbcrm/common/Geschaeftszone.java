package org.mwolff.fbcrm.common;

import java.time.ZoneId;

/**
 * Die eine Zeitzone, gegen die jede Datumsgrenze der Anwendung rechnet (E12).
 *
 * <p>Zeitstempel liegen in UTC — die Datenbank fuehrt sie so, und die Uhr der Anwendung ist {@code
 * Clock.systemUTC()}. Ein <b>Datum</b> ist dagegen keine Zeitspanne, sondern eine Grenze, und eine
 * Grenze braucht einen Ort: Ob ein Angebot heute noch gueltig ist, ob eine Rechnung faellig ist, ob
 * eine Belegnummer ins alte oder ins neue Jahr gehoert — das entscheidet sich am Kalender des
 * Freiberuflers und nicht am Nullmeridian. Zwischen 22:00 UTC und Mitternacht deutscher Zeit
 * unterscheiden sich beide Antworten um einen Tag.
 *
 * <p>Sie steht fest im Code und kommt nicht aus der Zone des Betriebssystems: Derselbe Bestand
 * muesste sonst auf einem Server in UTC und auf dem Rechner des Entwicklers verschiedene Fristen
 * ergeben.
 */
public final class Geschaeftszone {

  /** Europe/Berlin — der Kalender, gegen den jede Frist und jeder Jahreswechsel rechnet. */
  public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

  private Geschaeftszone() {}
}
