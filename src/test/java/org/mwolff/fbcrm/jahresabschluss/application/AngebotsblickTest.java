package org.mwolff.fbcrm.jahresabschluss.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;

/**
 * Das Storno an den drei Mengen des Jahresabschlusses (#330, Kriterium 11; Plan #332, E12).
 *
 * <p>Allein {@link Angebotsblick#offen(Angebot)} erfaehrt vom Storno. {@code abgegeben} und {@code
 * angenommen} nehmen weiter den Status, und genau das belegt der Vergleich am stornierten und am
 * nicht stornierten Angebot desselben Status. Die uebrigen Regeln von {@code Angebotsblick} prueft
 * {@code JahresabschlussUseCaseTest}.
 */
class AngebotsblickTest {

  private static final Instant ANGELEGT = Instant.parse("2025-01-10T08:00:00Z");

  private static Angebot angebot(final Angebotsstatus status, final boolean storniert) {
    return new Angebot(
        1L,
        5L,
        null,
        status.intern(),
        status,
        storniert,
        null,
        LocalDate.of(2025, 2, 1),
        "Website-Relaunch",
        "Neugestaltung der Website",
        List.of(),
        ANGELEGT,
        ANGELEGT,
        0L,
        null);
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotsstatus.class,
      names = {"ANGELEGT", "ABGEGEBEN", "BESTELLT"})
  void offen_whenTheAngebotIsStorniert_thenFalse(final Angebotsstatus status) {
    assertThat(Angebotsblick.offen(angebot(status, true))).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = Angebotsstatus.class,
      names = {"ANGELEGT", "ABGEGEBEN", "BESTELLT"})
  void abgegebenAndAngenommen_whenTheAngebotIsStorniert_thenAsWithoutStorno(
      final Angebotsstatus status) {
    final Angebot storniert = angebot(status, true);
    final Angebot nichtStorniert = angebot(status, false);

    assertThat(Angebotsblick.abgegeben(storniert.status()))
        .isEqualTo(Angebotsblick.abgegeben(nichtStorniert.status()));
    assertThat(Angebotsblick.angenommen(storniert.status()))
        .isEqualTo(Angebotsblick.angenommen(nichtStorniert.status()));
  }

  @ParameterizedTest
  @EnumSource(Angebotsstatus.class)
  void offen_whenTheAngebotIsNotStorniert_thenOnlyAbgegeben(final Angebotsstatus status) {
    assertThat(Angebotsblick.offen(angebot(status, false)))
        .isEqualTo(status == Angebotsstatus.ABGEGEBEN);
  }
}
