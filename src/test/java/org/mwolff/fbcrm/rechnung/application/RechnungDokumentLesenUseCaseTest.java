package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicher;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Das Lesen des archivierten Dokuments einer gestellten Rechnung (#160, Kriterium 24; Plan #169,
 * E11).
 *
 * <p>Der Anwendungsfall gibt heraus, was beim Stellen entstanden ist, und erzeugt nichts neu: Der
 * Beleg ist festgeschrieben, und ein zweiter Druck liefe mit der Zeit auseinander.
 *
 * <p>Gegenstand sind die drei Faelle, die der Weg unterscheiden muss: die gestellte Rechnung mit
 * ihrem Dokument, der Entwurf, der keines hat, und die gestellte Rechnung ohne Schluessel — die
 * Waise, die entsteht, wenn das Ablegen nach dem Stellen gescheitert ist.
 */
@ExtendWith(MockitoExtension.class)
class RechnungDokumentLesenUseCaseTest {

  private static final long RECHNUNG = 2L;
  private static final String NUMMER = "0001-2026";
  private static final String SCHLUESSEL = "rechnung/2/abc.pdf";
  private static final byte[] INHALT = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);

  @Mock private RechnungRepository rechnungen;
  @Mock private DokumentSpeicher dokumente;

  private RechnungDokumentLesenUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new RechnungDokumentLesenUseCase(rechnungen, dokumente);
  }

  private static Rechnung gestellteRechnung() {
    return Rechnungsdoppel.gestellt(RECHNUNG, NUMMER, List.of(Rechnungsdoppel.beratung("80.00")))
        .mitDokument(SCHLUESSEL);
  }

  @Test
  void lese_aGestellteRechnung_thenItCarriesTheNummerAndTheBytesFromTheSpeicher()
      throws IOException {
    // Given
    when(rechnungen.findById(RECHNUNG)).thenReturn(Optional.of(gestellteRechnung()));
    when(dokumente.lies(SCHLUESSEL)).thenReturn(INHALT.clone());

    // When
    final Rechnungsdokument dokument = useCase.lese(RECHNUNG);

    // Then — Byte fuer Byte das, was im Speicher liegt; die Groesse nennt dieselbe Zahl.
    assertThat(dokument.nummer()).isEqualTo(NUMMER);
    assertThat(dokument.groesse()).isEqualTo(INHALT.length);
    try (InputStream strom = dokument.inhalt()) {
      assertThat(strom.readAllBytes()).isEqualTo(INHALT);
    }
  }

  @Test
  void lese_withAnUnknownRechnung_thenNotFound() {
    // Given
    when(rechnungen.findById(RECHNUNG)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> useCase.lese(RECHNUNG)).isInstanceOf(RechnungNichtGefunden.class);
    verify(dokumente, never()).lies(anyString());
  }

  @Test
  void lese_anEntwurf_thenItIsRejected() {
    // Given — ein Entwurf hat noch kein Dokument; es entsteht erst mit dem Stellen.
    when(rechnungen.findById(RECHNUNG))
        .thenReturn(
            Optional.of(
                Rechnungsdoppel.entwurf(RECHNUNG, List.of(Rechnungsdoppel.beratung("80.00")))));

    // When / Then
    assertThatThrownBy(() -> useCase.lese(RECHNUNG)).isInstanceOf(RechnungszustandPasstNicht.class);
    verify(dokumente, never()).lies(anyString());
  }

  @Test
  void lese_anEntwurfThatAlreadyCarriesANummerAndASchluessel_thenItIsRejected() {
    // Given — ein Widerspruch im Bestand: Nummer und Schluessel entstehen erst beim Stellen. Der
    // Zustand entscheidet darum fuer sich und nicht erst, wenn eines von beiden fehlt — sonst gaebe
    // dieser Weg einen Beleg heraus, der nicht festgeschrieben ist.
    when(rechnungen.findById(RECHNUNG)).thenReturn(Optional.of(alsEntwurf(gestellteRechnung())));

    // When / Then
    assertThatThrownBy(() -> useCase.lese(RECHNUNG)).isInstanceOf(RechnungszustandPasstNicht.class);
    verify(dokumente, never()).lies(anyString());
  }

  @Test
  void lese_aGestellteRechnungWithoutASchluessel_thenItIsRejected() {
    // Given — die Waise aus E7: gestellt, aber das Ablegen des Belegs ist gescheitert.
    when(rechnungen.findById(RECHNUNG))
        .thenReturn(
            Optional.of(
                Rechnungsdoppel.gestellt(
                    RECHNUNG, NUMMER, List.of(Rechnungsdoppel.beratung("80.00")))));

    // When / Then — kein leeres PDF: das sahe wie ein gueltiger Beleg aus.
    assertThatThrownBy(() -> useCase.lese(RECHNUNG)).isInstanceOf(RechnungszustandPasstNicht.class);
    verify(dokumente, never()).lies(anyString());
  }

  @Test
  void lese_aGestellteRechnungWithoutANummer_thenItIsRejected() {
    // Given — eine gestellte Rechnung ohne Nummer kann es nicht geben; ohne sie gaebe es keinen
    // Dateinamen, und ein Beleg ohne Nummer darf nicht hinausgehen.
    when(rechnungen.findById(RECHNUNG)).thenReturn(Optional.of(ohneNummer(gestellteRechnung())));

    // When / Then
    assertThatThrownBy(() -> useCase.lese(RECHNUNG)).isInstanceOf(RechnungszustandPasstNicht.class);
    verify(dokumente, never()).lies(anyString());
  }

  private static Rechnung alsEntwurf(final Rechnung rechnung) {
    return new Rechnung(
        rechnung.id(),
        rechnung.angebotId(),
        Rechnungszustand.ENTWURF,
        rechnung.rechnungDatum(),
        rechnung.leistungszeitraum(),
        rechnung.positionen(),
        rechnung.nummer(),
        rechnung.steuersatz(),
        rechnung.zahlungszielTage(),
        rechnung.gestelltAm(),
        rechnung.pdfSchluessel(),
        rechnung.empfaenger(),
        rechnung.absender(),
        rechnung.createdAt(),
        rechnung.updatedAt());
  }

  private static Rechnung ohneNummer(final Rechnung rechnung) {
    return new Rechnung(
        rechnung.id(),
        rechnung.angebotId(),
        rechnung.zustand(),
        rechnung.rechnungDatum(),
        rechnung.leistungszeitraum(),
        rechnung.positionen(),
        null,
        rechnung.steuersatz(),
        rechnung.zahlungszielTage(),
        rechnung.gestelltAm(),
        rechnung.pdfSchluessel(),
        rechnung.empfaenger(),
        rechnung.absender(),
        rechnung.createdAt(),
        rechnung.updatedAt());
  }
}
