package org.mwolff.fbcrm.angebot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mwolff.fbcrm.angebot.application.Druckdatendoppel.ABSENDER;
import static org.mwolff.fbcrm.angebot.application.Druckdatendoppel.BEDINGUNGEN;
import static org.mwolff.fbcrm.angebot.application.Druckdatendoppel.EMPFAENGER;
import static org.mwolff.fbcrm.angebot.application.Druckdatendoppel.LANGER_TEXT;
import static org.mwolff.fbcrm.angebot.application.Druckdatendoppel.NETTO_HINWEIS;
import static org.mwolff.fbcrm.angebot.application.Druckdatendoppel.SUMME;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.angebot.domain.Belegabsender;
import org.mwolff.fbcrm.angebot.domain.Belegempfaenger;
import org.mwolff.fbcrm.common.Abrechnungsmodus;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;

/**
 * Der Satz des Belegs — die Rechnung vor dem Druck (E10).
 *
 * <p>Gegenstand ist die Folge der Druckzeilen: welche Pflichtinhalte aus Kriterium 15 darin stehen,
 * wie lange Texte auf die Textbreite umgebrochen werden, wo eine zweite Seite beginnt und was ein
 * Angebot ohne Ansprechpartner, ohne Leistungsbeschreibung und ohne Zahlungsbedingungen zeigt. Die
 * Bibliothek kommt hier nicht vor — genau das ist der Grund fuer die Teilung: Umbruch und
 * Seitenwechsel sind reine Rechnung und als solche pruefbar.
 */
class BeleglayoutTest {

  /** Eine volle Zeile des langen Texts: 19 Woerter zu vier Zeichen. */
  private static final String VOLLE_ZEILE = "wort ".repeat(19).trim();

  private static final String RESTZEILE = "wort wort";

  /** Ein Wort ohne Leerzeichen; es fuellt in der Spalte der Bezeichnung drei Zeilen: 46, 46, 8. */
  private static final String LANGES_WORT = "x".repeat(100);

  /** Ein Wort, das die Spalte der Bezeichnung genau ausfuellt. */
  private static final String PASSENDES_WORT = "b".repeat(46);

  /** Ein Text, dessen zweites Wort genau bis an die Textbreite von 96 Zeichen reicht. */
  private static final String GENAU_PASSEND = "a".repeat(91) + " wort";

  /** Ein Text, dessen drittes Wort um ein Zeichen zu lang fuer die erste Zeile waere. */
  private static final String EIN_ZEICHEN_ZU_LANG = "a".repeat(93) + " b c";

  private static final Angebotsposition STUNDENPOSTEN =
      new Angebotsposition(
          "Betreuung", Abrechnungsmodus.AUFWAND, BigDecimal.TEN, Einheit.STUNDE, BigDecimal.ONE);

  private static List<Druckzeile> satz() {
    return Beleglayout.zeilen(Druckdatendoppel.standard());
  }

  private static List<Druckzeile> mehrseitigerSatz() {
    return Beleglayout.zeilen(Druckdatendoppel.mehrseitig());
  }

  private static List<Druckzeile> knapperSatz(
      final Belegabsender absender, final Belegempfaenger empfaenger) {
    return Beleglayout.zeilen(
        Druckdatendoppel.mit(absender, empfaenger, null, null, List.of(STUNDENPOSTEN), SUMME));
  }

  private static List<String> texte(final List<Druckzeile> zeilen) {
    return zeilen.stream().map(Druckzeile::text).toList();
  }

  private static Druckzeile mitText(final List<Druckzeile> zeilen, final String text) {
    return zeilen.stream().filter(zeile -> text.equals(zeile.text())).findFirst().orElseThrow();
  }

  @Test
  void zeilen_thenCarriesTheMandatoryContentsOfCriterion15() {
    // When
    final List<Druckzeile> zeilen = satz();

    // Then — Absender, Empfaenger, Nummer, Datum und Gueltigkeit in Leserichtung.
    assertThat(texte(zeilen))
        .containsSubsequence(
            "Manfred Wolff, Am Deich 2, 28199 Hansestadt",
            "Adler AG",
            "Angebot A-2026-001",
            "Angebotsdatum: 20.09.2026",
            "Gültig bis: 20.10.2026");
  }

