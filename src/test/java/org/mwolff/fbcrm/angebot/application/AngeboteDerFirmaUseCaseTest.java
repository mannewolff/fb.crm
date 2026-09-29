package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsstand;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;

/**
 * Die Angebotsliste einer Firma (Kriterium 20).
 *
 * <p>Gegenstand ist die Reihenfolge aus E25: Entwuerfe stehen oben, und innerhalb jeder Gruppe
 * zaehlt der Anlagezeitpunkt absteigend; bei gleichem Zeitpunkt entscheidet die hoehere Kennung,
 * damit zwei Aufrufe dieselbe Liste liefern. Sortiert wird hier und nicht im Bestand: Die Ordnung
 * ist eine Aussage der Ansicht, und nur an dieser Stelle ist sie ohne Datenbank pruefbar.
 *
 * <p>Der Stand jeder Zeile entsteht wie beim einzelnen Angebot aus dem heutigen Tag (E4).
 */
@ExtendWith(MockitoExtension.class)
class AngeboteDerFirmaUseCaseTest {

  private static final Instant JETZT = Instant.parse("2026-10-21T08:00:00Z");
  private static final Instant FRUEH = Instant.parse("2026-09-20T08:00:00Z");
  private static final Instant SPAET = Instant.parse("2026-09-25T08:00:00Z");

  @Mock private AngebotRepository angebote;

  private AngeboteDerFirmaUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new AngeboteDerFirmaUseCase(angebote, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static Angebot zeile(
      final long id, final Angebotszustand zustand, final Instant angelegt) {
    return Angebotsdoppel.angebot(
        id, Angebotsdoppel.FIRMA, zustand, List.of(Angebotsdoppel.KONZEPTION), angelegt);
  }

  private List<Long> kennungenIn(final List<Angebot> bestand) {
    when(angebote.findByFirma(Angebotsdoppel.FIRMA)).thenReturn(bestand);
    return useCase.angebote(Angebotsdoppel.FIRMA).stream()
        .map(ansicht -> Long.valueOf(ansicht.angebot().requireId()))
        .toList();
  }

  @Test
  void angebote_thenDraftsComeFirst() {
    // Given — E25: ein Entwurf steht ueber jedem versendeten, auch wenn er aelter ist.
    final List<Long> kennungen =
        kennungenIn(
            List.of(
                zeile(1L, Angebotszustand.VERSENDET, SPAET),
                zeile(2L, Angebotszustand.ENTWURF, FRUEH)));

    // Then
    assertThat(kennungen).containsExactly(Long.valueOf(2L), Long.valueOf(1L));
  }

  @Test
  void angebote_withinAGroup_thenTheYoungestComesFirst() {
    // Given — E25: created_at absteigend.
    final List<Long> kennungen =
        kennungenIn(
            List.of(
                zeile(1L, Angebotszustand.VERSENDET, FRUEH),
                zeile(2L, Angebotszustand.ABGELEHNT, SPAET)));

    // Then
    assertThat(kennungen).containsExactly(Long.valueOf(2L), Long.valueOf(1L));
  }

  @Test
  void angebote_withEqualTimestamps_thenTheHigherIdComesFirst() {
    // Given — E25: der Tiebreak, damit zwei Aufrufe dieselbe Liste liefern.
    final List<Long> kennungen =
        kennungenIn(
            List.of(
                zeile(1L, Angebotszustand.ENTWURF, FRUEH),
                zeile(2L, Angebotszustand.ENTWURF, FRUEH)));

    // Then
    assertThat(kennungen).containsExactly(Long.valueOf(2L), Long.valueOf(1L));
  }

  @Test
  void angebote_thenEachRowCarriesItsDerivedStand() {
    // Given — E4: das versendete Angebot ist am 21.10. abgelaufen, der Entwurf nie.
    when(angebote.findByFirma(Angebotsdoppel.FIRMA))
        .thenReturn(
            List.of(
                zeile(1L, Angebotszustand.ENTWURF, SPAET),
                zeile(2L, Angebotszustand.VERSENDET, FRUEH)));

    // When
    final List<AngebotAnsicht> ansichten = useCase.angebote(Angebotsdoppel.FIRMA);

    // Then
    assertThat(ansichten)
        .extracting(AngebotAnsicht::stand)
        .containsExactly(Angebotsstand.ENTWURF, Angebotsstand.ABGELAUFEN);
  }

  @Test
  void angebote_whenTheFirmaHasNone_thenAnswersWithAnEmptyList() {
    // Given — eine Firma ohne Angebot ist kein Fehler.
    when(angebote.findByFirma(Angebotsdoppel.FIRMA)).thenReturn(List.of());

    // When / Then
    assertThat(useCase.angebote(Angebotsdoppel.FIRMA)).isEmpty();
  }
}
