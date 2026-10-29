package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.rechnung.domain.Belegabsender;

/** Wie Betrag, Zahl und zusammengesetzte Zeilen auf dem Beleg geschrieben werden. */
class BelegschreibweisenTest {

  private static Belegabsender absender(
      final @Nullable String steuernummer, final @Nullable String umsatzsteuerId) {
    return new Belegabsender(
        "Max Mustermann",
        null,
        new Anschrift(null, null, null, null),
        null,
        null,
        steuernummer,
        umsatzsteuerId,
        null,
        null,
        null,
        null,
        null);
  }

  private static Belegabsender bank(
      final @Nullable String bankverbindung,
      final @Nullable String iban,
      final @Nullable String bic,
      final @Nullable String kontoinhaber) {
    return new Belegabsender(
        "Max Mustermann",
        null,
        new Anschrift(null, null, null, null),
        null,
        null,
        null,
        null,
        bankverbindung,
        null,
        iban,
        bic,
        kontoinhaber);
  }

  @Test
  void bankzeile_givenKontoinhaberIbanUndBic_thenStehtDieIbanInVierergruppen() {
    // When
    final String zeile =
        Belegschreibweisen.bankzeile(
            bank(null, "DE89370400440532013000", "COBADEFFXXX", "Manfred Wolff"));

    // Then — Kriterium 8 von #460 im Wortlaut.
    assertThat(zeile)
        .isEqualTo(
            "Bankverbindung: Manfred Wolff · IBAN DE89 3704 0044 0532 0130 00 · BIC COBADEFFXXX");
  }

  @Test
  void bankzeile_givenKeineBic_thenEndetDieZeileNachDerIban() {
    // When
    final String zeile =
        Belegschreibweisen.bankzeile(bank(null, "DE89370400440532013000", null, "Manfred Wolff"));

    // Then
    assertThat(zeile).isEqualTo("Bankverbindung: Manfred Wolff · IBAN DE89 3704 0044 0532 0130 00");
  }

  @Test
  void bankzeile_givenIbanUndAltenText_thenZaehltDieIban() {
    // When
    final String zeile =
        Belegschreibweisen.bankzeile(
            bank("MLP Bank Heidelberg", "DE89370400440532013000", null, "Manfred Wolff"));

    // Then — eine Quelle fuer die Zeile (E16).
    assertThat(zeile).isEqualTo("Bankverbindung: Manfred Wolff · IBAN DE89 3704 0044 0532 0130 00");
  }

  @Test
  void bankzeile_givenKeineIbanAberAltenText_thenStehtDerAlteText() {
    // When
    final String zeile =
        Belegschreibweisen.bankzeile(bank("MLP Bank Heidelberg", null, null, null));

    // Then — eine Rechnung von vor den Bankfeldern bleibt, wie sie war.
    assertThat(zeile).isEqualTo("Bankverbindung: MLP Bank Heidelberg");
  }

  @Test
  void bankzeile_givenWederIbanNochAltenText_thenEntstehtKeineZeile() {
    // When
    final String zeile = Belegschreibweisen.bankzeile(bank(null, null, null, null));

    // Then
    assertThat(zeile).isNull();
  }

  @ParameterizedTest
  @CsvSource({
    "0, '0,00 €'",
    "120, '120,00 €'",
    "1200.5, '1.200,50 €'",
    "1234567.891, '1.234.567,89 €'",
    "-80, '-80,00 €'"
  })
  void betrag_givenAWert_thenItTraegtTausenderpunktZweiStellenUndEurozeichen(
      final String wert, final String erwartet) {
    // When
    final String geschrieben = Belegschreibweisen.betrag(new BigDecimal(wert));

    // Then
    assertThat(geschrieben).isEqualTo(erwartet);
  }

  @ParameterizedTest
  @CsvSource({"3, 3", "3.00, 3", "2.5, '2,5'", "19, 19", "19.50, '19,5'", "100, 100", "0.0, 0"})
  void zahl_givenAWert_thenItStehtOhneNachnullenMitKomma(final String wert, final String erwartet) {
    // When
    final String geschrieben = Belegschreibweisen.zahl(new BigDecimal(wert));

    // Then — „100" bleibt „100" und wird nicht zu „1E+2".
    assertThat(geschrieben).isEqualTo(erwartet);
  }

  @Test
  void gefuellt_givenFehlendeWerte_thenTheyLassenKeineLuecke() {
    // When
    final var zeilen = Belegschreibweisen.gefuellt("eins", null, "zwei", null);

    // Then
    assertThat(zeilen).containsExactly("eins", "zwei");
  }

