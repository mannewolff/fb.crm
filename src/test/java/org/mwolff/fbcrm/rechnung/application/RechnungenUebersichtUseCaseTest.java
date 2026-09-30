package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.application.AngebotNichtGefunden;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsstatus;
import org.mwolff.fbcrm.firma.application.FirmaNichtGefunden;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.rechnung.domain.Nummernmuster;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungseinstellungen;
import org.mwolff.fbcrm.rechnung.domain.RechnungseinstellungenRepository;

/**
 * Die Liste aller Rechnungen (#160, Kriterium 1).
 *
 * <p>Gegenstand sind die Reihenfolge — neueste zuerst, bei gleichem Datum die hoehere Kennung —,
 * der Name der Firma an jeder Zeile und der Bruttobetrag. Der Steuersatz kommt dabei aus zwei
 * Quellen: Ein Entwurf hat noch keinen und rechnet mit dem der aktuellen Einstellungen, eine
 * gestellte Rechnung mit ihrem eigenen, festgeschriebenen (Kriterium 14).
 */
@ExtendWith(MockitoExtension.class)
class RechnungenUebersichtUseCaseTest {

  private static final long ZWEITES_ANGEBOT = 12L;
  private static final long ZWEITE_FIRMA = 6L;

  @Mock private RechnungRepository rechnungen;
  @Mock private AngebotRepository angebote;
  @Mock private FirmaRepository firmen;
  @Mock private RechnungseinstellungenRepository einstellungen;

  @Captor private ArgumentCaptor<Collection<Long>> gefragteFirmen;

  private RechnungenUebersichtUseCase useCase;

  @BeforeEach
  void baueDenAnwendungsfall() {
    useCase = new RechnungenUebersichtUseCase(rechnungen, angebote, firmen, einstellungen);
  }

  private void gegebeneEinstellungen(final String steuersatz) {
    when(einstellungen.lies())
        .thenReturn(
            new Rechnungseinstellungen(
                new Nummernmuster("R{JJ}-{NNNN}"), new BigDecimal(steuersatz), 10));
  }

