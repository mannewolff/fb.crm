package org.mwolff.fbcrm.arbeitszeit.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
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
 * <p><b>Dieselbe Regel entscheidet die Monatsliste</b> ({@link #sicht(long, Optional)}): Wessen
 * Zeiten ein Konto sieht und welche Vorauswahl ohne Angabe gilt, steht hier neben der Liste der
 * waehlbaren Personen — eine zweite Abschrift der Employee-Regel in {@link ArbeitszeitMonatUseCase}
 * liefe beim ersten Nachziehen auseinander (Plan #426, E10).
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
    return waehlbar(aktuellesKonto.byId(accountId)).stream().map(WaehlbarePerson::aus).toList();
  }

  /**
   * Was das anfragende Konto in der Monatsliste sieht (Plan #426, E10; #410, Kriterien 2 bis 4).
   *
   * <p>Ein Employee bekommt seine eigenen Zeiten, ohne dass der Parameter gelesen wird — die Regel
   * entscheidet der Server, nicht der Parameter. Fuer C-Level und ReadOnly gilt der Parameter, und
   * ohne ihn die Vorauswahl der Rolle: C-Level die eigenen Zeiten, ReadOnly „Alle", auch wenn dem
   * Konto Zeiten gehoeren. Eine genannte Person muss es unter den Konten geben, auch eine
   * stillgelegte (Kriterium 9); sonst zeigte die Liste eine Auswahl, die niemand waehlen konnte.
   *
   * @param accountId Kennung des angemeldeten Kontos
   * @param person die Kennung eines Kontos oder {@code alle}, oder leer fuer die Vorauswahl der
   *     Rolle
   * @throws UnknownAccount wenn es das Konto nicht mehr gibt
   * @throws PersonNichtWaehlbar wenn {@code person} weder {@code alle} noch ein Konto nennt
   */
  Personensicht sicht(final long accountId, final Optional<String> person) {
    final Account anfragend = aktuellesKonto.byId(accountId);
    final Map<Long, WaehlbarePerson> personen =
        waehlbar(anfragend).stream()
            .map(WaehlbarePerson::aus)
            .collect(Collectors.toMap(WaehlbarePerson::id, Function.identity()));
    return new Personensicht(auswahl(anfragend, person, personen), personen);
  }

  private List<Account> waehlbar(final Account anfragend) {
    return anfragend.role() == Role.EMPLOYEE ? List.of(anfragend) : konten.liste();
  }

  private static Personenauswahl auswahl(
      final Account anfragend,
      final Optional<String> person,
      final Map<Long, WaehlbarePerson> personen) {
    final Personenauswahl eigene = new Personenauswahl.Einzeln(anfragend.requireId());
    if (anfragend.role() == Role.EMPLOYEE) {
      return eigene;
    }
    final Personenauswahl auswahl =
        person
            .map(Personenauswahl::aus)
            .orElseGet(
                () -> anfragend.role() == Role.READ_ONLY ? new Personenauswahl.Alle() : eigene);
    if (auswahl instanceof final Personenauswahl.Einzeln einzeln
        && !personen.containsKey(einzeln.accountId())) {
      throw new PersonNichtWaehlbar();
    }
    return auswahl;
  }
}
