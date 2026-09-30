package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.rechnung.application.RechnungDruckdaten.Druckposition;
import org.mwolff.fbcrm.rechnung.domain.Belegabsender;
import org.mwolff.fbcrm.rechnung.domain.Belegempfaenger;

/**
 * Der Satz der Rechnung gegen die verbindliche Vorlage {@code docs/vorlage-rechnung.pdf}.
 *
 * <p>Geprueft wird die Folge der Druckelemente: welcher Block ueber welchem steht, welche Wortlaute
 * im Klartext vorkommen, wie Zahl, Betrag und Satz geschrieben sind, was ohne die freiwilligen
 * Angaben wegfaellt und wo eine lange Rechnung umbricht. Wie das Ergebnis auf Papier aussieht, ist
 * nicht Gegenstand — dafuer steht die manuelle Pruefung am erzeugten Dokument.
 */
class RechnungslayoutTest {

  private static final Belegabsender ABSENDER =
      new Belegabsender(
          "Max Mustermann",
          "AGILE COACH · SOFTWARE ENGINEER · KI-PRAKTIKER",
          new Anschrift("Muster Str. 34a", "28197", "Bremen", null),
          "info@mmuster.org",
          "0173 0000 000",
          "12/345/67890",
          "DE 01010101010",
          "MLP Bank Heidelberg · IBAN DE00 xxxx xxxx xxxx xxxx xx",
          "mmuster.org");

  private static final Belegempfaenger EMPFAENGER =
      new Belegempfaenger(
          "Franz Mustermann GmbH", new Anschrift("Muster-Str. 24", "28217", "Bremen", null), null);

  /** Drei Positionen: eine in Stunden, eine in Personentagen, eine Pauschale. */
  private static final List<Druckposition> POSITIONEN =
      List.of(
          position("3", Einheit.STUNDE, "KI Beratung am 15.09.2026", "120.00", "360.00"),
          position("2.5", Einheit.PERSONENTAG, "Workshop", "480.00", "1200.00"),
          position("1", Einheit.PAUSCHAL, "Reisekosten", "200.00", "200.00"));

  private static Druckposition position(
      final String anzahl,
      final Einheit einheit,
      final String text,
      final String einzelpreis,
      final String gesamtpreis) {
    return new Druckposition(
        new BigDecimal(anzahl),
        einheit,
        text,
        new BigDecimal(einzelpreis),
        new BigDecimal(gesamtpreis));
  }

  private static RechnungDruckdaten daten(
      final Belegabsender absender,
      final Belegempfaenger empfaenger,
      final List<Druckposition> positionen,
      final String steuersatz,
      final int zahlungszielTage) {
    return new RechnungDruckdaten(
        absender,
        empfaenger,
        "0003-2026",
        LocalDate.of(2026, 9, 28),
        "September 2026",
        positionen,
        new BigDecimal("1760.00"),
        new BigDecimal(steuersatz),
        new BigDecimal("334.40"),
        new BigDecimal("2094.40"),
        zahlungszielTage);
  }

  private static RechnungDruckdaten vorlage() {
    return daten(ABSENDER, EMPFAENGER, POSITIONEN, "19", 10);
  }

  private static List<Druckelement.Text> texte(final List<Druckelement> elemente) {
    return elemente.stream()
        .filter(Druckelement.Text.class::isInstance)
        .map(Druckelement.Text.class::cast)
        .toList();
  }

  private static Druckelement.Text text(
      final List<Druckelement> elemente, final Predicate<Druckelement.Text> merkmal) {
    return texte(elemente).stream()
        .filter(merkmal)
        .findFirst()
        .orElseThrow(() -> new AssertionError("Kein Text mit diesem Merkmal gesetzt."));
  }

  private static Druckelement.Text text(final List<Druckelement> elemente, final String inhalt) {
    return text(elemente, gesetzt -> gesetzt.text().equals(inhalt));
  }

  private static List<String> inhalte(final List<Druckelement> elemente) {
    return texte(elemente).stream().map(Druckelement.Text::text).toList();
  }

  private static List<Integer> seiten(final List<Druckelement> elemente) {
    return elemente.stream().map(Druckelement::seite).distinct().sorted().toList();
  }

  /** Die Grundlinien der genannten Texte in der Reihenfolge, in der sie uebergeben wurden. */
  private static List<Integer> grundlinien(
      final List<Druckelement> elemente, final String... inhalte) {
    final List<Integer> hoehen = new ArrayList<>();
    for (final String inhalt : inhalte) {
      hoehen.add(text(elemente, inhalt).y());
    }
    return hoehen;
  }