  @Test
  void rechnungen_thenNewestFirstAndAtTheSameDatumTheHigherId() {
    // Given — zwei Rechnungen am selben Tag, eine spaetere davor.
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0001",
                    List.of(Rechnungsdoppel.beratung("80.00")),
                    LocalDate.of(2026, 9, 30)),
                Rechnungsdoppel.gestellt(
                    3L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0003",
                    List.of(Rechnungsdoppel.beratung("10.00")),
                    LocalDate.of(2026, 9, 30)),
                Rechnungsdoppel.gestellt(
                    2L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0002",
                    List.of(Rechnungsdoppel.beratung("20.00")),
                    LocalDate.of(2026, 10, 5))));
    when(angebote.findAlle(Optional.empty())).thenReturn(List.of(Rechnungsdoppel.angebot()));
    when(firmen.findAllById(any()))
        .thenReturn(List.of(Rechnungsdoppel.firma(Rechnungsdoppel.FIRMA, "Adler AG")));
    gegebeneEinstellungen("19.00");

    // When
    final List<RechnungMitFirma> zeilen = useCase.rechnungen();

    // Then
    assertThat(zeilen)
        .extracting(zeile -> zeile.rechnung().requireId())
        .containsExactly(2L, 3L, 1L);
  }

  @Test
  void rechnungen_thenEachZeileCarriesTheIdAndNameOfItsFirma() {
    // Given — zwei Angebote an zwei Firmen.
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L,
                    Rechnungsdoppel.ANGEBOT,
                    "R26-0001",
                    List.of(Rechnungsdoppel.beratung("80.00")),
                    LocalDate.of(2026, 9, 30)),
                Rechnungsdoppel.entwurf(
                    2L, ZWEITES_ANGEBOT, List.of(Rechnungsdoppel.beratung("10.00")))));
    when(angebote.findAlle(Optional.empty()))
        .thenReturn(
            List.of(
                Rechnungsdoppel.angebot(),
                new Angebot(
                    Long.valueOf(ZWEITES_ANGEBOT),
                    ZWEITE_FIRMA,
                    null,
                    Angebotsstatus.BESTELLT,
                    Rechnungsdoppel.ANGEBOTSDATUM,
                    null,
                    List.of(Rechnungsdoppel.BERATUNG),
                    Rechnungsdoppel.ANGELEGT,
                    Rechnungsdoppel.ANGELEGT)));
    when(firmen.findAllById(any()))
        .thenReturn(
            List.of(
                Rechnungsdoppel.firma(Rechnungsdoppel.FIRMA, "Adler AG"),
                Rechnungsdoppel.firma(ZWEITE_FIRMA, "Biber GmbH")));
    gegebeneEinstellungen("19.00");

    // When
    final List<RechnungMitFirma> zeilen = useCase.rechnungen();

    // Then
    assertThat(zeilen)
        .extracting(
            zeile -> zeile.rechnung().requireId(),
            RechnungMitFirma::firmaId,
            RechnungMitFirma::firmaName)
        .containsExactlyInAnyOrder(
            tuple(1L, Rechnungsdoppel.FIRMA, "Adler AG"), tuple(2L, ZWEITE_FIRMA, "Biber GmbH"));
    verify(firmen).findAllById(gefragteFirmen.capture());
    assertThat(gefragteFirmen.getValue())
        .containsExactlyInAnyOrder(Rechnungsdoppel.FIRMA, ZWEITE_FIRMA);
  }

  @Test
  void rechnungen_forAnEntwurf_thenTheBruttoUsesTheSteuersatzOfTheEinstellungen() {
    // Given — 80 Stunden zu 100,00 € netto, 19 Prozent aus den Einstellungen.
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(Rechnungsdoppel.entwurf(2L, List.of(Rechnungsdoppel.beratung("80.00")))));
    when(angebote.findAlle(Optional.empty())).thenReturn(List.of(Rechnungsdoppel.angebot()));
    when(firmen.findAllById(any()))
        .thenReturn(List.of(Rechnungsdoppel.firma(Rechnungsdoppel.FIRMA, "Adler AG")));
    gegebeneEinstellungen("19.00");

    // When
    final List<RechnungMitFirma> zeilen = useCase.rechnungen();

    // Then
    assertThat(zeilen)
        .singleElement()
        .extracting(RechnungMitFirma::brutto)
        .isEqualTo(new BigDecimal("9520.00"));
  }

  @Test
  void rechnungen_forAGestellteRechnung_thenTheBruttoUsesItsOwnSteuersatz() {
    // Given — die gestellte Rechnung traegt 7 Prozent, die Einstellungen stehen auf 19.
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(
                Rechnungsdoppel.gestellt(
                    1L, "R26-0001", List.of(Rechnungsdoppel.beratung("80.00")))));
    when(angebote.findAlle(Optional.empty())).thenReturn(List.of(Rechnungsdoppel.angebot()));
    when(firmen.findAllById(any()))
        .thenReturn(List.of(Rechnungsdoppel.firma(Rechnungsdoppel.FIRMA, "Adler AG")));
    gegebeneEinstellungen("19.00");

    // When
    final List<RechnungMitFirma> zeilen = useCase.rechnungen();

    // Then
    assertThat(zeilen)
        .singleElement()
        .extracting(RechnungMitFirma::brutto)
        .isEqualTo(new BigDecimal("8560.00"));
  }

  @Test
  void rechnungen_whenThereAreNone_thenAnswersEmptyWithoutAskingFurther() {
    // Given
    when(rechnungen.findAlle()).thenReturn(List.of());

    // When
    final List<RechnungMitFirma> zeilen = useCase.rechnungen();

    // Then
    assertThat(zeilen).isEmpty();
    verifyNoInteractions(angebote, firmen, einstellungen);
  }

  @Test
  void rechnungen_whenTheAngebotOfARechnungIsMissing_thenItIsAContradictionInTheBestand() {
    // Given
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(Rechnungsdoppel.entwurf(2L, List.of(Rechnungsdoppel.beratung("10.00")))));
    when(angebote.findAlle(Optional.empty())).thenReturn(List.of());

    // When / Then
    assertThatThrownBy(() -> useCase.rechnungen()).isInstanceOf(AngebotNichtGefunden.class);
  }

  @Test
  void rechnungen_whenTheFirmaOfAnAngebotIsMissing_thenItIsAContradictionInTheBestand() {
    // Given — Firmen werden nie geloescht; fehlt der Name, stimmt der Bestand nicht.
    when(rechnungen.findAlle())
        .thenReturn(
            List.of(Rechnungsdoppel.entwurf(2L, List.of(Rechnungsdoppel.beratung("10.00")))));
    when(angebote.findAlle(Optional.empty())).thenReturn(List.of(Rechnungsdoppel.angebot()));
    when(firmen.findAllById(any())).thenReturn(List.of());
    gegebeneEinstellungen("19.00");

    // When / Then
    assertThatThrownBy(() -> useCase.rechnungen()).isInstanceOf(FirmaNichtGefunden.class);
  }
}
