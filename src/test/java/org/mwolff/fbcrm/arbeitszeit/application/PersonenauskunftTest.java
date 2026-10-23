package org.mwolff.fbcrm.arbeitszeit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.auth.Konten;
import org.mwolff.fbcrm.auth.application.GetCurrentAccountUseCase;
import org.mwolff.fbcrm.auth.application.MitarbeitendeVerwaltung;
import org.mwolff.fbcrm.auth.application.UnknownAccount;
import org.mwolff.fbcrm.auth.domain.Account;
import org.mwolff.fbcrm.auth.domain.Role;

/**
 * Die waehlbaren Personen je Rolle (Plan #426, E5, E11; fachliche Quelle #410, Kriterien 2 bis 4
 * und 9).
 *
 * <p>Ausgangspunkt sind drei Konten: Manfred (C-Level), Ben (Employee) und Clara, deren Konto
 * stillgelegt ist. C-Level und ReadOnly bekommen alle drei, ein Employee allein sein eigenes. Der
 * Name kommt bei jedem Aufruf frisch aus dem Konto, nicht aus einer Kopie (Kriterium 9).
 */
@ExtendWith(MockitoExtension.class)
class PersonenauskunftTest {

  private static final Instant JETZT = Instant.parse("2026-11-05T08:00:00Z");
  private static final long MANFRED = 1L;
  private static final long BEN = 2L;
  private static final long CLARA = 3L;
  private static final long LESER = 4L;

  @Mock private GetCurrentAccountUseCase aktuellesKonto;
  @Mock private MitarbeitendeVerwaltung konten;

  @InjectMocks private Personenauskunft auskunft;

  private static Account konto(
      final long id, final String name, final Role rolle, final boolean aktiv) {
    return Konten.mit(id, name.toLowerCase(Locale.ROOT) + "@example.org", rolle, aktiv, 0, JETZT)
        .mitNamen(name, JETZT);
  }

  private static Account manfred() {
    return konto(MANFRED, "Manfred", Role.C_LEVEL, true);
  }

  private static Account ben() {
    return konto(BEN, "Ben", Role.EMPLOYEE, true);
  }

  private static Account clara() {
    return konto(CLARA, "Clara", Role.EMPLOYEE, false);
  }

  private static List<Account> alle() {
    return List.of(ben(), clara(), manfred());
  }

  private static List<WaehlbarePerson> alleAlsPersonen() {
    return List.of(
        new WaehlbarePerson(BEN, "Ben", true),
        new WaehlbarePerson(CLARA, "Clara", false),
        new WaehlbarePerson(MANFRED, "Manfred", true));
  }

  @Test
  void personen_forACLevel_thenAllAccountsIncludingTheDeactivatedOne() {
    // Given — Kriterium 9: C-Level kann auch eine stillgelegte Person auswaehlen.
    when(aktuellesKonto.byId(MANFRED)).thenReturn(manfred());
    when(konten.liste()).thenReturn(alle());

    // When
    final List<WaehlbarePerson> personen = auskunft.personen(MANFRED);

    // Then — in der Reihenfolge des Bestands, nach Anzeigenamen.
    assertThat(personen).containsExactlyElementsOf(alleAlsPersonen());
  }

  @Test
  void personen_forReadOnly_thenAllAccountsIncludingTheDeactivatedOne() {
    // Given — Kriterium 4: ReadOnly sieht wie ein C-Level, auch wenn ihm selbst nichts gehoert.
    when(aktuellesKonto.byId(LESER)).thenReturn(konto(LESER, "Lea", Role.READ_ONLY, true));
    when(konten.liste()).thenReturn(alle());

    // When
    final List<WaehlbarePerson> personen = auskunft.personen(LESER);

    // Then
    assertThat(personen).containsExactlyElementsOf(alleAlsPersonen());
  }

  @Test
  void personen_forAnEmployee_thenExactlyTheOwnAccount() {
    // Given — Kriterium 2: fremde Personen sieht ein Employee nicht.
    when(aktuellesKonto.byId(BEN)).thenReturn(ben());

    // When
    final List<WaehlbarePerson> personen = auskunft.personen(BEN);

    // Then
    assertThat(personen).containsExactly(new WaehlbarePerson(BEN, "Ben", true));
    verifyNoInteractions(konten);
  }

  @Test
  void personen_afterARename_thenTheCurrentNameOfTheAccount() {
    // Given — Kriterium 9: nach einer Umbenennung zeigt die Auswahl den neuen Namen.
    when(aktuellesKonto.byId(MANFRED)).thenReturn(manfred());
    when(konten.liste())
        .thenReturn(List.of(ben(), manfred()))
        .thenReturn(List.of(ben().mitNamen("Benjamin", JETZT), manfred()));
    assertThat(auskunft.personen(MANFRED)).extracting(WaehlbarePerson::name).contains("Ben");

    // When
    final List<WaehlbarePerson> personen = auskunft.personen(MANFRED);

    // Then
    assertThat(personen)
        .containsExactly(
            new WaehlbarePerson(BEN, "Benjamin", true),
            new WaehlbarePerson(MANFRED, "Manfred", true));
  }

  @Test
  void personen_givenAnAccountThatIsGone_thenTheSessionIsWorthless() {
    // Given — die Sitzung verweist auf ein Konto, das es nicht mehr gibt.
    when(aktuellesKonto.byId(MANFRED)).thenThrow(new UnknownAccount());

    // When / Then
    assertThatThrownBy(() -> auskunft.personen(MANFRED)).isInstanceOf(UnknownAccount.class);
    verifyNoInteractions(konten);
  }
}