  @Test
  void setze_givenDieAngabenDerVorlage_thenTheBloeckeStehenVonObenNachUnten() {
    // When
    final List<Druckelement> elemente = Rechnungslayout.setze(vorlage());

    // Then — die Folge des Aufbaus, von oben nach unten wie in docs/vorlage-rechnung.pdf.
    assertThat(
            grundlinien(
                elemente,
                "AGILE COACH · SOFTWARE ENGINEER · KI-PRAKTIKER",
                "mmuster.org",
                "Max Mustermann · Muster Str. 34a · 28197 Bremen",
                "Franz Mustermann GmbH",
                "Muster-Str. 24",
                "28217 Bremen",
                "Rechnung 0003-2026",
                "Sehr geehrte Damen und Herren,",
                "Für meine Leistungen im September 2026 stelle ich Ihnen in Rechnung",
                "Anzahl",
                "Stunden KI Beratung am 15.09.2026",
                "Reisekosten",
                "Gesamtbetrag netto",
                "+ 19% Mehrwertsteuer",
                "Gesamtbetrag brutto",
                "Bitte überweisen Sie den Betrag innerhalb von 10 Tagen",
                "Mit freundlichen Grüßen",
                "Max Mustermann · mmuster.org"))
        .isSortedAccordingTo(java.util.Comparator.reverseOrder());
  }

  @Test
  void setze_givenDieAngabenDerVorlage_thenTheErsteFlaecheIstDerRandstreifen() {
    // When
    final List<Druckelement> elemente = Rechnungslayout.setze(vorlage());

    // Then — der farbige Streifen liegt links, ueber die ganze Hoehe, und wird zuerst gesetzt.
    final Druckelement.Flaeche streifen =
        elemente.stream()
            .filter(Druckelement.Flaeche.class::isInstance)
            .map(Druckelement.Flaeche.class::cast)
            .findFirst()
            .orElseThrow();
    assertThat(streifen.x()).isZero();
    assertThat(streifen.y()).isZero();
    assertThat(streifen.breite()).isEqualTo(20);
    assertThat(streifen.hoehe()).isEqualTo(842);
    assertThat(streifen.farbe()).isEqualTo(new Farbe(47, 140, 151));
  }

  @Test
  void setze_givenDieAngabenDerVorlage_thenTheWortlauteStehenImKlartext() {
    // When
    final List<Druckelement> elemente = Rechnungslayout.setze(vorlage());

    // Then — die festen Saetze der Vorlage, Wort fuer Wort.
    assertThat(inhalte(elemente))
        .contains(
            "Rechnung 0003-2026",
            "Sehr geehrte Damen und Herren,",
            "Für meine Leistungen im September 2026 stelle ich Ihnen in Rechnung",
            "Bitte überweisen Sie den Betrag innerhalb von 10 Tagen",
            "Mit freundlichen Grüßen",
            "Bremen, 28.09.2026");
    assertThat(text(elemente, "Rechnung 0003-2026").schrift()).isEqualTo(Schrift.FETT);
  }

  @Test
  void setze_givenZahlungszielVonNullTagen_thenTheBetragIstSofortZuUeberweisen() {
    // When
    final List<Druckelement> elemente =
        Rechnungslayout.setze(daten(ABSENDER, EMPFAENGER, POSITIONEN, "19", 0));

    // Then — „innerhalb von 0 Tagen" ergaebe keinen Sinn (#159 nennt 0 Tage „sofort faellig").
    assertThat(inhalte(elemente)).contains("Bitte überweisen Sie den Betrag sofort");
  }

  @Test
  void setze_givenPositionenInAllenEinheiten_thenTheEinheitStehtVorDemText() {
    // When
    final List<Druckelement> elemente = Rechnungslayout.setze(vorlage());

    // Then — bei PAUSCHAL steht nur der Text.
    assertThat(inhalte(elemente))
        .contains("Stunden KI Beratung am 15.09.2026", "Personentage Workshop", "Reisekosten");
  }

  @Test
  void setze_givenMengenUndBetraege_thenTheyAreGeschriebenWieInDerVorlage() {
    // When
    final List<Druckelement> elemente = Rechnungslayout.setze(vorlage());

    // Then — Anzahl ohne Nachnullen, Betraege mit Tausenderpunkt und Eurozeichen.
    assertThat(inhalte(elemente)).contains("3", "2,5", "120,00 €", "1.200,00 €");
    assertThat(text(elemente, "120,00 €").ausrichtung()).isEqualTo(Ausrichtung.RECHTS);
    assertThat(text(elemente, "1.200,00 €").ausrichtung()).isEqualTo(Ausrichtung.RECHTS);
    assertThat(text(elemente, "3").ausrichtung()).isEqualTo(Ausrichtung.RECHTS);
  }