  @Test
  void zeilen_thenEachPositionCarriesQuantityUnitPriceAndAmount() {
    // When
    final List<Druckzeile> zeilen = satz();

    // Then — Kriterium 15: Positionen mit Menge, Einheit, Einzelpreis und Betrag.
    assertThat(texte(zeilen))
        .contains("Konzeption", "2,5", "Personentag", "1.000,01 €", "2.500,03 €");
  }

  @Test
  void zeilen_thenTheQuantityCellsStandOnTheFirstLineOfTheirPosition() {
    // Given
    final List<Druckzeile> zeilen = satz();

    // When
    final Druckzeile bezeichnung = mitText(zeilen, "Konzeption");

    // Then — die Zahlen der Position liegen auf derselben Grundlinie wie ihre Bezeichnung.
    assertThat(mitText(zeilen, "2,5"))
        .extracting(Druckzeile::seite, Druckzeile::y)
        .containsExactly(bezeichnung.seite(), bezeichnung.y());
  }

  @Test
  void zeilen_thenTheUnitIsSpelledOutForEveryValue() {
    // Given — eine Position je Einheit; „PERSONENTAG" ist keine Beschriftung fuer einen Kunden.
    final AngebotDruckdaten daten =
        Druckdatendoppel.mit(
            ABSENDER,
            EMPFAENGER,
            null,
            null,
            List.of(STUNDENPOSTEN, Angebotsdoppel.KONZEPTION, Angebotsdoppel.SCHULUNG),
            SUMME);

    // When
    final List<Druckzeile> zeilen = Beleglayout.zeilen(daten);

    // Then
    assertThat(texte(zeilen)).contains("Stunde", "Personentag", "Pauschal");
  }

  @Test
  void zeilen_thenTheSumStandsUnderTheTable() {
    // When
    final List<Druckzeile> zeilen = satz();

    // Then
    assertThat(texte(zeilen)).containsSubsequence("Betrag", "Angebotssumme", "3.700,03 €");
  }

  @Test
  void zeilen_thenTheNetHintStandsBelowTheSum() {
    // Given
    final List<Druckzeile> zeilen = satz();

    // When
    final Druckzeile summe = mitText(zeilen, "Angebotssumme");
    final Druckzeile hinweis = mitText(zeilen, NETTO_HINWEIS);

    // Then — E10: ohne den Hinweis liest sich ein Nettobetrag als Bruttopreis.
    assertThat(hinweis.seite()).isEqualTo(summe.seite());
    assertThat(hinweis.y()).isLessThan(summe.y());
  }

  @Test
  void zeilen_thenTheTitleIsSetInBoldAndLarger() {
    // When
    final Druckzeile titel = mitText(satz(), "Angebot A-2026-001");

    // Then
    assertThat(titel)
        .extracting(Druckzeile::schrift, Druckzeile::groesse)
        .containsExactly(Schrift.FETT, 16);
  }

  @Test
  void zeilen_givenALongLeistungsbeschreibung_thenItIsWrappedAtTheTextWidth() {
    // When
    final List<Druckzeile> zeilen = satz();

    // Then — 40 Woerter zu vier Zeichen: 19, 19, 2.
    assertThat(texte(zeilen)).containsSubsequence(VOLLE_ZEILE, VOLLE_ZEILE, RESTZEILE);
  }

  @Test
  void zeilen_givenALongBezeichnung_thenItIsWrappedInsideItsColumn() {
    // Given — ein Wort ohne Leerzeichen laesst sich nur hart trennen.
    final Angebotsposition sperrig =
        new Angebotsposition(
            LANGES_WORT,
            Abrechnungsmodus.FESTPREIS,
            BigDecimal.ONE,
            Einheit.PAUSCHAL,
            BigDecimal.TEN);

    // When
    final List<Druckzeile> zeilen =
        Beleglayout.zeilen(
            Druckdatendoppel.mit(ABSENDER, EMPFAENGER, null, null, List.of(sperrig), SUMME));

    // Then — die Spalte der Bezeichnung traegt 46 Zeichen.
    assertThat(texte(zeilen)).containsSubsequence("x".repeat(46), "x".repeat(46), "x".repeat(8));
  }

