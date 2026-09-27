package org.mwolff.fbcrm.vorgang.application;

import java.time.Clock;
import org.mwolff.fbcrm.vorgang.domain.Eintrag;
import org.mwolff.fbcrm.vorgang.domain.EintragRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Vermerken eines Ereignisses in der Historie eines Vorgangs (Kriterium 19).
 *
 * <p><b>Der einzige Schreibweg fuer Ereignisse.</b> Es gibt keinen Controller dazu und es soll
 * keinen geben: Ein Ereignis entsteht aus einem Zustandswechsel innerhalb der Anwendung — ein
 * versandtes Angebot, ein angenommener Auftrag —, und ein Aufrufer von aussen koennte es nur
 * faelschen. Die Anfrage der Eintragsmaske weist die Art {@code EREIGNIS} deshalb schon in der Bean
 * Validation ab.
 *
 * <p>Der Zeitpunkt kommt aus der Uhr der Anwendung und nicht vom Aufrufer. Ein Ereignis geschieht,
 * wenn es geschieht; ein nachgetragener Zeitpunkt waere ein Nachtrag von Hand und gehoerte damit in
 * einen Kommentar.
 *
 * <p>Dass es den Vorgang gibt, prueft der Fremdschluessel der Zeile. Eine eigene Abfrage braeuchte
 * es nur fuer eine bessere Meldung — und die liest hier niemand: Der Aufrufer ist Code, der den
 * Vorgang bereits in der Hand hat.
 */
@Service
@Transactional
public class EreignisVermerkenUseCase {

  private final EintragRepository eintraege;
  private final Clock clock;

  public EreignisVermerkenUseCase(final EintragRepository eintraege, final Clock clock) {
    this.eintraege = eintraege;
    this.clock = clock;
  }

  /**
   * Schreibt das Ereignis mit dem jetzigen Zeitpunkt in die Historie.
   *
   * @param vorgangId Kennung des Vorgangs, an dessen Historie das Ereignis haengt
   * @param text was geschehen ist; darf nicht leer sein
   * @throws IllegalArgumentException wenn der Text leer ist
   */
  public void vermerken(final long vorgangId, final String text) {
    eintraege.save(Eintrag.ereignis(vorgangId, text, clock.instant()));
  }
}