  @Test
  void setze_givenSteuersatzMitNachkommastelle_thenTheSatzStehtOhneNachnullen() {
    // When
    final List<Druckelement> ganz = Rechnungslayout.setze(vorlage());
    final List<Druckelement> halb =
        Rechnungslayout.setze(daten(ABSENDER, EMPFAENGER, POSITIONEN, "19.5", 10));

    // Then
    assertThat(inhalte(ganz)).contains("+ 19% Mehrwertsteuer");
    assertThat(inhalte(halb)).contains("+ 19,5% Mehrwertsteuer");
  }

  @Test
  void setze_givenKeineUmsatzsteuerId_thenTheFussNenntDieSteuernummer() {
    // Given
    final Belegabsender ohneId =
        new Belegabsender(
            ABSENDER.name(),
            ABSENDER.berufsbezeichnung(),
            ABSENDER.anschrift(),
            ABSENDER.email(),
            ABSENDER.telefon(),
            "12/345/67890",
            null,
            ABSENDER.bankverbindung(),
            ABSENDER.webadresse());

    // When
    final List<Druckelement> mitId = Rechnungslayout.setze(vorlage());
    final List<Druckelement> ohne =
        Rechnungslayout.setze(daten(ohneId, EMPFAENGER, POSITIONEN, "19", 10));

    // Then
    assertThat(inhalte(mitId)).contains("USt-IdNr. DE 01010101010");
    assertThat(inhalte(ohne)).contains("Steuernummer 12/345/67890");
    assertThat(inhalte(ohne)).noneMatch(zeile -> zeile.startsWith("USt-IdNr."));
  }

  @Test
  void setze_givenKeineBerufsbezeichnungUndKeineWebadresse_thenTheirZeilenFallWegOhneLuecke() {
    // Given — nur der Name und die Anschrift; alles Freiwillige fehlt.
    final Belegabsender knapp =
        new Belegabsender(
            "Max Mustermann",
            null,
            new Anschrift(null, null, null, null),
            null,
            null,
            null,
            null,
            null,
            null);
    final Belegempfaenger knappeFirma =
        new Belegempfaenger("Franz Mustermann GmbH", new Anschrift(null, null, null, null), null);

    // When
    final List<Druckelement> voll = Rechnungslayout.setze(vorlage());
    final List<Druckelement> elemente =
        Rechnungslayout.setze(daten(knapp, knappeFirma, POSITIONEN, "19", 10));

    // Then — der Name rueckt an die Stelle der Berufsbezeichnung, keine leere Zeile bleibt.
    assertThat(inhalte(elemente))
        .doesNotContain("mmuster.org", "AGILE COACH · SOFTWARE ENGINEER · KI-PRAKTIKER");
    final Druckelement.Text name = text(elemente, gesetzt -> gesetzt.groesse() == 26);
    assertThat(name.y())
        .isEqualTo(text(voll, "AGILE COACH · SOFTWARE ENGINEER · KI-PRAKTIKER").y());
    // Ort und Datum ohne eigenen Ort: nur das Datum.
    assertThat(inhalte(elemente)).contains("28.09.2026").doesNotContain("Bremen, 28.09.2026");
    // Unter dem Gruss steht der Name, keine Unterschrift.
    assertThat(inhalte(voll)).noneMatch(zeile -> zeile.contains("Unterschrift"));
    assertThat(inhalte(elemente)).noneMatch(zeile -> zeile.contains("Unterschrift"));
  }

  @Test
  void setze_givenKeinLeistungszeitraum_thenTheEinleitungNenntKeinen() {
    // Given
    final RechnungDruckdaten ohneZeitraum =
        new RechnungDruckdaten(
            ABSENDER,
            EMPFAENGER,
            "0003-2026",
            LocalDate.of(2026, 9, 28),
            null,
            POSITIONEN,
            new BigDecimal("1760.00"),
            new BigDecimal("19"),
            new BigDecimal("334.40"),
            new BigDecimal("2094.40"),
            10);

    // When
    final List<Druckelement> elemente = Rechnungslayout.setze(ohneZeitraum);

    // Then
    assertThat(inhalte(elemente)).contains("Für meine Leistungen stelle ich Ihnen in Rechnung");
  }

  @Test
  void setze_givenTheGanzeAnschriftMitLand_thenTheLandStehtAlsEigeneZeile() {
    // Given
    final Belegempfaenger imAusland =
        new Belegempfaenger(
            "Franz Mustermann GmbH",
            new Anschrift("Muster-Str. 24", null, "Wien", "Österreich"),
            null);

    // When
    final List<Druckelement> elemente =
        Rechnungslayout.setze(daten(ABSENDER, imAusland, POSITIONEN, "19", 10));

    // Then — ohne Postleitzahl bleibt der Ort allein stehen.
    assertThat(inhalte(elemente)).contains("Wien", "Österreich");
  }