  @Test
  void zeilen_thenTheTableHeadNamesEveryColumn() {
    // When
    final List<Druckzeile> zeilen = satz();

    // Then — eine Spalte ohne Kopf liesse offen, was die Zahl darin bedeutet.
    assertThat(texte(zeilen)).contains("Bezeichnung", "Menge", "Einheit", "Einzelpreis", "Betrag");
  }

  @Test
  void zeilen_thenTheSumIsSetOffFromTheLastPosition() {
    // Given
    final List<Druckzeile> zeilen = satz();

    // When
    final Druckzeile letztePosition = mitText(zeilen, "Schulungstag");

    // Then — eine Zeilenhoehe Abstand und dann die Summe: zwei Zeilenhoehen unter der letzten
    // Position. Ohne den Abstand klebte die Summe an der Tabelle.
    assertThat(mitText(zeilen, "Angebotssumme").y()).isEqualTo(letztePosition.y() - 28);
  }

  @Test
  void zeilen_thenTheTermsAreSetOffFromTheNetHint() {
    // Given
    final List<Druckzeile> zeilen = satz();

    // When
    final Druckzeile hinweis = mitText(zeilen, NETTO_HINWEIS);

    // Then — die Hoehe der kleinen Zeile und ein Absatzabstand darunter.
    assertThat(mitText(zeilen, "Zahlungsbedingungen").y()).isEqualTo(hinweis.y() - 39);
  }

  @Test
  void zeilen_givenAWrappedBezeichnung_thenEveryFurtherLineStandsOneLineLower() {
    // Given
    final Angebotsposition sperrig =
        new Angebotsposition(
            LANGES_WORT,
            Abrechnungsmodus.FESTPREIS,
            BigDecimal.ONE,
            Einheit.PAUSCHAL,
            BigDecimal.TEN);
    final List<Druckzeile> zeilen =
        Beleglayout.zeilen(
            Druckdatendoppel.mit(ABSENDER, EMPFAENGER, null, null, List.of(sperrig), SUMME));

    // When
    final Druckzeile erste = mitText(zeilen, "x".repeat(46));

    // Then — das dritte Stueck steht zwei Zeilen unter dem ersten; ohne den Vorschub je Stueck
    // lagen das zweite und das dritte auf derselben Grundlinie.
    assertThat(mitText(zeilen, "x".repeat(8)).y()).isEqualTo(erste.y() - 28);
  }

  @Test
  void zeilen_givenAWordThatReachesExactlyTheTextWidth_thenItStaysOnTheLine() {
    // Given — 91 Zeichen, ein Leerzeichen, vier Zeichen: genau 96.
    final AngebotDruckdaten daten =
        Druckdatendoppel.mit(
            ABSENDER, EMPFAENGER, GENAU_PASSEND, null, List.of(STUNDENPOSTEN), SUMME);

    // When
    final List<Druckzeile> zeilen = Beleglayout.zeilen(daten);

    // Then — was genau passt, wird nicht umgebrochen.
    assertThat(texte(zeilen)).contains(GENAU_PASSEND);
  }

  @Test
  void zeilen_givenAWordThatIsOneCharacterTooLong_thenItMovesToTheNextLine() {
    // Given — die erste Zeile fuellt 95 Zeichen; das naechste Wort braeuchte 97.
    final AngebotDruckdaten daten =
        Druckdatendoppel.mit(
            ABSENDER, EMPFAENGER, EIN_ZEICHEN_ZU_LANG, null, List.of(STUNDENPOSTEN), SUMME);

    // When
    final List<Druckzeile> zeilen = Beleglayout.zeilen(daten);

    // Then
    assertThat(texte(zeilen)).containsSubsequence("a".repeat(93) + " b", "c");
  }

