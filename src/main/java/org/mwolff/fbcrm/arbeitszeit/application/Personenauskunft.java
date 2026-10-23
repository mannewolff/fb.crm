package org.mwolff.fbcrm.arbeitszeit.application;

import java.util.List;
import org.mwolff.fbcrm.auth.application.GetCurrentAccountUseCase;
import org.mwolff.fbcrm.auth.application.MitarbeitendeVerwaltung;
import org.mwolff.fbcrm.auth.application.UnknownAccount;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Personen, unter denen das anfragende Konto in der Ansicht „Arbeitszeit" waehlen darf (Plan
 * #426, E5, E6, E11; fachliche Quelle #410, Kriterien 2 bis 4 und 9).
 *
 * <p><b>Je Rolle verschieden:</b> Ein C-Level und ReadOnly bekommen alle Konten einschliesslich der
 * stillgelegten, ein Employee allein sein eigenes. Die Rolle kommt frisch aus dem Konto und nicht
 * aus dem Token — so wie {@link GetCurrentAccountUseCase} es fuer das Konto der Sitzung tut; eine
 * Rolle, die ein C-Level eben geaendert hat, wirkt damit beim naechsten Aufruf.
 *
 * <p><b>Die Richtung ist {@code arbeitszeit} → {@code auth}</b> (E5): Gelesen wird ueber die
 * Lesewege, die {@code auth.application} schon hat, und nicht ueber einen Port, den {@code auth}
 * umsetzt — das Anmeldemodul kennt kein Fachmodul. Der Name kommt immer aus dem Konto, eine Kopie
 * in der Zeile {@code arbeitszeit} gibt es nicht (Kriterium 9).
 *
 * <p><b>Die Reihenfolge ist die des Bestands</b>: nach Anzeigenamen, bei gleichem Namen nach
 * Kennung ({@code AccountRepository#findAllOrderedByDisplayName}). Umschalter und Auswahlliste
 * springen damit nicht.
 */
@Service
@Transactional(readOnly = true)
public class Personenauskunft {

  private final GetCurrentAccountUseCase aktuellesKonto;
  private final MitarbeitendeVerwaltung konten;

  Personenauskunft(
      final GetCurrentAccountUseCase aktuellesKonto, final MitarbeitendeVerwaltung konten) {
    this.aktuellesKonto = aktuellesKonto;
    this.konten = konten;
  }

  /**
   * Die waehlbaren Personen fuer das anfragende Konto.
   *
   * @param accountId Kennung des angemeldeten Kontos
   * @throws UnknownAccount wenn es das Konto nicht mehr gibt
   */
  public List<WaehlbarePerson> personen(final long accountId) {
    final Account anfragend = aktuellesKonto.byId(accountId);
    final List<Account> waehlbar =
        anfragend.role() == Role.EMPLOYEE ? List.of(anfragend) : konten.liste();
    return waehlbar.stream().map(WaehlbarePerson::aus).toList();
  }
}