  @Test
  void setze_givenTheAnschriftOhneOrt_thenTheZeileTraegtNurDiePostleitzahl() {
    // Given
    final Belegempfaenger ohneOrt =
        new Belegempfaenger(
            "Franz Mustermann GmbH", new Anschrift(null, "28217", null, null), null);

    // When
    final List<Druckelement> elemente =
        Rechnungslayout.setze(daten(ABSENDER, ohneOrt, POSITIONEN, "19", 10));

    // Then
    assertThat(inhalte(elemente)).contains("28217");
  }

  @Test
  void setze_givenTheLangenPositionstext_thenItBrichtInDerSpalteUm() {
    // Given — ein Satz, der breiter ist als die Spalte, und ein Wort, das es allein schon ist.
    final List<Druckposition> lang =
        List.of(
            position(
                "1",
                Einheit.PAUSCHAL,
                "Begleitung der Einfuehrung eines Vorgangsverwaltungssystems"
                    + " Donaudampfschifffahrtsgesellschaftskapitaenspatentpruefungsordnung",
                "200.00",
                "200.00"));

    // When
    final List<Druckelement> elemente =
        Rechnungslayout.setze(daten(ABSENDER, EMPFAENGER, lang, "19", 10));

    // Then — der Text steht in mehreren Zeilen, keine breiter als die Spalte, nichts geht verloren.
    final List<String> zeilen =
        texte(elemente).stream()
            .filter(gesetzt -> gesetzt.groesse() == 10)
            .filter(gesetzt -> gesetzt.ausrichtung() == Ausrichtung.LINKS)
            .map(Druckelement.Text::text)
            .filter(zeile -> !"Position".equals(zeile))
            .filter(zeile -> !"Anzahl".equals(zeile))
            .filter(zeile -> !"Einzelpreis".equals(zeile))
            .filter(zeile -> !"Gesamtpreis".equals(zeile))
            .toList();
    assertThat(zeilen).hasSizeGreaterThan(3);
    assertThat(zeilen).allSatisfy(zeile -> assertThat(zeile.length()).isLessThanOrEqualTo(41));
    assertThat(String.join("", zeilen).replace(" ", ""))
        .isEqualTo(lang.get(0).text().replace(" ", ""));
  }

  @Test
  void setze_givenSechzigPositionen_thenMehrereSeitenMitRandstreifenUndFuss() {
    // Given
    final List<Druckposition> viele =
        IntStream.rangeClosed(1, 60)
            .mapToObj(
                nummer -> position("1", Einheit.STUNDE, "Leistung " + nummer, "120.00", "120.00"))
            .toList();

    // When
    final List<Druckelement> elemente =
        Rechnungslayout.setze(daten(ABSENDER, EMPFAENGER, viele, "19", 10));

    // Then — mehrere Seiten, jede mit Randstreifen und Fuss.
    final List<Integer> seiten = seiten(elemente);
    assertThat(seiten).hasSizeGreaterThan(1);
    for (final Integer seite : seiten) {
      assertThat(elemente)
          .anyMatch(
              element ->
                  element instanceof Druckelement.Flaeche flaeche
                      && flaeche.seite() == seite
                      && flaeche.x() == 0);
      assertThat(texte(elemente))
          .anyMatch(
              gesetzt ->
                  gesetzt.seite() == seite
                      && gesetzt.text().equals("Max Mustermann · mmuster.org"));
    }

    // Then — kein Text unterhalb des Fusses, keiner ausserhalb des Blattes.
    assertThat(texte(elemente))
        .allSatisfy(
            gesetzt -> {
              assertThat(gesetzt.y()).isBetween(37, 842);
              assertThat(gesetzt.x()).isBetween(0, 595);
            });
    assertThat(elemente)
        .allSatisfy(element -> assertThat(element.seite()).isBetween(1, seiten.size()));
  }

  @Test
  void setze_givenTheSummenblockPasstNichtMehr_thenItBleibtBeiSeinerLetztenZeile() {
    // Given — so viele Positionen, dass die letzte genau an der Seitengrenze steht.
    for (int anzahl = 15; anzahl <= 45; anzahl++) {
      final int zahl = anzahl;
      final List<Druckposition> viele =
          IntStream.rangeClosed(1, zahl)
              .mapToObj(
                  nummer -> position("1", Einheit.STUNDE, "Leistung " + nummer, "120.00", "120.00"))
              .toList();

      // When
      final List<Druckelement> elemente =
          Rechnungslayout.setze(daten(ABSENDER, EMPFAENGER, viele, "19", 10));

      // Then — die letzte Position und der Summenblock stehen auf derselben Seite.
      final Druckelement.Text letzte = text(elemente, "Stunden Leistung " + zahl);
      assertThat(text(elemente, "Gesamtbetrag netto").seite()).isEqualTo(letzte.seite());
    }
  }
}