  @Test
  void zeilen_givenABezeichnungThatFillsItsColumnExactly_thenItIsNotSplit() {
    // Given — 46 Zeichen, die Breite der Spalte.
    final Angebotsposition passend =
        new Angebotsposition(
            PASSENDES_WORT,
            Abrechnungsmodus.FESTPREIS,
            BigDecimal.ONE,
            Einheit.PAUSCHAL,
            BigDecimal.TEN);

    // When
    final List<Druckzeile> zeilen =
        Beleglayout.zeilen(
            Druckdatendoppel.mit(ABSENDER, EMPFAENGER, null, null, List.of(passend), SUMME));

    // Then — kein Reststueck und damit keine leere zweite Zeile.
    assertThat(texte(zeilen)).contains(PASSENDES_WORT).doesNotContain("");
  }

  @Test
  void zeilen_thenNoTextIsWiderThanItsColumn() {
    // Given — der lange Text am Absatz und in der Spalte zugleich.
    final Angebotsposition sperrig =
        new Angebotsposition(
            LANGER_TEXT, Abrechnungsmodus.AUFWAND, BigDecimal.ONE, Einheit.STUNDE, BigDecimal.TEN);

    // When
    final List<Druckzeile> zeilen =
        Beleglayout.zeilen(
            Druckdatendoppel.mit(
                ABSENDER, EMPFAENGER, LANGER_TEXT, LANGER_TEXT, List.of(sperrig), SUMME));

    // Then — keine Zeile laeuft aus dem Satzspiegel heraus.
    assertThat(texte(zeilen)).allSatisfy(text -> assertThat(text).hasSizeLessThanOrEqualTo(96));
  }

  @Test
  void zeilen_givenManyPositions_thenTheTableContinuesOnASecondPage() {
    // When
    final List<Druckzeile> zeilen = mehrseitigerSatz();

    // Then
    assertThat(zeilen).extracting(Druckzeile::seite).contains(2);
  }

  @Test
  void zeilen_givenManyPositions_thenTheTableHeadIsRepeatedOnTheNewPage() {
    // When
    final List<Druckzeile> zeilen = mehrseitigerSatz();

    // Then — eine fortgesetzte Tabelle ohne Spaltenkopf ist nicht lesbar.
    assertThat(texte(zeilen)).filteredOn("Bezeichnung"::equals).hasSize(2);
  }

  @Test
  void zeilen_givenARowEndingExactlyOnTheLowerMargin_thenItStaysOnThePage() {
    // Given
    final List<Druckzeile> zeilen = mehrseitigerSatz();

    // When
    final Druckzeile letzte = mitText(zeilen, "Posten 29");

    // Then — die Grundlinie liegt genau auf dem unteren Rand des Satzspiegels; erst die naechste
    // Zeile fiele darunter und beginnt deshalb die neue Seite.
    assertThat(letzte).extracting(Druckzeile::seite, Druckzeile::y).containsExactly(1, 85);
    assertThat(mitText(zeilen, "Posten 30").seite()).isEqualTo(2);
  }

  @Test
  void zeilen_givenFewPositions_thenEverythingStaysOnTheFirstPage() {
    // When
    final List<Druckzeile> zeilen = satz();

    // Then
    assertThat(zeilen).extracting(Druckzeile::seite).containsOnly(1);
  }

  @Test
  void zeilen_givenNoAnsprechpartner_thenOnlyTheCompanyAddressIsPrinted() {
    // Given — Kriterium 12: ein Ansprechpartner ist nicht noetig.
    final Belegempfaenger ohnePerson =
        new Belegempfaenger("Adler AG", EMPFAENGER.anschrift(), null);

    // When
    final List<Druckzeile> zeilen = knapperSatz(ABSENDER, ohnePerson);

    // Then
    assertThat(texte(zeilen))
        .containsSubsequence("Adler AG", "Hauptstrasse 1", "28195 Bremen", "Deutschland")
        .doesNotContain("Frau Dr. Adler");
  }

