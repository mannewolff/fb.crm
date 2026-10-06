package org.mwolff.fbcrm.rechnung.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.rechnung.application.Rechnungslistenzeile;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Die Liste aller Rechnungen (#160, Kriterium 1).
 *
 * <p>Ein Objekt mit einer Liste und kein nacktes Array, wie jede Liste dieser Anwendung: So kommt
 * eine weitere Angabe neben der Liste spaeter ohne Bruch dazu.
 *
 * @param rechnungen die Rechnungen, neueste zuerst
 */
public record RechnungenUebersichtResponse(List<Zeile> rechnungen) {

  /** Die Sicht der Oberflaeche auf die Liste. */
  static RechnungenUebersichtResponse of(final List<Rechnungslistenzeile> zeilen) {
    return new RechnungenUebersichtResponse(zeilen.stream().map(Zeile::of).toList());
  }

  /**
   * Eine Zeile der Liste.
   *
   * <p>Die {@code nummer} fehlt beim Entwurf: Sie entsteht erst mit dem Stellen (Kriterium 15). Der
   * {@code brutto} kommt gerechnet und steht in keiner Spalte — mit dem Steuersatz, der fuer diese
   * Rechnung gilt (Kriterium 14); bei einer nachgetragenen ist er der erfasste.
   *
   * <p>{@code nachgetragen} sagt die Art und damit, in welchem Kennungsraum {@code id} steht;
   * {@code dokument}, ob etwas herunterzuladen ist — die Oberflaeche schliesst das nicht mehr aus
   * dem Zustand (Plan #259, E18).
   *
   * @param id Kennung der Rechnung im Raum ihrer Art
   * @param nummer die Rechnungsnummer, oder {@code null} im Entwurf
   * @param firmaId Kennung der Firma, an die die Rechnung geht
   * @param firmaName Name dieser Firma
   * @param rechnungDatum Datum der Rechnung
   * @param brutto der Bruttobetrag, gerechnet
   * @param zustand der Zustand der Rechnung
   * @param nachgetragen ob die Rechnung nachgetragen und nicht von fb.crm geschrieben ist
   * @param dokument ob ein Dokument hinterlegt ist
   */
  public record Zeile(
      long id,
      @Nullable String nummer,
      long firmaId,
      String firmaName,
      LocalDate rechnungDatum,
      BigDecimal brutto,
      Rechnungszustand zustand,
      boolean nachgetragen,
      boolean dokument) {

    static Zeile of(final Rechnungslistenzeile zeile) {
      return new Zeile(
          zeile.id(),
          zeile.nummer(),
          zeile.firmaId(),
          zeile.firmaName(),
          zeile.rechnungDatum(),
          zeile.brutto(),
          zeile.zustand(),
          zeile.nachgetragen(),
          zeile.hatDokument());
    }
  }
}
