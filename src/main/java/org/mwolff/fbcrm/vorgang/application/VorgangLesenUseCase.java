package org.mwolff.fbcrm.vorgang.application;

import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.vorgang.domain.EintragRepository;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Detailansicht eines Vorgangs samt Historie (Kriterien 9, 11, 15, 16, 23).
 *
 * <p>Alles in einer Antwort (E25): Die Historie <b>ist</b> die Detailansicht, und ein zweiter
 * Leseweg fuer sie braechte der Oberflaeche nur einen zweiten Ladezustand.
 *
 * <p>Firma und Ansprechpartner kommen vollstaendig mit — einschliesslich ihres Stilllegungsstands.
 * Kriterium 23 verlangt, dass eine stillgelegte Zuordnung sichtbar bleibt und als solche
 * gekennzeichnet wird; ohne den Stand waere sie von einer aktiven nicht zu unterscheiden, und ein
 * eigener Leseweg dafuer waere eine zweite Abfrage fuer eine Ansicht.
 */
@Service
@Transactional(readOnly = true)
public class VorgangLesenUseCase {

  private final VorgangRepository vorgaenge;
  private final EintragRepository eintraege;
  private final FirmaRepository firmen;
  private final AnsprechpartnerRepository ansprechpartner;

  public VorgangLesenUseCase(
      final VorgangRepository vorgaenge,
      final EintragRepository eintraege,
      final FirmaRepository firmen,
      final AnsprechpartnerRepository ansprechpartner) {
    this.vorgaenge = vorgaenge;
    this.eintraege = eintraege;
    this.firmen = firmen;
    this.ansprechpartner = ansprechpartner;
  }

  /**
   * Der Vorgang zu einer Kennung mit Zuordnung und vollstaendiger Historie.
   *
   * @param id technische Id des Vorgangs
   * @throws VorgangNichtGefunden wenn es den Vorgang nicht gibt
   */
  public VorgangMitHistorie lese(final long id) {
    final Vorgang vorgang = vorgaenge.findById(id).orElseThrow(VorgangNichtGefunden::new);
    return new VorgangMitHistorie(
        vorgang,
        firma(vorgang.firmaId()),
        partner(vorgang.ansprechpartnerId()),
        eintraege.findByVorgang(id).stream().map(EintragAnsicht::of).toList());
  }

  /*
   * Die beiden Fremdschluessel schliessen eine fehlende Zuordnung aus. Traete sie trotzdem ein,
   * waere der Bestand kaputt — dann ist ein lauter Fehler die richtige Antwort und kein 404, das
   * eine gebrochene Beziehung wie eine erfundene Kennung aussehen liesse.
   */
  private Firma firma(final long firmaId) {
    return firmen
        .findById(firmaId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Zum Vorgang gibt es keine Firma mit der Kennung " + firmaId + "."));
  }

  private @Nullable Ansprechpartner partner(final @Nullable Long ansprechpartnerId) {
    if (ansprechpartnerId == null) {
      return null;
    }
    return ansprechpartner
        .findById(ansprechpartnerId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Zum Vorgang gibt es keinen Ansprechpartner mit der Kennung "
                        + ansprechpartnerId
                        + "."));
  }
}
