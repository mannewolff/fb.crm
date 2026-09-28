package org.mwolff.fbcrm.auftrag.application;

import java.util.Collection;
import java.util.Set;
import org.mwolff.fbcrm.auftrag.domain.AuftragRepository;
import org.mwolff.fbcrm.vorgang.domain.Belegstand;
import org.mwolff.fbcrm.vorgang.domain.Phase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sagt dem Vorgang, an welchen seiner Zeilen ein Auftrag haengt (Kriterium 10) — die zweite von
 * mehreren Umsetzungen des Ports, je Belegart eine.
 *
 * <p><b>Die Abhaengigkeit ist umgekehrt</b> (Plan E2 von Issue #117): Der Port {@link Belegstand}
 * steht in {@code vorgang.domain}, umgesetzt wird er hier. So bekommt der Vorgang seine Phase, ohne
 * {@code auftrag} zu kennen — die andere Richtung, {@code auftrag} liest den Vorgang, gibt es
 * schon, und eine Rueckkante waere ein Paketzyklus.
 *
 * <p><b>Sein Dasein genuegt.</b> Beim Angebot zaehlt erst die Festschreibung, weil es einen Entwurf
 * hat; ein Auftrag hat keinen. Er traegt seine Nummer ab dem Anlegen, und darum gibt es keinen
 * Zustand, in dem er nicht zaehlt — auch ein abgeschlossener Auftrag haelt die Phase (F6). Erst
 * sein Loeschen laesst den Vorgang auf „Angebot" zurueckfallen, und das folgt aus dieser Regel ohne
 * eigenes Zutun.
 *
 * <p>Die leere Auswahl wird hier abgefangen und nicht im Adapter: Der Bestand sagt zu, dass er nie
 * mit einer leeren Menge gefragt wird ({@code in ()} ist kein gueltiges SQL), und diese Klasse ist
 * die Stelle, an der die Menge entsteht.
 */
@Service
@Transactional(readOnly = true)
public class AuftragBelegstand implements Belegstand {

  private final AuftragRepository auftraege;

  public AuftragBelegstand(final AuftragRepository auftraege) {
    this.auftraege = auftraege;
  }

  @Override
  public Phase phase() {
    return Phase.AUFTRAG;
  }

  @Override
  public Set<Long> mitBeleg(final Collection<Long> vorgangIds) {
    if (vorgangIds.isEmpty()) {
      return Set.of();
    }
    return auftraege.vorgaengeMitAuftrag(vorgangIds);
  }
}
