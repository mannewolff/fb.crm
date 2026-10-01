package org.mwolff.fbcrm.rechnung.application;

import java.io.ByteArrayInputStream;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicher;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Dokument einer gestellten Rechnung (#160, Kriterium 24; Plan #169, E11).
 *
 * <p><b>Es wird gelesen und nicht gedruckt.</b> Der Beleg ist beim Stellen entstanden und liegt im
 * Objektspeicher; ein zweiter Druck aus den Daten von heute liefe mit der Zeit auseinander — genau
 * das soll die Archivierung verhindern (#160, Kriterium 14).
 *
 * <p>Drei Faelle weist der Anwendungsfall ab, und alle drei entscheidet er selbst:
 *
 * <ul>
 *   <li>Es gibt die Rechnung nicht — {@link RechnungNichtGefunden}, also 404.
 *   <li>Sie ist ein Entwurf. Ein Entwurf traegt kein Dokument, weil es keines gibt; das ist kein
 *       fehlender Bestand, sondern ein Zustand, der den Schritt nicht zulaesst, und darum 409.
 *   <li>Sie ist gestellt, traegt aber keinen Schluessel oder keine Nummer. Das ist die Waise aus
 *       Plan #169, E7: Das Stellen hat geschrieben, das Ablegen des Belegs ist gescheitert. Lieber
 *       409 als leere Bytes — ein leeres PDF sahe wie ein gueltiger Beleg aus.
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class RechnungDokumentLesenUseCase {

  private final RechnungRepository rechnungen;
  private final DokumentSpeicher dokumente;

  RechnungDokumentLesenUseCase(
      final RechnungRepository rechnungen, final DokumentSpeicher dokumente) {
    this.rechnungen = rechnungen;
    this.dokumente = dokumente;
  }

  /**
   * Das Dokument zu einer Rechnung samt ihrer Nummer.
   *
   * @param rechnungId Kennung der Rechnung
   * @throws RechnungNichtGefunden wenn es die Rechnung nicht gibt
   * @throws RechnungszustandPasstNicht wenn die Rechnung ein Entwurf ist oder kein Dokument traegt
   */
  public Rechnungsdokument lese(final long rechnungId) {
    final Rechnung rechnung =
        rechnungen.findById(rechnungId).orElseThrow(RechnungNichtGefunden::new);
    final @Nullable String schluessel = rechnung.pdfSchluessel();
    final @Nullable String nummer = rechnung.nummer();
    if (rechnung.zustand() != Rechnungszustand.GESTELLT || schluessel == null || nummer == null) {
      throw new RechnungszustandPasstNicht();
    }
    final byte[] inhalt = dokumente.lies(schluessel);
    return new Rechnungsdokument(nummer, inhalt.length, new ByteArrayInputStream(inhalt));
  }
}
