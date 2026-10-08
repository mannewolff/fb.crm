package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnung;
import org.mwolff.fbcrm.rechnung.domain.NachgetrageneRechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Das Lesen einer nachgetragenen Rechnung (#254, Kriterium 11): Sie verweist auf ihre Firma, der
 * Name kommt darum aus dem Bestand von heute.
 */
@ExtendWith(MockitoExtension.class)
class NachtragLesenUseCaseTest {

  private static final long ID = 21L;
  private static final long FIRMA = 4L;
  private static final Instant ANGELEGT = Instant.parse("2026-03-02T08:00:00Z");

  @Mock private NachgetrageneRechnungRepository nachgetragene;
  @Mock private FirmaRepository firmen;

  private NachtragLesenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new NachtragLesenUseCase(nachgetragene, firmen);
  }

  private static NachgetrageneRechnung rechnung() {
    return new NachgetrageneRechnung(
        ID,
        FIRMA,
        "RE-1",
        LocalDate.of(2026, 3, 1),
        new BigDecimal("1000.00"),
        new BigDecimal("1190.00"),
        Rechnungszustand.GESTELLT,
        null,
        ANGELEGT,
        ANGELEGT);
  }

  @Test
  void lese_thenTheRechnungWithTheNameOfItsFirmaToday() {
    // Given — die Firma heisst inzwischen anders als beim Nachtragen
    when(nachgetragene.findById(ID)).thenReturn(Optional.of(rechnung()));
    when(firmen.findById(FIRMA))
        .thenReturn(Optional.of(Rechnungsdoppel.firma(FIRMA, "Umbenannt GmbH")));

    // When
    final NachgetrageneRechnungMitFirma gelesen = useCase.lese(ID);

    // Then
    assertThat(gelesen.rechnung()).isEqualTo(rechnung());
    assertThat(gelesen.firmaId()).isEqualTo(FIRMA);
    assertThat(gelesen.firmaName()).isEqualTo("Umbenannt GmbH");
  }

  @Test
  void lese_withAnUnknownRechnung_thenNachtragNichtGefunden() {
    // Given
    when(nachgetragene.findById(ID)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.lese(ID)).isInstanceOf(NachtragNichtGefunden.class);
  }

  @Test
  void lese_whenTheFirmaIsMissing_thenItIsAContradictionInTheBestand() {
    // Given — Firmen werden nie geloescht
    when(nachgetragene.findById(ID)).thenReturn(Optional.of(rechnung()));
    when(firmen.findById(FIRMA)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.lese(ID)).isInstanceOf(FirmaNichtGefunden.class);
  }

  @Test
  void nachtragNichtGefunden_thenNotFound() {
    // When / Then
    assertThat(NachtragNichtGefunden.class.getAnnotation(ResponseStatus.class).code())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
