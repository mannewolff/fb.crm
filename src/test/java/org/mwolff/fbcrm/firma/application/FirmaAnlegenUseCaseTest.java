package org.mwolff.fbcrm.firma.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.firma.domain.Anschrift;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;

/**
 * Das Anlegen einer Firma (Kriterien 4, 7).
 *
 * <p>Hier steht die Normalisierung aus E9 auf dem Pruefstand: Leerraum am Rand faellt weg, ein
 * optionales Feld aus lauter Leerraum wird {@code NULL}, und ein Name aus lauter Leerraum wird
 * abgewiesen, bevor irgendetwas gespeichert wird.
 */
@ExtendWith(MockitoExtension.class)
class FirmaAnlegenUseCaseTest {

  private static final Instant JETZT = Instant.parse("2026-09-25T09:30:00Z");

  @Mock private FirmaRepository firmen;
  @Captor private ArgumentCaptor<Firma> gespeicherte;

  private FirmaAnlegenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new FirmaAnlegenUseCase(firmen, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static FirmaDaten daten(final String name) {
    return new FirmaDaten(
        name,
        new Anschrift("Am Wall 1", "28195", "Bremen", "Deutschland"),
        "75/123/45678",
        "DE123456789");
  }

  private Firma speichereUndFange(final FirmaDaten daten) {
    when(firmen.save(any(Firma.class))).thenAnswer(aufruf -> aufruf.getArgument(0));
    useCase.anlegen(daten);
    verify(firmen).save(gespeicherte.capture());
    return gespeicherte.getValue();
  }

  @Test
  void anlegen_thenStoresTheFirmaAsActive() {
    // When
    final Firma gespeichert = speichereUndFange(daten("Adler AG"));

    // Then — Kriterium 4: eine neue Firma ist aktiv.
    assertThat(gespeichert.aktiv()).isTrue();
  }

  @Test
  void anlegen_thenTakesBothTimestampsFromTheClock() {
    // When
    final Firma gespeichert = speichereUndFange(daten("Adler AG"));

    // Then
    assertThat(gespeichert).extracting(Firma::createdAt, Firma::updatedAt).containsOnly(JETZT);
  }

  @Test
  void anlegen_thenLeavesTheIdToTheDatabase() {
    // When
    final Firma gespeichert = speichereUndFange(daten("Adler AG"));

    // Then
    assertThat(gespeichert.id()).isNull();
  }

  @Test
  void anlegen_thenStoresEveryValueOfTheRequest() {
    // When
    final Firma gespeichert = speichereUndFange(daten("Adler AG"));

    // Then
    assertThat(gespeichert)
        .extracting(Firma::name, Firma::anschrift, Firma::steuernummer, Firma::umsatzsteuerId)
        .containsExactly(
            "Adler AG",
            new Anschrift("Am Wall 1", "28195", "Bremen", "Deutschland"),
            "75/123/45678",
            "DE123456789");
  }

  @Test
  void anlegen_thenReturnsTheFirmaWithTheIdFromTheRepository() {
    // Given
    final Firma mitId =
        new Firma(
            42L, "Adler AG", new Anschrift(null, null, null, null), null, null, true, JETZT, JETZT);
    when(firmen.save(any(Firma.class))).thenReturn(mitId);

    // When — Kriterium 7: die Oberflaeche braucht die Kennung fuer den Weg zur Detailansicht.
    final Firma angelegt = useCase.anlegen(daten("Adler AG"));

    // Then
    assertThat(angelegt).isEqualTo(mitId);
  }

  @Test
  void anlegen_givenAPaddedName_thenStoresItTrimmed() {
    // When — E9.
    final Firma gespeichert = speichereUndFange(daten("  Adler AG  "));

    // Then
    assertThat(gespeichert.name()).isEqualTo("Adler AG");
  }

  @Test
  void anlegen_givenOptionalFieldsOfWhitespaceOnly_thenStoresThemAsNull() {
    // When — E9: es gibt genau eine Schreibweise fuer „nicht angegeben".
    final Firma gespeichert =
        speichereUndFange(new FirmaDaten("Adler AG", new Anschrift("  ", "", "\t", " "), "  ", ""));

    // Then
    assertThat(gespeichert)
        .extracting(Firma::anschrift, Firma::steuernummer, Firma::umsatzsteuerId)
        .containsExactly(new Anschrift(null, null, null, null), null, null);
  }

  @Test
  void anlegen_givenNoOptionalValuesAtAll_thenKeepsThemAbsent() {
    // When — Kriterium 4: eine Firma darf mit nichts als ihrem Namen entstehen.
    final Firma gespeichert =
        speichereUndFange(
            new FirmaDaten("Adler AG", new Anschrift(null, null, null, null), null, null));

    // Then
    assertThat(gespeichert)
        .extracting(Firma::anschrift, Firma::steuernummer, Firma::umsatzsteuerId)
        .containsExactly(new Anschrift(null, null, null, null), null, null);
  }

  @Test
  void anlegen_givenPaddedOptionalFields_thenStoresThemTrimmed() {
    // When
    final Firma gespeichert =
        speichereUndFange(
            new FirmaDaten(
                "Adler AG",
                new Anschrift(" Am Wall 1 ", " 28195 ", " Bremen ", " Deutschland "),
                " 75/123/45678 ",
                " DE123456789 "));

    // Then
    assertThat(gespeichert)
        .extracting(Firma::anschrift, Firma::steuernummer, Firma::umsatzsteuerId)
        .containsExactly(
            new Anschrift("Am Wall 1", "28195", "Bremen", "Deutschland"),
            "75/123/45678",
            "DE123456789");
  }

  @Test
  void anlegen_givenANameOfWhitespaceOnly_thenIsRefused() {
    // When / Then — E9: was @NotBlank an der Schnittstelle abweist, weist auch die
    // Transaktionsgrenze ab.
    assertThatThrownBy(() -> useCase.anlegen(daten("   ")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anlegen_givenANameOfWhitespaceOnly_thenStoresNothing() {
    // When
    assertThatThrownBy(() -> useCase.anlegen(daten("   ")))
        .isInstanceOf(IllegalArgumentException.class);

    // Then
    verifyNoInteractions(firmen);
  }
}