  @Test
  void zeilen_givenAnAnsprechpartner_thenTheNameStandsUnderTheCompany() {
    // When
    final List<Druckzeile> zeilen = satz();

    // Then
    assertThat(texte(zeilen))
        .containsSubsequence("Adler AG", "Frau Dr. Adler", "Hauptstrasse 1", "28195 Bremen");
  }

  @Test
  void zeilen_givenAnEmptyRecipientAddress_thenNoBlankLineIsPrinted() {
    // Given — jede Angabe der Anschrift darf fehlen (common.Anschrift).
    final Belegempfaenger nurName =
        new Belegempfaenger("Adler AG", new Anschrift(null, null, null, null), null);

    // When
    final List<Druckzeile> zeilen = knapperSatz(ABSENDER, nurName);

    // Then
    assertThat(texte(zeilen)).contains("Adler AG").doesNotContain("");
  }

  @Test
  void zeilen_givenNoLeistungsbeschreibung_thenTheTableFollowsTheDates() {
    // When
    final List<Druckzeile> zeilen = knapperSatz(ABSENDER, EMPFAENGER);

    // Then
    assertThat(texte(zeilen))
        .containsSubsequence("Gültig bis: 20.10.2026", "Bezeichnung")
        .doesNotContain("");
  }

  @Test
  void zeilen_givenEmptyTexts_thenTheyCountAsAbsent() {
    // Given — ein geleertes Textfeld ist keine Angabe, sondern eine fehlende.
    final AngebotDruckdaten daten =
        Druckdatendoppel.mit(ABSENDER, EMPFAENGER, "", "", List.of(STUNDENPOSTEN), SUMME);

    // When
    final List<Druckzeile> zeilen = Beleglayout.zeilen(daten);

    // Then
    assertThat(texte(zeilen))
        .containsSubsequence("Gültig bis: 20.10.2026", "Bezeichnung")
        .doesNotContain("", "Zahlungsbedingungen");
  }

  @Test
  void zeilen_givenZahlungsbedingungen_thenTheyStandUnderTheirHeading() {
    // When
    final List<Druckzeile> zeilen = satz();

    // Then — Kriterium 15 verlangt die Zahlungsbedingungen auf dem Beleg.
    assertThat(texte(zeilen)).containsSubsequence("Zahlungsbedingungen", BEDINGUNGEN);
  }

  @Test
  void zeilen_givenNoZahlungsbedingungen_thenTheHeadingIsOmitted() {
    // When
    final List<Druckzeile> zeilen = knapperSatz(ABSENDER, EMPFAENGER);

    // Then — eine Ueberschrift ohne Text waere eine leere Zusage.
    assertThat(texte(zeilen)).doesNotContain("Zahlungsbedingungen");
  }

  @Test
  void zeilen_thenTheSenderDetailsStandInTheFooter() {
    // When
    final List<Druckzeile> zeilen = satz();

    // Then — Kriterium 15: die Absenderangaben aus „Eigene Angaben".
    assertThat(texte(zeilen))
        .containsSubsequence(
            "E-Mail: manne@example.org",
            "Telefon: 0421 123456",
            "Steuernummer: 12/345/67890",
            "USt-IdNr.: DE123456789",
            "Bankverbindung: Sparkasse, IBAN DE02 1203 0000 0000 2020 51");
  }

  @Test
  void zeilen_givenASenderWithoutContactDetails_thenNoLabelStandsAlone() {
    // Given — unter „Eigene Angaben" sind nur Name und Anschrift Pflicht (F12).
    final Belegabsender knapp =
        new Belegabsender(
            "Manfred Wolff",
            new Anschrift("Am Deich 2", null, null, null),
            null,
            null,
            null,
            null,
            null);

    // When
    final List<Druckzeile> zeilen = knapperSatz(knapp, EMPFAENGER);

    // Then
    assertThat(texte(zeilen))
        .contains("Manfred Wolff, Am Deich 2")
        .noneMatch(text -> text.startsWith("E-Mail"));
  }
}
