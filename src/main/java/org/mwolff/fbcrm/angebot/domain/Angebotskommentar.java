package org.mwolff.fbcrm.angebot.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Identifiable;

/**
 * Ein Kommentar am Angebot (Issue #140, Kriterium 1).
 *
 * <p>Ein eigenes Fachobjekt mit eigener Identitaet und keine Liste am {@link Angebot} (Plan #141,
 * E2): Das Angebot wird mit seinen Positionen als Ganzes geschrieben, und ein Kommentar darf bei
 * einer Aenderung des Angebots nicht mitwandern.
 *
 * <p>Der Record <b>bereinigt den Text nicht</b> und prueft seine Laenge nicht. Beides tut der
 * Anwendungsfall, nach dem Muster von {@code FirmaDaten} und {@code AnsprechpartnerDaten} (E7);
 * kein {@code domain}-Paket des Bestands schneidet Leerraum ab. Wer den Record von Hand baut,
 * traegt darum die Verantwortung fuer den Text — die letzte Grenze halten die CHECKs der Tabelle.
 *
 * <p>Der Verfasser steht als Kennung seines Kontos und nicht als Name (Plan #414, A7): Die Ansicht
 * zeigt den <b>aktuellen</b> Namen, und den liest der Anwendungsfall beim Lesen aus dem Konto — ein
 * kopierter Name waere nach dem ersten Umbenennen falsch (fachliche Quelle #409, Kriterium 11).
 *
 * <p>Unveraenderlich: {@link #geaendert(String, Instant)} liefert einen neuen Kommentar. Der
 * Zeitpunkt kommt von aussen, weil die Domaene keine Uhr kennt (CLAUDE-java.md §6.2).
 *
 * <p><b>Eigene Version, eigener Urheber</b> (Plan #415, E8, E14). Der Kommentar ist ein Datensatz
 * fuer sich: Aendern und Loeschen beruhen auf seinem Stand, nicht auf dem des Angebots, und ein
 * neuer Kommentar laesst die Version des Angebots stehen. <b>Geloescht wird mit Kennzeichen</b>
 * (E17): {@link #geloescht(long, Instant)} haelt Zeitpunkt und Konto fest, damit ein spaeterer
 * Zugriff beides nennen kann; aus der Liste faellt der Kommentar trotzdem heraus.
 *
 * @param id technische Id — {@code null}, solange der Kommentar nicht gespeichert ist
 * @param angebotId Kennung des Angebots, an dem der Kommentar haengt
 * @param verfasserId Kennung des Kontos, das den Kommentar geschrieben hat
 * @param text der Text des Kommentars; nie leer, das prueft der Anwendungsfall
 * @param createdAt Zeitpunkt der Anlage — der Zeitpunkt, den die Ansicht zeigt (Kriterium 3)
 * @param updatedAt Zeitpunkt der letzten Aenderung; keine Antwort traegt ihn (Kriterium 9)
 * @param version der Stand des Kommentars, wie ihn {@code If-Match} vorlegt (Plan #415, E1, E8); 0
 *     vor dem ersten Schreiben, danach fuehrt ihn der Bestand
 * @param geaendertVon Kennung des Kontos, das den Kommentar zuletzt geaendert hat, oder {@code
 *     null}, solange er nie geaendert wurde
 * @param geloeschtAm Zeitpunkt der Loeschung, oder {@code null} fuer einen bestehenden Kommentar
 *     (E17)
 * @param geloeschtVon Kennung des Kontos, das den Kommentar geloescht hat, oder {@code null}
 */
public record Angebotskommentar(
    @Nullable Long id,
    long angebotId,
    long verfasserId,
    String text,
    Instant createdAt,
    Instant updatedAt,
    long version,
    @Nullable Long geaendertVon,
    @Nullable Instant geloeschtAm,
    @Nullable Long geloeschtVon)
    implements Identifiable {

  /**
   * Ein Kommentar, der nie geaendert und nie geloescht wurde — so entsteht jeder neue.
   *
   * @param id technische Id, oder {@code null} vor dem Speichern
   * @param angebotId Kennung des Angebots
   * @param verfasserId Kennung des verfassenden Kontos
   * @param text der Text, bereits bereinigt
   * @param createdAt Zeitpunkt der Anlage
   * @param updatedAt Zeitpunkt der letzten Aenderung
   */
  public Angebotskommentar(
      final @Nullable Long id,
      final long angebotId,
      final long verfasserId,
      final String text,
      final Instant createdAt,
      final Instant updatedAt) {
    this(id, angebotId, verfasserId, text, createdAt, updatedAt, 0L, null, null, null);
  }

  /**
   * Der Kommentar mit neuem Text (Kriterium 9).
   *
   * <p>Wechselt genau zwei Felder: den Text und {@code updatedAt}. Kennung, Angebot, Verfasser und
   * {@code createdAt} bleiben, damit der Zeitpunkt in der Ansicht nach dem Bearbeiten derselbe ist
   * (E8) und der Kommentar seinem Verfasser gehoert, gleich wer ihn aendert.
   *
   * @param neuerText der neue Text; bereits bereinigt
   * @param zeitpunkt Zeitpunkt der Aenderung
   */
  public Angebotskommentar geaendert(final String neuerText, final Instant zeitpunkt) {
    return new Angebotskommentar(
        id,
        angebotId,
        verfasserId,
        neuerText,
        createdAt,
        zeitpunkt,
        version,
        geaendertVon,
        geloeschtAm,
        geloeschtVon);
  }

  /**
   * Der Kommentar mit dem Konto, das ihn gerade geaendert hat (Plan #415, E14).
   *
   * <p>Ein eigener Schritt wie {@code Angebot.durch(long)}: Wer aendert, ist eine Angabe des
   * Anwendungsfalls. Die Version bleibt, wie sie ist — sie zaehlt der Bestand beim Schreiben
   * weiter.
   *
   * @param konto Kennung des handelnden Kontos
   */
  public Angebotskommentar durch(final long konto) {
    return new Angebotskommentar(
        id,
        angebotId,
        verfasserId,
        text,
        createdAt,
        updatedAt,
        version,
        konto,
        geloeschtAm,
        geloeschtVon);
  }

  /**
   * Der Kommentar mit dem Kennzeichen der Loeschung (Plan #415, E17).
   *
   * <p>Text, Verfasser und Zeitpunkte bleiben an der Zeile; nur Zeitpunkt und Konto der Loeschung
   * kommen hinzu.
   *
   * @param konto Kennung des loeschenden Kontos
   * @param zeitpunkt Zeitpunkt der Loeschung
   */
  public Angebotskommentar geloescht(final long konto, final Instant zeitpunkt) {
    return new Angebotskommentar(
        id,
        angebotId,
        verfasserId,
        text,
        createdAt,
        updatedAt,
        version,
        geaendertVon,
        zeitpunkt,
        konto);
  }
}
