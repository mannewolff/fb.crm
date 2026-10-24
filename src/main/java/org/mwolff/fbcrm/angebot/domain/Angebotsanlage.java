package org.mwolff.fbcrm.angebot.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Identifiable;

/**
 * Eine Anlage am Angebot (Issue #148, Kriterium 1).
 *
 * <p>Ein eigenes Fachobjekt mit eigener Identitaet und keine Liste am {@link Angebot} (Plan #150,
 * E1), wie schon der {@link Angebotskommentar}: Das Angebot wird mit seinen Positionen als Ganzes
 * geschrieben, und eine Anlage darf bei einer Aenderung des Angebots nicht mitwandern.
 *
 * <p>Der Record traegt <b>keine Regel</b>. Der Dateiname ist bereits ueber {@link Dateiname}
 * gesaeubert, die {@link Vorschauart} bereits am Inhalt erkannt, und die Groesse hat der
 * Anwendungsfall gegen die Upload-Grenze gehalten; die letzte Grenze halten die CHECKs der Tabelle.
 * Wer den Record von Hand baut, traegt dafuer die Verantwortung.
 *
 * <p>Eine Anlage aendert sich nie — kein Umbenennen, kein Ersetzen (Nicht-Ziele der Quelle).
 * Deshalb gibt es weder ein {@code updatedAt} noch eine Version (Plan #415, E7). Ihr einziger
 * schreibender Weg nach dem Hochladen ist das Loeschen, und das <b>kennzeichnet</b> die Zeile
 * (E17): {@link #geloescht(long, Instant)} haelt Zeitpunkt und Konto fest, damit ein zweites
 * Loeschen beides nennen kann (E16).
 *
 * @param id technische Id — {@code null}, solange die Anlage nicht gespeichert ist
 * @param angebotId Kennung des Angebots, an dem die Anlage haengt
 * @param dateiName der gesaeuberte Name, wie die Ansicht ihn zeigt und die Auslieferung ihn nennt
 * @param groesse Groesse des Inhalts in Byte; zwischen 1 und der Upload-Grenze
 * @param vorschauArt die am Inhalt erkannte Art, oder {@code null} — dann gibt es keine Vorschau
 * @param objektSchluessel Schluessel des Inhalts im Objektspeicher; steht in keiner Antwort
 * @param createdAt Zeitpunkt des Hochladens — der Zeitpunkt, den die Ansicht zeigt (Kriterium 4)
 * @param geloeschtAm Zeitpunkt der Loeschung, oder {@code null} fuer eine bestehende Anlage (E7)
 * @param geloeschtVon Kennung des Kontos, das die Anlage geloescht hat, oder {@code null}
 */
public record Angebotsanlage(
    @Nullable Long id,
    long angebotId,
    String dateiName,
    long groesse,
    @Nullable Vorschauart vorschauArt,
    String objektSchluessel,
    Instant createdAt,
    @Nullable Instant geloeschtAm,
    @Nullable Long geloeschtVon)
    implements Identifiable {

  /**
   * Eine Anlage, die nicht geloescht ist — so entsteht jede neue.
   *
   * @param id technische Id, oder {@code null} vor dem Speichern
   * @param angebotId Kennung des Angebots
   * @param dateiName der gesaeuberte Name
   * @param groesse Groesse des Inhalts in Byte
   * @param vorschauArt die am Inhalt erkannte Art, oder {@code null}
   * @param objektSchluessel Schluessel des Inhalts im Objektspeicher
   * @param createdAt Zeitpunkt des Hochladens
   */
  public Angebotsanlage(
      final @Nullable Long id,
      final long angebotId,
      final String dateiName,
      final long groesse,
      final @Nullable Vorschauart vorschauArt,
      final String objektSchluessel,
      final Instant createdAt) {
    this(id, angebotId, dateiName, groesse, vorschauArt, objektSchluessel, createdAt, null, null);
  }

  /**
   * Die Anlage mit dem Kennzeichen der Loeschung (Plan #415, E7, E17).
   *
   * <p>Name, Groesse, Schluessel und Zeitpunkt des Hochladens bleiben an der Zeile; nur Zeitpunkt
   * und Konto der Loeschung kommen hinzu.
   *
   * @param konto Kennung des loeschenden Kontos
   * @param zeitpunkt Zeitpunkt der Loeschung
   */
  public Angebotsanlage geloescht(final long konto, final Instant zeitpunkt) {
    return new Angebotsanlage(
        id,
        angebotId,
        dateiName,
        groesse,
        vorschauArt,
        objektSchluessel,
        createdAt,
        zeitpunkt,
        konto);
  }
}
