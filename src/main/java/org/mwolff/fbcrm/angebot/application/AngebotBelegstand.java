package org.mwolff.fbcrm.angebot.application;

import java.util.Collection;
import java.util.Set;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.vorgang.domain.Belegstand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sagt dem Vorgang, an welchen seiner Zeilen ein festgeschriebenes Angebot haengt (Kriterium 22).
 *
 * <p><b>Die Abhaengigkeit ist umgekehrt</b> (Plan E2): Der Port {@link Belegstand} steht in {@code
 * vorgang.domain}, umgesetzt wird er hier. So bekommt der Vorgang seine Phase, ohne {@code angebot}
 * zu kennen — die andere Richtung, {@code angebot} liest den Vorgang, gibt es schon, und eine
 * Rueckkante waere ein Paketzyklus.
 *
 * <p>Die leere Auswahl wird hier abgefangen und nicht im Adapter: Der Bestand sagt zu, dass er nie
 * mit einer leeren Menge gefragt wird ({@code in ()} ist kein gueltiges SQL), und diese Klasse ist
 * die Stelle, an der die Menge entsteht.
 */
@Service
@Transactional(readOnly = true)
public class AngebotBelegstand implements Belegstand {

  private final AngebotRepository angebote;

  public AngebotBelegstand(final AngebotRepository angebote) {
    this.angebote = angebote;
  }

  @Override
  public Set<Long> mitFestgeschriebenemAngebot(final Collection<Long> vorgangIds) {
    if (vorgangIds.isEmpty()) {
      return Set.of();
    }
    return angebote.vorgaengeMitFestgeschriebenemAngebot(vorgangIds);
  }
}