  @Test
  void verbunden_givenGefuellteUndFehlendeWerte_thenNurDieGefuelltenStehenZwischenTrennern() {
    // When
    final String zeile = Belegschreibweisen.verbunden(" · ", "Max", null, "Bremen");

    // Then
    assertThat(zeile).isEqualTo("Max · Bremen");
  }

  @Test
  void verbunden_givenKeinGefuellterWert_thenEntstehtKeineZeile() {
    // When
    final String zeile = Belegschreibweisen.verbunden(" · ", null, null);

    // Then — keine Zeile aus Trennern allein.
    assertThat(zeile).isNull();
  }

  @Test
  void mit_givenAWert_thenStehtDieBeschriftungDavor() {
    // When
    final String zeile = Belegschreibweisen.mit("Tel ", "0173");

    // Then
    assertThat(zeile).isEqualTo("Tel 0173");
  }

  @Test
  void mit_givenKeinWert_thenEntstehtKeineZeile() {
    // When
    final String zeile = Belegschreibweisen.mit("Tel ", null);

    // Then — eine Beschriftung ohne Wert wird nicht gesetzt.
    assertThat(zeile).isNull();
  }

  @ParameterizedTest
  @CsvSource(
      value = {"28197, Bremen, 28197 Bremen", "NULL, Wien, Wien", "28217, NULL, 28217"},
      nullValues = "NULL")
  void ortszeile_givenPostleitzahlUndOrt_thenStehenDieVorhandenen(
      final String plz, final String ort, final String erwartet) {
    // When
    final String zeile = Belegschreibweisen.ortszeile(new Anschrift(null, plz, ort, null));

    // Then
    assertThat(zeile).isEqualTo(erwartet);
  }

  @Test
  void ortszeile_givenWederPostleitzahlNochOrt_thenEntstehtKeineZeile() {
    // When
    final String zeile = Belegschreibweisen.ortszeile(new Anschrift("Weg 1", null, null, "DE"));

    // Then
    assertThat(zeile).isNull();
  }

  @Test
  void steuerzeile_givenUmsatzsteuerIdUndSteuernummer_thenNenntSieDieUmsatzsteuerId() {
    // When
    final String zeile = Belegschreibweisen.steuerzeile(absender("12/345/67890", "DE 01010101010"));

    // Then
    assertThat(zeile).isEqualTo("USt-IdNr. DE 01010101010");
  }

  @Test
  void steuerzeile_givenNurDieSteuernummer_thenNenntSieDieSteuernummer() {
    // When
    final String zeile = Belegschreibweisen.steuerzeile(absender("12/345/67890", null));

    // Then
    assertThat(zeile).isEqualTo("Steuernummer 12/345/67890");
  }

  @Test
  void steuerzeile_givenWederUmsatzsteuerIdNochSteuernummer_thenEntstehtKeineZeile() {
    // When
    final String zeile = Belegschreibweisen.steuerzeile(absender(null, null));

    // Then
    assertThat(zeile).isNull();
  }

  @Test
  void einleitungssatz_givenEinenLeistungszeitraum_thenNenntErIhn() {
    // When
    final String satz = Belegschreibweisen.einleitungssatz("September 2026");

    // Then
    assertThat(satz)
        .isEqualTo("Für meine Leistungen im September 2026 stelle ich Ihnen in Rechnung");
  }

  @Test
  void einleitungssatz_givenKeinenLeistungszeitraum_thenStehtErOhneIhn() {
    // When
    final String satz = Belegschreibweisen.einleitungssatz(null);

    // Then
    assertThat(satz).isEqualTo("Für meine Leistungen stelle ich Ihnen in Rechnung");
  }

  @Test
  void zahlungssatz_givenEinZahlungszielInTagen_thenNenntErDieTage() {
    // When
    final String satz = Belegschreibweisen.zahlungssatz(14);

    // Then
    assertThat(satz).isEqualTo("Bitte überweisen Sie den Betrag innerhalb von 14 Tagen");
  }

  @Test
  void zahlungssatz_givenNullTage_thenIstDerBetragSofortFaellig() {
    // When
    final String satz = Belegschreibweisen.zahlungssatz(0);

    // Then
    assertThat(satz).isEqualTo("Bitte überweisen Sie den Betrag sofort");
  }

  @Test
  void ortUndDatum_givenEinenOrt_thenStehtErVorDemDatum() {
    // When
    final String zeile = Belegschreibweisen.ortUndDatum("Bremen", LocalDate.of(2026, 9, 8));

    // Then
    assertThat(zeile).isEqualTo("Bremen, 08.09.2026");
  }

  @Test
  void ortUndDatum_givenKeinenOrt_thenStehtNurDasDatum() {
    // When
    final String zeile = Belegschreibweisen.ortUndDatum(null, LocalDate.of(2026, 9, 8));

    // Then
    assertThat(zeile).isEqualTo("08.09.2026");
  }
}
