package org.mwolff.fbcrm.vorgang.application;

import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;
import org.springframework.stereotype.Component;

/**
 * Die Wahlregel fuer Firma und Ansprechpartner eines Vorgangs (E19, Kriterien 6 und 23).
 *
 * <p><b>Serverseitig und nicht nur in der Maske.</b> Die Oberflaeche ist keine Schranke; ohne diese
 * Probe waere eine untergeschobene stillgelegte Zuordnung von der Ausnahme aus Kriterium 23 nicht
 * zu unterscheiden.
 *
 * <p><b>Eine Klasse fuer beide Schreibwege</b>, weil es eine Regel mit einer Ausnahme ist: Anlegen
 * und Aendern unterscheiden sich allein darin, ob es einen bisherigen Stand gibt, an dem die
 * Ausnahme haengt. Zweimal geschrieben waere die Ausnahme zweimal zu pflegen — und genau sie trennt
 * Kriterium 23 von einer Luecke.
 */
@Component
public class Zuordnungswahl {

  private final FirmaRepository firmen;
  private final AnsprechpartnerRepository ansprechpartner;

  public Zuordnungswahl(
      final FirmaRepository firmen, final AnsprechpartnerRepository ansprechpartner) {
    this.firmen = firmen;
    this.ansprechpartner = ansprechpartner;
  }

  /**
   * Prueft die gewaehlte Zuordnung und laesst sie durch, oder wirft.
   *
   * @param daten die gewaehlte Zuordnung
   * @param bisher der Stand vor der Aenderung, oder {@code null} beim Anlegen — an ihm haengt die
   *     Ausnahme aus Kriterium 23
   * @throws FirmaNichtWaehlbar wenn die Firma unbekannt oder stillgelegt und nicht die bisherige
   *     ist
   * @throws AnsprechpartnerNichtWaehlbar wenn der Ansprechpartner unbekannt ist, zu einer anderen
   *     Firma gehoert, oder stillgelegt und nicht der bisherige ist
   */
  public void pruefe(final VorgangDaten daten, final @Nullable Vorgang bisher) {
    pruefeFirma(daten.firmaId(), bisher);
    pruefeAnsprechpartner(daten, bisher);
  }

  private void pruefeFirma(final long firmaId, final @Nullable Vorgang bisher) {
    final Firma firma = firmen.findById(firmaId).orElseThrow(FirmaNichtWaehlbar::new);
    final boolean bleibtWieBisher = bisher != null && bisher.firmaId() == firmaId;
    if (!firma.aktiv() && !bleibtWieBisher) {
      throw new FirmaNichtWaehlbar();
    }
  }

  private void pruefeAnsprechpartner(final VorgangDaten daten, final @Nullable Vorgang bisher) {
    final Long id = daten.ansprechpartnerId();
    if (id == null) {
      return;
    }
    final Ansprechpartner partner =
        ansprechpartner.findById(id).orElseThrow(AnsprechpartnerNichtWaehlbar::new);
    // Die Zugehoerigkeit gilt ohne Ausnahme: Wer die Firma wechselt, verliert den Ansprechpartner
    // der alten (Kriterium 6). Kriterium 23 nimmt nur den Stilllegungsstand aus.
    if (partner.firmaId() != daten.firmaId()) {
      throw new AnsprechpartnerNichtWaehlbar();
    }
    final boolean bleibtWieBisher = bisher != null && id.equals(bisher.ansprechpartnerId());
    if (!partner.aktiv() && !bleibtWieBisher) {
      throw new AnsprechpartnerNichtWaehlbar();
    }
  }
}
