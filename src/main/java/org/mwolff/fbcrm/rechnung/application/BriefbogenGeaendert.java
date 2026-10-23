package org.mwolff.fbcrm.rechnung.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Bearbeitungskonflikt;
import org.mwolff.fbcrm.rechnung.domain.Zeilenstand;

/**
 * Der Briefbogen wurde seit dem Oeffnen geaendert, und der Schritt beruht auf dem alten Stand
 * (#411, Kriterium 4; Plan #415, E1, E11, E14).
 *
 * <p>Gilt fuer alle drei Wege gleichermassen — Schalter und Raender, Hochladen und Entfernen des
 * Briefpapiers —, denn der Briefbogen traegt einen Stand fuer die ganze Zeile (E11): Wer Raender
 * auf dem Stand vor einem Hochladen speichert, wird abgewiesen, obwohl beide Wege in getrennte
 * Spalten schreiben. Status und Abbildung nach 409 mit der Erweiterung {@code konflikt} legt {@link
 * Bearbeitungskonflikt} fest; hier stehen nur, wer zuletzt geaendert hat, wer abgewiesen wird und
 * wann der gueltige Stand entstand.
 *
 * <p>Beim Stellen einer Rechnung und beim Druck weist der Briefbogen nichts ab (E10): Er gilt, wie
 * er im Augenblick ist.
 */
public final class BriefbogenGeaendert extends Bearbeitungskonflikt {

  /**
   * Der Konflikt mit allem, was die Meldung nennt.
   *
   * @param urheberKonto wer zuletzt geaendert hat, oder {@code null}, wenn die Zeile es nicht weiss
   * @param handelndesKonto wer gerade abgewiesen wird
   * @param zeitpunkt wann zuletzt geaendert wurde
   */
  public BriefbogenGeaendert(
      final @Nullable Long urheberKonto, final long handelndesKonto, final Instant zeitpunkt) {
    super(Art.VERALTET, urheberKonto, handelndesKonto, zeitpunkt);
  }

  /**
   * Weist den Schritt ab, wenn die Zeile einen anderen Stand traegt als den vorgelegten.
   *
   * @param stand der Stand der Zeile, wie ihn der Bestand liefert
   * @param version der vorgelegte Stand aus {@code If-Match}
   * @param konto Kennung des handelnden Kontos
   * @throws BriefbogenGeaendert wenn die Zeile inzwischen einen anderen Stand hat
   */
  static void pruefe(final Zeilenstand stand, final long version, final long konto) {
    Bearbeitungskonflikt.pruefe(
        version,
        stand.version(),
        () -> new BriefbogenGeaendert(stand.geaendertVon(), konto, stand.geaendertAm()));
  }
}
