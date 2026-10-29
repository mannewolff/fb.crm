package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.rechnung.application.RechnungDruckdaten.Druckposition;
import org.mwolff.fbcrm.rechnung.domain.Belegabsender;
import org.mwolff.fbcrm.rechnung.domain.Belegempfaenger;

/**
 * Die Positionstabelle fuer sich: Spaltenkoepfe, Positionszeilen mit Spaltenumbruch, Seitenwechsel
 * und Summenblock, gesetzt in einem eingestellten {@link Satzspiegel}.
 *
 * <p>Die Masse sind die der Kupferwolke, solange ein Test nicht ausdruecklich einen anderen
 * Satzspiegel braucht. Die Tabelle beginnt in jedem Test bei {@value #OBEN}: Ihre Kopfzeile reicht
 * bis 449, und von dort zaehlen die Positionen nach unten.
 */
class PositionstabelleTest {

  /** Die obere Linie der Tabelle in jedem Test. */
  private static final int OBEN = 467;

  /** Der Satzspiegel der Kupferwolke. */
  private static final Satzspiegel KUPFERWOLKE =
      new Satzspiegel(71, 136, 371, 436, 503, 120, 100, 780);

  private static final Farbe SCHWARZ = new Farbe(0, 0, 0);

  /** Was der Rueckruf auf ein neues Blatt legt, damit ein Test es wiederfindet. */
  private static final Farbe MARKE = new Farbe(1, 2, 3);

  private final List<Druckelement> elemente = new ArrayList<>();

  private final List<Integer> neueBlaetter = new ArrayList<>();

  private static Satzspiegel mitTabellenuntergrenze(final int untergrenze) {
    return new Satzspiegel(71, 136, 371, 436, 503, untergrenze, 100, 780);
  }

  private static Druckposition position(final Einheit einheit, final String text) {
    return new Druckposition(
        BigDecimal.ONE, einheit, text, new BigDecimal("120.00"), new BigDecimal("120.00"));
  }

  private static List<Druckposition> positionen(final int anzahl) {
    return IntStream.rangeClosed(1, anzahl)
        .mapToObj(nummer -> position(Einheit.STUNDE, "Leistung " + nummer))
        .toList();
  }

  private static RechnungDruckdaten daten(final List<Druckposition> positionen) {
    return new RechnungDruckdaten(
        new Belegabsender(
            "Max Mustermann",
            null,
            new Anschrift(null, null, null, null),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null),
        new Belegempfaenger(
            "Franz Mustermann GmbH", new Anschrift(null, null, null, null), null, null),
        "0003-2026",
        LocalDate.of(2026, 9, 28),
        null,
        positionen,
        new BigDecimal("1760.00"),
        new BigDecimal("19"),
        new BigDecimal("334.40"),
        new BigDecimal("2094.40"),
        10);
  }

  /** Die Tabelle, deren Rueckruf jedes neue Blatt notiert und eine Marke darauf legt. */
  private Positionstabelle mitRueckruf(final Satzspiegel spiegel) {
    return new Positionstabelle(
        spiegel,
        elemente,
        blatt -> {
          neueBlaetter.add(blatt);
          elemente.add(new Druckelement.Flaeche(blatt, 0, 0, 1, 1, MARKE));
        });
  }

  /** Die Tabelle, auf deren neuem Blatt zuerst nichts entsteht — wie spaeter auf dem Briefbogen. */
  private Positionstabelle ohneRueckruf(final Satzspiegel spiegel) {
    return new Positionstabelle(spiegel, elemente, blatt -> {});
  }

  private List<Druckelement.Text> texte() {
    return elemente.stream()
        .filter(Druckelement.Text.class::isInstance)
        .map(Druckelement.Text.class::cast)
        .toList();
  }

  private Druckelement.Text text(final String inhalt) {
    return texte().stream()
        .filter(gesetzt -> gesetzt.text().equals(inhalt))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Nicht gesetzt: " + inhalt));
  }

  private List<Druckelement> aufSeite(final int seite) {
    return elemente.stream().filter(element -> element.seite() == seite).toList();
  }

