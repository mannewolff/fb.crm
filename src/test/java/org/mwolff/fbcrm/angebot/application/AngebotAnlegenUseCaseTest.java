package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsstand;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenRepository;
import org.mwolff.fbcrm.vorgang.application.VorgangNichtGefunden;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;

/**
 * Das Anlegen eines Angebots am Vorgang (Kriterien 2, 3, 8, 9).
 *
 * <p>Drei Zusagen stehen im Mittelpunkt. Erstens die Vorbelegung aus Kriterium 3: Das Angebotsdatum
 * ist der heutige Tag <b>in der Geschaeftszone</b> und nicht in UTC (E12), die Gueltigkeit laeuft
 * dreissig Tage, und die Zahlungsbedingungen kommen aus „Eigene Angaben" (F8). Zweitens die Vorlage
 * aus E23: Texte und Positionen werden uebernommen, Datum und Gueltigkeit nicht — und eine Quelle
 * an einem anderen Vorgang steht nicht zur Wahl. Drittens die Schreibsperre aus E13: Am
 * abgeschlossenen Vorgang entsteht kein Angebot.
 *
 * <p>Die Uhr steht bewusst auf 22:30 UTC: In der Geschaeftszone ist da bereits der naechste Tag.
 * Eine Uhr am Mittag liesse beide Rechnungen gleich aussehen.
 */
@ExtendWith(MockitoExtension.class)
class AngebotAnlegenUseCaseTest {

  private static final long QUELLE = 11L;
  private static final Instant JETZT = Instant.parse("2026-09-27T22:30:00Z");
  private static final LocalDate HEUTE_IN_BERLIN = LocalDate.of(2026, 9, 28);
  private static final String STANDARDBEDINGUNGEN = "Zahlbar innerhalb von 30 Tagen netto.";

  @Mock private AngebotRepository angebote;
  @Mock private VorgangRepository vorgaenge;
  @Mock private EigeneAngabenRepository eigeneAngaben;

