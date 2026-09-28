package org.mwolff.fbcrm.auftrag.web;

import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.auftrag.application.AuftragAmAngebot;
import org.mwolff.fbcrm.auftrag.application.AuftragAnsicht;

/**
 * Was die Angebotsansicht ueber den Auftrag zeigt (Plan E3).
 *
 * <p>Zwei Angaben in einer Antwort, weil die Ansicht beide zugleich braucht: die Zeile des Auftrags
 * — oder {@code null}, wenn es keinen gibt — und ob sich heute einer anlegen laesst. Die Taste
 * „Auftrag anlegen" erscheint genau dann, wenn {@code auftrag} leer und {@code anlegbar} wahr ist.
 *
 * <p><b>{@code anlegbar} sagt nichts darueber, ob schon ein Auftrag haengt</b> — das steht daneben.
 * Zweimal dasselbe auszudruecken hiesse, beide Werte in Einklang halten zu muessen.
 *
 * @param auftrag der Auftrag zu diesem Angebot, oder {@code null} — hoechstens einer (F9)
 * @param anlegbar {@code true}, wenn Angebotszustand und Abschlussstand des Vorgangs das Anlegen
 *     heute zulassen (Kriterien 1, 11)
 */
public record AngebotAuftragResponse(@Nullable AuftragResponse auftrag, boolean anlegbar) {

  /** Die Sicht der Oberflaeche auf den Auftragsstand eines Angebots. */
  static AngebotAuftragResponse of(final AuftragAmAngebot auskunft) {
    final @Nullable AuftragAnsicht auftrag = auskunft.auftrag();
    return new AngebotAuftragResponse(
        auftrag == null ? null : AuftragResponse.of(auftrag), auskunft.anlegbar());
  }
}