  @Test
  void setze_givenEinePosition_thenTheSpaltenkoepfeStehenAnIhrenKantenInDerKopfzeile() {
    // Given
    final Positionstabelle tabelle = mitRueckruf(KUPFERWOLKE);

    // When
    tabelle.setze(daten(positionen(1)), OBEN);

    // Then — vier Punkte hinter der Kante, fuenf ueber der Linie unter der Kopfzeile.
    assertThat(texte())
        .extracting(
            Druckelement.Text::text,
            Druckelement.Text::x,
            Druckelement.Text::y,
            Druckelement.Text::ausrichtung)
        .startsWith(
            tuple("Anzahl", 75, 454, Ausrichtung.LINKS),
            tuple("Position", 140, 454, Ausrichtung.LINKS),
            tuple("Einzelpreis", 375, 454, Ausrichtung.LINKS),
            tuple("Gesamtpreis", 440, 454, Ausrichtung.LINKS));
  }

  @Test
  void setze_givenEinePosition_thenAnzahlUndPreiseStehenRechtsbuendigVorIhrerKante() {
    // Given
    final Positionstabelle tabelle = mitRueckruf(KUPFERWOLKE);

    // When
    tabelle.setze(daten(List.of(position(Einheit.PERSONENTAG, "Workshop"))), OBEN);

    // Then — die Einheit steht vor dem Text, die Zahlen an der rechten Kante ihrer Spalte.
    assertThat(texte())
        .extracting(
            Druckelement.Text::text,
            Druckelement.Text::x,
            Druckelement.Text::y,
            Druckelement.Text::ausrichtung)
        .contains(
            tuple("1", 132, 438, Ausrichtung.RECHTS),
            tuple("120,00 €", 432, 438, Ausrichtung.RECHTS),
            tuple("Personentage Workshop", 140, 438, Ausrichtung.LINKS));
  }

  @Test
  void setze_givenASchmalereSpaltePosition_thenTheTextBrichtAnIhrerBreiteUm() {
    // Given — die Spalte „Position" ist hier 115 statt 235 Punkte breit und traegt 19 Zeichen; im
    // Satzspiegel der Kupferwolke stuende derselbe Text auf einer Zeile.
    final Satzspiegel schmal = new Satzspiegel(71, 136, 251, 316, 383, 120, 100, 780);
    final Positionstabelle tabelle = mitRueckruf(schmal);

    // When
    tabelle.setze(daten(List.of(position(Einheit.PAUSCHAL, "Begleitung der Einfuehrung"))), OBEN);

    // Then
    assertThat(texte())
        .filteredOn(gesetzt -> gesetzt.x() == 140 && gesetzt.y() != 454)
        .extracting(Druckelement.Text::text, Druckelement.Text::y)
        .containsExactly(tuple("Begleitung der", 438), tuple("Einfuehrung", 422));
  }

  @Test
  void setze_givenEinePosition_thenTheSummenblockFolgtIhrUndTheUntersteLinieKommtZurueck() {
    // Given
    final Positionstabelle tabelle = mitRueckruf(KUPFERWOLKE);

    // When
    final int unten = tabelle.setze(daten(positionen(1)), OBEN);

    // Then — drei Zeilen zu je 16 unter der Position bei 433, darunter der Doppelstrich.
    assertThat(texte())
        .extracting(
            Druckelement.Text::text,
            Druckelement.Text::x,
            Druckelement.Text::y,
            Druckelement.Text::ausrichtung)
        .endsWith(
            tuple("Gesamtbetrag netto", 367, 422, Ausrichtung.RECHTS),
            tuple("1.760,00 €", 499, 422, Ausrichtung.RECHTS),
            tuple("+ 19% Mehrwertsteuer", 367, 406, Ausrichtung.RECHTS),
            tuple("334,40 €", 499, 406, Ausrichtung.RECHTS),
            tuple("Gesamtbetrag brutto", 367, 390, Ausrichtung.RECHTS),
            tuple("2.094,40 €", 499, 390, Ausrichtung.RECHTS));
    assertThat(unten).isEqualTo(383);
  }