  private AngebotAnlegenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase =
        new AngebotAnlegenUseCase(
            angebote, vorgaenge, eigeneAngaben, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static Vorgang vorgang(final boolean abgeschlossen) {
    return new Vorgang(
        Long.valueOf(Angebotsdoppel.VORGANG),
        12L,
        "Website-Relaunch",
        7L,
        null,
        abgeschlossen,
        Angebotsdoppel.ANGELEGT,
        Angebotsdoppel.ANGELEGT);
  }

  private static EigeneAngaben angabenMit(final String zahlungsbedingungen) {
    return new EigeneAngaben(
        "Manfred Wolff",
        new Anschrift(null, null, null, null),
        null,
        null,
        null,
        null,
        null,
        zahlungsbedingungen);
  }

  private Angebot legeAn(final Long vorlageAngebotId) {
    when(angebote.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
    return useCase.anlegen(Angebotsdoppel.VORGANG, vorlageAngebotId).angebot();
  }

  private void vorgangIstOffen() {
    when(vorgaenge.findById(Angebotsdoppel.VORGANG)).thenReturn(Optional.of(vorgang(false)));
  }

  @Test
  void anlegen_thenStartsAsDraftAtTheVorgang() {
    // Given — Kriterium 2: Am Vorgang entsteht ein Angebot als Entwurf.
    vorgangIstOffen();
    when(eigeneAngaben.lies()).thenReturn(angabenMit(STANDARDBEDINGUNGEN));

    // When
    final Angebot angelegt = legeAn(null);

    // Then
    assertThat(angelegt.zustand()).isEqualTo(Angebotszustand.ENTWURF);
    assertThat(angelegt.vorgangId()).isEqualTo(Angebotsdoppel.VORGANG);
    assertThat(angelegt.nummer()).isNull();
    assertThat(angelegt.positionen()).isEmpty();
  }

  @Test
  void anlegen_thenDatesTheAngebotInTheGeschaeftszoneAndGivesItThirtyDays() {
    // Given — Kriterium 3 und E12: der heutige Tag in Europe/Berlin, nicht in UTC.
    vorgangIstOffen();
    when(eigeneAngaben.lies()).thenReturn(angabenMit(STANDARDBEDINGUNGEN));

    // When
    final Angebot angelegt = legeAn(null);

    // Then
    assertThat(angelegt.angebotDatum()).isEqualTo(HEUTE_IN_BERLIN);
    assertThat(angelegt.gueltigBis()).isEqualTo(HEUTE_IN_BERLIN.plusDays(30));
  }

  @Test
  void anlegen_thenPrefillsTheZahlungsbedingungenFromEigeneAngaben() {
    // Given — Kriterium 3, F8: der Standardtext der eigenen Angaben.
    vorgangIstOffen();
    when(eigeneAngaben.lies()).thenReturn(angabenMit(STANDARDBEDINGUNGEN));

    // When
    final Angebot angelegt = legeAn(null);

    // Then
    assertThat(angelegt.zahlungsbedingungen()).isEqualTo(STANDARDBEDINGUNGEN);
    assertThat(angelegt.leistungsbeschreibung()).isNull();
  }

  @Test
  void anlegen_whenEigeneAngabenCarryNoText_thenLeavesTheZahlungsbedingungenEmpty() {
    // Given — auf einer frischen Instanz ist der Standardtext leer.
    vorgangIstOffen();
    when(eigeneAngaben.lies()).thenReturn(angabenMit(null));

    // When
    final Angebot angelegt = legeAn(null);

    // Then
    assertThat(angelegt.zahlungsbedingungen()).isNull();
  }

  @Test
  void anlegen_thenAnswersWithTheStandOfTheNewDraft() {
    // Given
    vorgangIstOffen();
    when(eigeneAngaben.lies()).thenReturn(angabenMit(STANDARDBEDINGUNGEN));
    when(angebote.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));

    // When
    final AngebotAnsicht ansicht = useCase.anlegen(Angebotsdoppel.VORGANG, null);

    // Then
    assertThat(ansicht.stand()).isEqualTo(Angebotsstand.ENTWURF);
  }

  @Test
  void anlegen_withVorlage_thenCopiesTextsAndPositionen() {
    // Given — E23: uebernommen werden Texte und Positionen.
    vorgangIstOffen();
    when(angebote.findById(QUELLE))
        .thenReturn(
            Optional.of(
                Angebotsdoppel.entwurf(
                    QUELLE, Angebotsdoppel.VORGANG, List.of(Angebotsdoppel.SCHULUNG))));

    // When
    final Angebot angelegt = legeAn(Long.valueOf(QUELLE));

    // Then
    assertThat(angelegt.leistungsbeschreibung()).isEqualTo(Angebotsdoppel.BESCHREIBUNG);
    assertThat(angelegt.zahlungsbedingungen()).isEqualTo(Angebotsdoppel.BEDINGUNGEN);
    assertThat(angelegt.positionen()).containsExactly(Angebotsdoppel.SCHULUNG);
  }

  @Test
  void anlegen_withVorlage_thenDatesAreFreshAndNotCopied() {
    // Given — E23: Angebotsdatum und Gueltigkeit werden neu vorbelegt.
    vorgangIstOffen();
    when(angebote.findById(QUELLE)).thenReturn(Optional.of(Angebotsdoppel.entwurf(QUELLE)));

    // When
    final Angebot angelegt = legeAn(Long.valueOf(QUELLE));

    // Then
    assertThat(angelegt.angebotDatum()).isEqualTo(HEUTE_IN_BERLIN);
    assertThat(angelegt.gueltigBis()).isEqualTo(HEUTE_IN_BERLIN.plusDays(30));
  }

  @Test
  void anlegen_withVorlageFromAnotherVorgang_thenRejects() {
    // Given — E23: die Quelle haengt an einem anderen Vorgang.
    vorgangIstOffen();
    when(angebote.findById(QUELLE))
        .thenReturn(
            Optional.of(
                Angebotsdoppel.entwurf(
                    QUELLE, Angebotsdoppel.FREMDER_VORGANG, List.of(Angebotsdoppel.KONZEPTION))));

    // When / Then
    assertThatThrownBy(() -> useCase.anlegen(Angebotsdoppel.VORGANG, Long.valueOf(QUELLE)))
        .isInstanceOf(VorlageNichtWaehlbar.class);
    verify(angebote, never()).save(any());
  }

  @Test
  void anlegen_withUnknownVorlage_thenRejects() {
    // Given — eine Kennung im Rumpf, zu der es kein Angebot gibt.
    vorgangIstOffen();
    when(angebote.findById(QUELLE)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.anlegen(Angebotsdoppel.VORGANG, Long.valueOf(QUELLE)))
        .isInstanceOf(VorlageNichtWaehlbar.class);
    verify(angebote, never()).save(any());
  }

  @Test
  void anlegen_atAClosedVorgang_thenRejectsAndWritesNothing() {
    // Given — E13, Kriterium 9: am abgeschlossenen Vorgang entsteht kein Angebot.
    when(vorgaenge.findById(Angebotsdoppel.VORGANG)).thenReturn(Optional.of(vorgang(true)));

    // When / Then
    assertThatThrownBy(() -> useCase.anlegen(Angebotsdoppel.VORGANG, null))
        .isInstanceOf(VorgangAbgeschlossen.class);
    verify(angebote, never()).save(any());
  }

  @Test
  void anlegen_atAnUnknownVorgang_thenRejects() {
    // Given
    when(vorgaenge.findById(Angebotsdoppel.VORGANG)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.anlegen(Angebotsdoppel.VORGANG, null))
        .isInstanceOf(VorgangNichtGefunden.class);
    verify(angebote, never()).save(any());
  }
}
