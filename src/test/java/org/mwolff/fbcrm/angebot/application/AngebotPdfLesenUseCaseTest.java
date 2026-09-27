package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.angebot.domain.DokumentSpeicher;

/**
 * Das Wiederlesen des archivierten Belegs (Kriterium 14, E17).
 *
 * <p>Ausgeliefert wird das beim Versenden abgelegte Objekt und nichts Nachgerechnetes — der
 * Anwendungsfall kennt deshalb weder Layout noch Drucker, nur den Schluessel am Angebot.
 *
 * <p>Der Entwurf ist der interessante Fall: Er traegt keinen Schluessel, und ein Entwurf ist kein
 * Fehler im Bestand, sondern ein Angebot, das den Kunden noch nicht erreicht hat. Deshalb 409 und
 * nicht 404 — und der Objektspeicher wird dabei nicht einmal gefragt.
 */
@ExtendWith(MockitoExtension.class)
class AngebotPdfLesenUseCaseTest {

  private static final long ANGEBOT = 11L;
  private static final byte[] BELEG = "%PDF-1.7 Angebot".getBytes(StandardCharsets.UTF_8);

  @Mock private AngebotRepository angebote;
  @Mock private DokumentSpeicher speicher;

  @InjectMocks private AngebotPdfLesenUseCase useCase;

  @ParameterizedTest
  @EnumSource(
      value = Angebotszustand.class,
      names = {"VERSENDET", "ANGENOMMEN", "ABGELEHNT", "ABGELOEST"})
  void pdf_thenAnswersWithTheStoredObject(final Angebotszustand zustand) {
    // Given — Kriterium 14: jeder festgeschriebene Beleg laesst sich wieder oeffnen.
    when(angebote.findById(ANGEBOT))
        .thenReturn(Optional.of(Angebotsdoppel.festgeschrieben(ANGEBOT, zustand)));
    when(speicher.lies("angebot/11/beleg.pdf")).thenReturn(BELEG);

    // When
    final Belegdokument dokument = useCase.pdf(ANGEBOT);

    // Then
    assertThat(dokument.inhalt()).containsExactly(BELEG);
    assertThat(dokument.dateiname()).isEqualTo("A-2026-011.pdf");
  }

  @Test
  void pdf_givenADraft_thenRejectsWithoutAskingTheSpeicher() {
    // Given — ein Entwurf hat kein Dokument (Kriterium 6).
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.of(Angebotsdoppel.entwurf(ANGEBOT)));

    // When / Then
    assertThatThrownBy(() -> useCase.pdf(ANGEBOT)).isInstanceOf(AngebotOhneBeleg.class);
    verifyNoInteractions(speicher);
  }

  @Test
  void pdf_whenTheAngebotIsUnknown_thenRejects() {
    // Given
    when(angebote.findById(ANGEBOT)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.pdf(ANGEBOT)).isInstanceOf(AngebotNichtGefunden.class);
    verifyNoInteractions(speicher);
  }
}