  @Test
  void setze_givenEinePosition_thenTheRahmenSchliesstMitDenSenkrechtenAnDenSpaltenkanten() {
    // Given
    final Positionstabelle tabelle = mitRueckruf(KUPFERWOLKE);

    // When
    tabelle.setze(daten(positionen(1)), OBEN);

    // Then — der Doppelstrich unter dem Brutto, dann die fuenf Senkrechten bis zur obersten Linie.
    assertThat(elemente.subList(elemente.size() - 6, elemente.size()))
        .containsExactly(
            new Druckelement.Linie(1, 71, 383, 503, 383, 1, SCHWARZ),
            new Druckelement.Linie(1, 71, 385, 71, 467, 1, SCHWARZ),
            new Druckelement.Linie(1, 136, 385, 136, 467, 1, SCHWARZ),
            new Druckelement.Linie(1, 371, 385, 371, 467, 1, SCHWARZ),
            new Druckelement.Linie(1, 436, 385, 436, 467, 1, SCHWARZ),
            new Druckelement.Linie(1, 503, 385, 503, 467, 1, SCHWARZ));
  }

  @Test
  void setze_givenRestGenauGleichDerTabellenuntergrenze_thenThePositionBleibtAufDerSeite() {
    // Given — von 449 an braucht die einzige Position 16 und der Summenblock dahinter 50: Es
    // bleiben genau 383. Eine Untergrenze von 383 wird damit gerade noch gehalten.
    final Positionstabelle tabelle = mitRueckruf(mitTabellenuntergrenze(383));

    // When
    tabelle.setze(daten(positionen(1)), OBEN);

    // Then
    assertThat(tabelle.seite()).isEqualTo(1);
    assertThat(neueBlaetter).isEmpty();
  }

  @Test
  void setze_givenRestEinenPunktUnterDerTabellenuntergrenze_thenThePositionGehtAufsNeueBlatt() {
    // Given — dieselbe Position, die Untergrenze einen Punkt hoeher: Der Rest von 383 haelt sie
    // nicht mehr.
    final Positionstabelle tabelle = mitRueckruf(mitTabellenuntergrenze(384));

    // When
    tabelle.setze(daten(positionen(1)), OBEN);

    // Then
    assertThat(tabelle.seite()).isEqualTo(2);
    assertThat(text("Stunden Leistung 1").seite()).isEqualTo(2);
  }

  @Test
  void setze_givenASeitenwechselMitRueckruf_thenEntstehtSeinElementZuerstAufDemNeuenBlatt() {
    // Given
    final Positionstabelle tabelle = mitRueckruf(mitTabellenuntergrenze(384));

    // When
    tabelle.setze(daten(positionen(1)), OBEN);

    // Then — der Rueckruf hoert die neue Seite; danach beginnt der Rahmen oben auf ihr.
    assertThat(neueBlaetter).containsExactly(2);
    assertThat(aufSeite(2))
        .startsWith(
            new Druckelement.Flaeche(2, 0, 0, 1, 1, MARKE),
            new Druckelement.Linie(2, 71, 780, 503, 780, 1, SCHWARZ));
  }

  @Test
  void setze_givenASeitenwechselOhneRueckruf_thenBeginntDasNeueBlattMitDerOberstenLinie() {
    // Given
    final Positionstabelle tabelle = ohneRueckruf(mitTabellenuntergrenze(384));

    // When
    tabelle.setze(daten(positionen(1)), OBEN);

    // Then — auf Seite eins bleiben nur Kopfzeile und geschlossener Rahmen zurueck.
    assertThat(aufSeite(2))
        .first()
        .isEqualTo(new Druckelement.Linie(2, 71, 780, 503, 780, 1, SCHWARZ));
    assertThat(aufSeite(1))
        .filteredOn(Druckelement.Linie.class::isInstance)
        .map(Druckelement.Linie.class::cast)
        .filteredOn(linie -> linie.vonX() == linie.bisX())
        .extracting(Druckelement.Linie::vonY, Druckelement.Linie::bisY)
        .containsOnly(tuple(449, 467));
  }

