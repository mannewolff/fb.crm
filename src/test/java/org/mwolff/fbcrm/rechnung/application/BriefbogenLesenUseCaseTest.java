package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.rechnung.domain.Briefbogen;
import org.mwolff.fbcrm.rechnung.domain.BriefbogenRepository;
import org.mwolff.fbcrm.rechnung.domain.Briefpapierblatt;
import org.mwolff.fbcrm.rechnung.domain.Randmasse;
import org.mwolff.fbcrm.rechnung.domain.Zeilenstand;

/**
 * Der Stand des Briefbogens, wie die Maske ihn sieht (fachliche Quelle #361, Kriterien 1 und 15;
 * Plan #363, E1).
 *
 * <p>Der Schluessel des Briefpapiers im Objektspeicher ist ein Detail der Ablage und geht nicht
 * nach aussen — wie bei der nachgetragenen Rechnung. Die Version der Zeile geht mit (Plan #415,
 * E11).
 */
@ExtendWith(MockitoExtension.class)
class BriefbogenLesenUseCaseTest {

  private static final Randmasse VORGABE = new Randmasse(250, 200, 985, 200, 200, 200);
  private static final String SCHLUESSEL = "briefpapier/3f2a.pdf";

  private static final Zeilenstand STAND =
      new Zeilenstand(4L, 30L, Instant.parse("2026-10-19T08:00:00Z"));

  @Mock private BriefbogenRepository bestand;

  @Test
  void lese_givenNoBriefpapier_thenAnswersWithoutBlattAndWithTheSettings() {
    // Given — eine frische Instanz: die Vorgaben der Migration, beide Schalter an.
    when(bestand.lies()).thenReturn(new Briefbogen(null, true, true, VORGABE));
    when(bestand.stand()).thenReturn(STAND);

    // When
    final Briefbogenstand stand = new BriefbogenLesenUseCase(bestand).lese();

    // Then
    assertThat(stand)
        .isEqualTo(new Briefbogenstand(false, null, null, null, true, true, VORGABE, 4L));
  }

  @Test
  void lese_givenABriefpapier_thenAnswersWithItsNameSizeAndPagesButNotItsKey() {
    // Given
    final Randmasse eingestellt = new Randmasse(300, 150, 1_000, 250, 180, 220);
    when(bestand.lies())
        .thenReturn(
            new Briefbogen(
                new Briefpapierblatt(SCHLUESSEL, "kopf.pdf", 98_765L, 2),
                false,
                true,
                eingestellt));
    when(bestand.stand()).thenReturn(STAND);

    // When
    final Briefbogenstand stand = new BriefbogenLesenUseCase(bestand).lese();

    // Then
    assertThat(stand)
        .isEqualTo(new Briefbogenstand(true, "kopf.pdf", 98_765L, 2, false, true, eingestellt, 4L));
    assertThat(stand.toString()).doesNotContain(SCHLUESSEL);
  }
}
