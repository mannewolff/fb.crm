package org.mwolff.fbcrm.firma.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Uebersicht der Firmen mit Suchtext und Schalter (Kriterien 2, 3, 6, 13).
 *
 * <p>Gefiltert und sortiert wird im Bestand, nicht hier und nicht im Browser (E5, E6): Die Zahl der
 * Firmen ist nach oben offen, und dieselbe Liste wird spaeter fuer die Zuordnung zu einem Vorgang
 * gebraucht — die Regel gehoert an eine Stelle.
 *
 * <p>Die Zahl der aktiven Ansprechpartner kommt in <b>einer</b> Abfrage fuer alle gefundenen
 * Firmen, nicht in einer je Zeile.
 */
@Service
@Transactional(readOnly = true)
public class FirmenUebersichtUseCase {

  private final FirmaRepository firmen;
  private final AnsprechpartnerRepository ansprechpartner;

  public FirmenUebersichtUseCase(
      final FirmaRepository firmen, final AnsprechpartnerRepository ansprechpartner) {
    this.firmen = firmen;
    this.ansprechpartner = ansprechpartner;
  }

  /**
   * Die gefilterte Liste und die Zahl aller Firmen.
   *
   * @param suche Teil des Namens; der Leerstring trifft jede Firma
   * @param auchStillgelegte {@code true}, wenn auch stillgelegte Firmen erscheinen sollen
   */
  public FirmenUebersicht uebersicht(final String suche, final boolean auchStillgelegte) {
    final List<Firma> gefunden = firmen.uebersicht(suche, auchStillgelegte);
    final Map<Long, Long> aktive =
        ansprechpartner.zaehleAktiveJeFirma(gefunden.stream().map(Firma::requireId).toList());
    return new FirmenUebersicht(
        gefunden.stream().map(firma -> zeile(firma, aktive)).toList(), firmen.zaehleAlle());
  }

  /*
   * requireNonNull statt getOrDefault: Der Port sagt zu, zu jeder angefragten Kennung einen
   * Eintrag zu liefern — auch die Null. Ein stiller Ersatzwert machte aus einem gebrochenen
   * Versprechen eine plausible Zahl.
   */
  private static FirmaZeile zeile(final Firma firma, final Map<Long, Long> aktive) {
    final long id = firma.requireId();
    return new FirmaZeile(
        id,
        firma.name(),
        firma.anschrift().ort(),
        Objects.requireNonNull(aktive.get(id)),
        firma.aktiv());
  }
}
