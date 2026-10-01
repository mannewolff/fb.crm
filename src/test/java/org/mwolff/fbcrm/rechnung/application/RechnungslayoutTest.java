package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

  static RechnungDruckdaten vorlageDaten() {
    return daten(ABSENDER, EMPFAENGER, POSITIONEN, "19", 10);
  }

  /** Derselbe Beleg ohne jede freiwillige Angabe: nur die beiden Namen und die Positionen. */
  static RechnungDruckdaten knappeDaten() {
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
    return daten(knapp, knappeFirma, POSITIONEN, "19", 10);
  }

  /** Das Pruefbild aus den Testmitteln; die Datei liegt neben den uebrigen Testressourcen. */
  private static String pruefbild(final String name) throws IOException {
    try (InputStream quelle =
        RechnungslayoutTest.class.getResourceAsStream("/rechnung/" + name + ".txt")) {
      return new String(
          Objects.requireNonNull(quelle, "Pruefbild fehlt: " + name).readAllBytes(),
          StandardCharsets.UTF_8);
    }
  }

  private static List<Druckelement.Linie> linien(final List<Druckelement> elemente) {
    return elemente.stream()
        .filter(Druckelement.Linie.class::isInstance)
        .map(Druckelement.Linie.class::cast)
        .toList();
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
  void setze_givenDieAngabenDerVorlage_thenTheFolgeStimmtMitDemPruefbildUeberein()
      throws IOException {
    // Given — das Pruefbild in rechnung/satz-vorlage.txt ist der vollstaendige Vertrag des Satzes:
    // jedes Element, jedes Mass, jede Farbe, in der Reihenfolge des Satzes. Es steht hier, weil
    // einzelne Zusicherungen nur benennen koennen, woran jemand gedacht hat — eine verschobene
    // Grundlinie, eine fehlende Linie oder ein gekehrtes Vorzeichen fallen nur so auf.
    //
    // Aendert sich das Layout absichtlich, wird die Datei bewusst nachgezogen; der Unterschied im
    // Diff ist dann die Liste dessen, was sich bewegt hat, und am Dokument selbst abzunehmen.

    // When
    final List<Druckelement> elemente = Rechnungslayout.setze(vorlageDaten());

    // Then
    assertThat(Satzbild.von(elemente)).isEqualTo(pruefbild("satz-vorlage"));
  }

  @Test
  void setze_givenKeineEinzigeFreiwilligeAngabe_thenTheFolgeStimmtMitDemPruefbildUeberein()
      throws IOException {
    // Given — das zweite Pruefbild: Derselbe Beleg, aber Berufsbezeichnung, Webadresse, Telefon,
    // E-Mail, Bankverbindung und jede Zeile beider Anschriften fehlen. Es haelt fest, dass eine
    // fehlende Zeile keine Hoehe verbraucht und dass aus einer leeren Verbindung keine Zeile mit
    // Trennern entsteht.

    // When
    final List<Druckelement> elemente = Rechnungslayout.setze(knappeDaten());

    // Then
    assertThat(Satzbild.von(elemente)).isEqualTo(pruefbild("satz-ohne-freiwillige-angaben"));
  }

  @Test
  void setze_givenDieAngabenDerVorlage_thenTheBloeckeStehenVonObenNachUnten() {
    // When
    final List<Druckelement> elemente = Rechnungslayout.setze(vorlageDaten());

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
    final List<Druckelement> elemente = Rechnungslayout.setze(vorlageDaten());

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
    final List<Druckelement> elemente = Rechnungslayout.setze(vorlageDaten());

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
    final List<Druckelement> elemente = Rechnungslayout.setze(vorlageDaten());

    // Then — bei PAUSCHAL steht nur der Text.
    assertThat(inhalte(elemente))
        .contains("Stunden KI Beratung am 15.09.2026", "Personentage Workshop", "Reisekosten");
  }

  @Test
  void setze_givenMengenUndBetraege_thenTheyAreGeschriebenWieInDerVorlage() {
    // When
    final List<Druckelement> elemente = Rechnungslayout.setze(vorlageDaten());

    // Then — Anzahl ohne Nachnullen, Betraege mit Tausenderpunkt und Eurozeichen.
    assertThat(inhalte(elemente)).contains("3", "2,5", "120,00 €", "1.200,00 €");
    assertThat(text(elemente, "120,00 €").ausrichtung()).isEqualTo(Ausrichtung.RECHTS);
    assertThat(text(elemente, "1.200,00 €").ausrichtung()).isEqualTo(Ausrichtung.RECHTS);
    assertThat(text(elemente, "3").ausrichtung()).isEqualTo(Ausrichtung.RECHTS);
  }

  @Test
  void setze_givenSteuersatzMitNachkommastelle_thenTheSatzStehtOhneNachnullen() {
    // When
    final List<Druckelement> ganz = Rechnungslayout.setze(vorlageDaten());
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
    final List<Druckelement> mitId = Rechnungslayout.setze(vorlageDaten());
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

    // When
    final List<Druckelement> voll = Rechnungslayout.setze(vorlageDaten());
    final List<Druckelement> elemente = Rechnungslayout.setze(knappeDaten());

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

  /**
   * Die Zeilen der Spalte „Position" in ihrer Reihenfolge, ohne den Spaltenkopf.
   *
   * <p>Der Kopf „Position" steht an derselben Spaltenkante wie die Texte darunter; er wird an
   * seiner Grundlinie erkannt, die er mit den drei uebrigen Koepfen teilt.
   */
  private static List<String> positionszeilen(final List<Druckelement> elemente) {
    final int kopfzeile = text(elemente, "Gesamtpreis").y();
    return texte(elemente).stream()
        .filter(gesetzt -> gesetzt.ausrichtung() == Ausrichtung.LINKS)
        .filter(gesetzt -> gesetzt.x() == 140)
        .filter(gesetzt -> gesetzt.y() != kopfzeile)
        .map(Druckelement.Text::text)
        .toList();
  }

  /** Der Beleg mit so vielen einzeiligen Positionen, jede mit ihrer Nummer im Text. */
  private static List<Druckelement> mitPositionen(final int anzahl) {
    final List<Druckposition> viele =
        IntStream.rangeClosed(1, anzahl)
            .mapToObj(
                nummer -> position("1", Einheit.STUNDE, "Leistung " + nummer, "120.00", "120.00"))
            .toList();
    return Rechnungslayout.setze(daten(ABSENDER, EMPFAENGER, viele, "19", 10));
  }

  /** Die Seite, auf der die Position mit dieser Nummer steht. */
  private static int seiteDerPosition(final List<Druckelement> elemente, final int nummer) {
    return text(elemente, "Stunden Leistung " + nummer).seite();
  }

  @Test
  void setze_givenSiebzehnPositionen_thenTheyAllStillFitOnTheErsteSeite() {
    // Given — die Masse sagen genau, wo die Grenze liegt: Die Tabelle beginnt unter ihrer Kopfzeile
    // auf 449, jede Zeile verbraucht 16, und unter 120 faengt die Tabelle nicht mehr an. Die letzte
    // Position legt ausserdem die 50 des Summenblocks zurueck, damit er ihr folgen kann. Siebzehn
    // Positionen sind die letzte Anzahl, die damit auf eine Seite geht.

    // When
    final List<Druckelement> elemente = mitPositionen(17);

    // Then
    assertThat(seiteDerPosition(elemente, 17)).isEqualTo(1);
  }

  @Test
  void setze_givenAchtzehnPositionen_thenOnlyTheLetzteGoesToTheZweiteSeite() {
    // Given — eine Position mehr als siebzehn. Sie allein passte noch; mit der Rueckstellung fuer
    // den Summenblock passt sie nicht. Ohne diese Rueckstellung stuende sie noch auf Seite eins.

    // When
    final List<Druckelement> elemente = mitPositionen(18);

    // Then
    assertThat(seiteDerPosition(elemente, 17)).isEqualTo(1);
    assertThat(seiteDerPosition(elemente, 18)).isEqualTo(2);
    assertThat(text(elemente, "Gesamtbetrag netto").seite()).isEqualTo(2);

    // Then — der Rahmen der Tabelle wird vor dem Umbruch geschlossen: fuenf Senkrechte von der
    // letzten Linie der Seite bis zu ihrer obersten. Ohne sie endete die Tabelle auf Seite eins
    // offen.
    assertThat(linien(elemente))
        .filteredOn(linie -> linie.seite() == 1 && linie.vonX() == linie.bisX())
        .extracting(Druckelement.Linie::vonX, Druckelement.Linie::vonY, Druckelement.Linie::bisY)
        .containsExactly(
            tuple(71, 177, 467),
            tuple(136, 177, 467),
            tuple(371, 177, 467),
            tuple(436, 177, 467),
            tuple(503, 177, 467));

    // Then — und auf der neuen Seite faengt die Tabelle mit ihrer obersten Linie wieder an.
    assertThat(linien(elemente))
        .filteredOn(linie -> linie.seite() == 2 && linie.vonY() == linie.bisY())
        .first()
        .extracting(Druckelement.Linie::vonX, Druckelement.Linie::bisX, Druckelement.Linie::vonY)
        .containsExactly(71, 503, 780);
  }

  @Test
  void setze_givenZweiundzwanzigPositionen_thenTheUmbruchFaelltVorDieEinundzwanzigste() {
    // Given — hier ist die einundzwanzigste nicht die letzte und legt darum nichts zurueck. Sie
    // bricht erst eine Zeile spaeter um als die letzte es taete; faellt die Unterscheidung weg,
    // wandert der Umbruch an eine andere Position.

    // When
    final List<Druckelement> elemente = mitPositionen(22);

    // Then
    assertThat(seiteDerPosition(elemente, 20)).isEqualTo(1);
    assertThat(seiteDerPosition(elemente, 21)).isEqualTo(2);
    assertThat(seiteDerPosition(elemente, 22)).isEqualTo(2);
  }

  @Test
  void setze_givenZwoelfPositionen_thenTheSchlussStillFitsUnderTheTabelle() {
    // Given — die Tabelle endet hoch genug: Nach ihr bleiben 40 Punkte Abstand, der Schluss braucht
    // 58, und unter 100 geht er nicht mehr. Bei zwoelf Positionen reicht es gerade.

    // When
    final List<Druckelement> elemente = mitPositionen(12);

    // Then
    assertThat(text(elemente, "Mit freundlichen Grüßen").seite()).isEqualTo(1);
  }

  @Test
  void setze_givenDreizehnPositionen_thenTheSchlussGoesToTheNaechsteSeite() {
    // Given — eine Position mehr, und der Schluss passt nicht mehr unter die Tabelle. Er wandert
    // ganz auf die naechste Seite und beginnt dort oben; zerrissen wird er nicht.

    // When
    final List<Druckelement> elemente = mitPositionen(13);

    // Then — die Tabelle steht noch ganz auf Seite eins, der Schluss nicht mehr.
    assertThat(seiteDerPosition(elemente, 13)).isEqualTo(1);
    assertThat(text(elemente, "Gesamtbetrag netto").seite()).isEqualTo(1);
    final Druckelement.Text gruss = text(elemente, "Mit freundlichen Grüßen");
    assertThat(gruss.seite()).isEqualTo(2);
    assertThat(text(elemente, "Bitte überweisen Sie den Betrag innerhalb von 10 Tagen").y())
        .isEqualTo(780);
  }

  @Test
  void setze_givenAPositionstextExactlyAsWideAsTheSpalte_thenItStaysOnOneZeile() {
    // Given — die Spalte traegt 41 Zeichen. Genau 41 passen noch: Die Grenze gehoert zur Zeile.
    final String randvoll = "A".repeat(20) + " " + "B".repeat(20);
    assertThat(randvoll).hasSize(41);

    // When
    final List<Druckelement> elemente =
        Rechnungslayout.setze(
            daten(
                ABSENDER,
                EMPFAENGER,
                List.of(position("1", Einheit.PAUSCHAL, randvoll, "200.00", "200.00")),
                "19",
                10));

    // Then
    assertThat(positionszeilen(elemente)).containsExactly(randvoll);
  }

  @Test
  void setze_givenAPositionstextOneZeichenTooWide_thenItBrichtNachDemErstenWortUm() {
    // Given — ein Zeichen mehr als die Spalte traegt. Das Leerzeichen zwischen den Woertern zaehlt
    // mit; ohne es waere auch dieser Text noch einzeilig.
    final String zuBreit = "A".repeat(20) + " " + "B".repeat(21);
    assertThat(zuBreit).hasSize(42);

    // When
    final List<Druckelement> elemente =
        Rechnungslayout.setze(
            daten(
                ABSENDER,
                EMPFAENGER,
                List.of(position("1", Einheit.PAUSCHAL, zuBreit, "200.00", "200.00")),
                "19",
                10));

    // Then
    assertThat(positionszeilen(elemente)).containsExactly("A".repeat(20), "B".repeat(21));
  }

  @Test
  void
      setze_givenASingleWortExactlyAsWideAsTheSpalte_thenItIsNeitherSplitNorPrecededByALeerzeile() {
    // Given — ein Wort von genau der Spaltenbreite. Es wird nicht hart getrennt, und vor ihm steht
    // keine leere Zeile: Die erste Zeile faengt nicht mit einem Umbruch an.
    final String randvoll = "C".repeat(41);

    // When
    final List<Druckelement> elemente =
        Rechnungslayout.setze(
            daten(
                ABSENDER,
                EMPFAENGER,
                List.of(position("1", Einheit.PAUSCHAL, randvoll, "200.00", "200.00")),
                "19",
                10));

    // Then — eine Zeile, und keine zweite; eine Pauschale traegt ihre Einheit nicht im Text.
    assertThat(positionszeilen(elemente)).containsExactly(randvoll);
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