  @ParameterizedTest
  @CsvSource({"1, 1", "18, 2", "58, 2", "59, 3"})
  void seite_givenSoVielePositionen_thenSindSoVieleSeitenBenutzt(
      final int anzahl, final int seiten) {
    // Given — auf Seite eins stehen bis zu 20 Positionen, auf jeder Folgeseite ab 780 bis zu 41;
    // die letzte legt dazu die 50 des Summenblocks zurueck.
    final Positionstabelle tabelle = mitRueckruf(KUPFERWOLKE);

    // When
    tabelle.setze(daten(positionen(anzahl)), OBEN);

    // Then
    assertThat(tabelle.seite()).isEqualTo(seiten);
  }

  @Test
  void seite_givenNochNichtsGesetzt_thenIstEsDieErsteSeite() {
    // When
    final Positionstabelle tabelle = mitRueckruf(KUPFERWOLKE);

    // Then
    assertThat(tabelle.seite()).isEqualTo(1);
  }

  @Test
  void platzFuer_givenRestGenauGleichDerInhaltsuntergrenze_thenBleibtDerBlockAufDerSeite() {
    // Given — von 200 an reicht der Block 100 tief und endet genau auf der Untergrenze 100.
    final Positionstabelle tabelle = mitRueckruf(KUPFERWOLKE);

    // When
    final int anfang = tabelle.platzFuer(200, 100);

    // Then
    assertThat(anfang).isEqualTo(200);
    assertThat(tabelle.seite()).isEqualTo(1);
    assertThat(neueBlaetter).isEmpty();
  }

  @Test
  void platzFuer_givenRestEinenPunktUnterDerInhaltsuntergrenze_thenBeginntDerBlockOben() {
    // Given — ein Punkt tiefer, und der Block reichte unter die Untergrenze.
    final Positionstabelle tabelle = mitRueckruf(KUPFERWOLKE);

    // When
    final int anfang = tabelle.platzFuer(199, 100);

    // Then — er beginnt auf der obersten Grundlinie des neuen Blattes.
    assertThat(anfang).isEqualTo(780);
    assertThat(tabelle.seite()).isEqualTo(2);
    assertThat(neueBlaetter).containsExactly(2);
  }

  @Test
  void platzFuer_givenEinenEigenenSatzspiegelFuerFolgeseiten_thenGiltErAbDemZweitenBlatt() {
    // Given — Folgeseiten beginnen bei 700 und enden bei 600; Seite eins traegt die Kupferwolke.
    final Satzspiegel folgeseiten = new Satzspiegel(71, 136, 371, 436, 503, 620, 600, 700);
    final Positionstabelle tabelle =
        new Positionstabelle(KUPFERWOLKE, folgeseiten, elemente, neueBlaetter::add);

    // When — der erste Block passt auf Seite eins nicht mehr, der zweite reichte auf dem neuen
    // Blatt unter dessen Untergrenze, obwohl er die der ersten Seite hielte.
    final int ersterAnfang = tabelle.platzFuer(199, 100);
    final int zweiterAnfang = tabelle.platzFuer(650, 100);

    // Then
    assertThat(ersterAnfang).isEqualTo(700);
    assertThat(zweiterAnfang).isEqualTo(700);
    assertThat(neueBlaetter).containsExactly(2, 3);
  }

  @Test
  void
      setze_givenEinenEigenenSatzspiegelFuerFolgeseiten_thenBrichtDieTabelleDortAnSeinerGrenzeUm() {
    // Given — auf Seite eins stehen 20 Positionen. Eine Folgeseite von 700 bis 600 haelt sechs
    // Zeilen, die letzte Position legt aber die 50 des Summenblocks dazu zurueck.
    final Satzspiegel folgeseiten = new Satzspiegel(71, 136, 371, 436, 503, 600, 100, 700);
    final Positionstabelle tabelle =
        new Positionstabelle(KUPFERWOLKE, folgeseiten, elemente, neueBlaetter::add);

    // When
    tabelle.setze(daten(positionen(24)), OBEN);

    // Then — Position 21 eroeffnet Seite zwei an ihrer oberen Kante; Position 24 fehlt dort der
    // Platz fuer sich und den Summenblock, sie steht auf Seite drei.
    assertThat(text("Stunden Leistung 21").seite()).isEqualTo(2);
    assertThat(text("Stunden Leistung 21").y()).isEqualTo(689);
    assertThat(text("Stunden Leistung 24").seite()).isEqualTo(3);
  }
}
