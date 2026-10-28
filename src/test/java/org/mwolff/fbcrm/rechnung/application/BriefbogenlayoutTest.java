package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.mwolff.fbcrm.rechnung.application.RechnungDruckdaten.Druckposition;
import org.mwolff.fbcrm.rechnung.domain.Belegabsender;
import org.mwolff.fbcrm.rechnung.domain.Belegempfaenger;
import org.mwolff.fbcrm.rechnung.domain.Briefbogen;
import org.mwolff.fbcrm.rechnung.domain.Briefpapierblatt;
import org.mwolff.fbcrm.rechnung.domain.Randmasse;

/**
 * Der Satz der Rechnung auf dem eigenen Briefpapier (Plan #363, E10, E11, E12, E14).
 *
 * <p>Geprueft wird die Folge der Druckelemente: wo Anschriftfeld und Absenderzeile fest stehen, was
 * den Raendern folgt, wo eine lange Tabelle umbricht, welchen Platz die Steuer- und Bankzeile
 * freihaelt und dass nichts von der Kupferwolke bleibt. Das Briefpapier selbst legt erst der
 * Drucker darunter; der Satz kennt es nur ueber den {@link Briefbogen}.
 *
 * <p>Bei den Raendern der Vorgabe liegen in Punkten: linker Rand 71, rechte Kante 538, Beginn des
 * Inhalts 563, unterer Rand 57 und auf Folgeseiten obere Kante 785, unterer Rand 57. Die Steuer-
 * und Bankzeile haelt ueber dem unteren Rand 30 Punkte frei.
 */
class BriefbogenlayoutTest {

  private static final Farbe SCHWARZ = new Farbe(0, 0, 0);

  /** Die Raender aus Kriterium 15 in Zehntel-Millimetern. */
  private static final Randmasse VORGABE = new Randmasse(250, 200, 985, 200, 200, 200);

  /** Andere Raender in jeder Richtung — fuer alles, was von ihnen unabhaengig sein soll. */
  private static final Randmasse ANDERE = new Randmasse(400, 300, 1200, 300, 300, 300);

  /** Ein Briefpapier mit nur einer Seite; die Folgeseiten bleiben ohne Unterlage. */
  private static final Briefpapierblatt EINSEITIG =
      new Briefpapierblatt("briefpapier/0f8c.pdf", "briefpapier.pdf", 48_213L, 1);

  private static final Belegabsender ABSENDER =
      absender("12/345/67890", "DE 01010101010", "MLP Bank Heidelberg · IBAN DE00 1234");

  private static final Belegempfaenger EMPFAENGER =
      new Belegempfaenger(
          "Franz Mustermann GmbH",
          new Anschrift("Muster-Str. 24", "28217", "Bremen", "Deutschland"),
          null);

  private static Belegabsender absender(
      final @Nullable String steuernummer,
      final @Nullable String umsatzsteuerId,
      final @Nullable String bankverbindung) {
    return new Belegabsender(
        "Max Mustermann",
        "AGILE COACH",
        new Anschrift("Muster Str. 34a", "28197", "Bremen", null),
        "info@mmuster.org",
        "0173 0000 000",
        steuernummer,
        umsatzsteuerId,
        bankverbindung,
        "mmuster.org");
  }

  private static RechnungDruckdaten daten(final Belegabsender absender, final int positionen) {
    final List<Druckposition> zeilen =
        IntStream.rangeClosed(1, positionen)
            .mapToObj(
                nummer ->
                    new Druckposition(
                        BigDecimal.ONE,
                        Einheit.STUNDE,
                        "Leistung " + nummer,
                        new BigDecimal("120.00"),
                        new BigDecimal("120.00")))
            .toList();
    return new RechnungDruckdaten(
        absender,
        EMPFAENGER,
        "0003-2026",
        LocalDate.of(2026, 9, 28),
        "September 2026",
        zeilen,
        new BigDecimal("120.00"),
        new BigDecimal("19"),
        new BigDecimal("22.80"),
        new BigDecimal("142.80"),
        10);
  }

  private static RechnungDruckdaten daten(final int positionen) {
    return daten(ABSENDER, positionen);
  }

  private static Briefbogen bogen(
      final boolean absenderzeile, final boolean steuerBankZeile, final Randmasse raender) {
    return new Briefbogen(EINSEITIG, absenderzeile, steuerBankZeile, raender);
  }

  private static Briefbogen bogen(final Randmasse raender) {
    return bogen(true, true, raender);
  }

  private static EigeneAngaben eigene(final @Nullable String name) {
    return new EigeneAngaben(
        name,
        null,
        new Anschrift("Muster Str. 34a", "28197", "Bremen", null),
        null,
        null,
        null,
        "12/345/67890",
        null,
        null,
        null,
        null,
        "MLP Bank Heidelberg");
  }

  /** Das Pruefbild aus den Testmitteln; die Datei liegt neben den uebrigen Testressourcen. */
  private static String pruefbild(final String name) throws IOException {
    try (InputStream quelle =
        BriefbogenlayoutTest.class.getResourceAsStream("/rechnung/" + name + ".txt")) {
      return new String(
          Objects.requireNonNull(quelle, "Pruefbild fehlt: " + name).readAllBytes(),
          StandardCharsets.UTF_8);
    }
  }

  private static List<Druckelement.Text> texte(final List<Druckelement> elemente) {
    return elemente.stream()
        .filter(Druckelement.Text.class::isInstance)
        .map(Druckelement.Text.class::cast)
        .toList();
  }

  private static Druckelement.Text text(final List<Druckelement> elemente, final String inhalt) {
    return texte(elemente).stream()
        .filter(gesetzt -> gesetzt.text().equals(inhalt))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Nicht gesetzt: " + inhalt));
  }

  private static List<String> inhalte(final List<Druckelement> elemente) {
    return texte(elemente).stream().map(Druckelement.Text::text).toList();
  }

  private static int seiten(final List<Druckelement> elemente) {
    return elemente.stream().mapToInt(Druckelement::seite).max().orElseThrow();
  }

  private static List<Druckelement> aufSeite(final List<Druckelement> elemente, final int seite) {
    return elemente.stream().filter(element -> element.seite() == seite).toList();
  }

  /** Der tiefste Punkt, den Tabelle, Summen oder Schluss auf Seite eins erreichen. */
  private static int tiefsterInhaltAufSeiteEins(final List<Druckelement> elemente) {
    return aufSeite(elemente, 1).stream()
        .filter(element -> !(element instanceof Druckelement.Text text && text.groesse() == 8))
        .mapToInt(BriefbogenlayoutTest::unten)
        .min()
        .orElseThrow();
  }

  private static int unten(final Druckelement element) {
    return switch (element) {
      case Druckelement.Text text -> text.y();
      case Druckelement.Linie linie -> Math.min(linie.vonY(), linie.bisY());
      case Druckelement.Flaeche flaeche -> flaeche.y();
      case Druckelement.Schraegtext schraeg -> schraeg.y();
    };
  }

  private static int oben(final Druckelement element) {
    return switch (element) {
      case Druckelement.Text text -> text.y();
      case Druckelement.Linie linie -> Math.max(linie.vonY(), linie.bisY());
      case Druckelement.Flaeche flaeche -> flaeche.y() + flaeche.hoehe();
      case Druckelement.Schraegtext schraeg -> schraeg.y();
    };
  }

  private static Farbe farbe(final Druckelement element) {
    return switch (element) {
      case Druckelement.Text text -> text.farbe();
      case Druckelement.Linie linie -> linie.farbe();
      case Druckelement.Flaeche flaeche -> flaeche.farbe();
      case Druckelement.Schraegtext schraeg -> schraeg.farbe();
    };
  }

  @Test
  void setze_givenDieVorgabe_thenTheFolgeStimmtMitDemPruefbildUeberein() throws IOException {
    // Given — das Pruefbild in rechnung/briefbogen-vorgabe.txt ist der vollstaendige Vertrag des
    // Satzes auf dem Briefbogen, wie satz-vorlage.txt fuer die Kupferwolke: jedes Element, jedes
    // Mass, jede Farbe, in der Reihenfolge des Satzes. Aendert sich das Layout absichtlich, wird
    // die Datei bewusst nachgezogen.

    // When
    final List<Druckelement> elemente = Briefbogenlayout.setze(daten(1), bogen(VORGABE));

    // Then
    assertThat(Satzbild.von(elemente)).isEqualTo(pruefbild("briefbogen-vorgabe"));
  }

  @Test
  void
      setze_givenEinseitigesBriefpapierUndVolleTabelle_thenLangeRechnungOhneFolgeseitenBriefpapier() {
    // Given — Szenario „Lange Rechnung ohne Folgeseiten-Briefpapier": 30 Positionen passen nicht
    // auf Seite eins, die bei der Vorgabe bis zu 20 haelt.
    final Briefbogen bogen = bogen(VORGABE);

    // When
    final List<Druckelement> elemente = Briefbogenlayout.setze(daten(30), bogen);

    // Then — die Tabelle setzt sich auf Seite zwei fort, an deren oberem Rand.
    assertThat(seiten(elemente)).isGreaterThanOrEqualTo(2);
    assertThat(text(elemente, "Stunden Leistung 20").seite()).isEqualTo(1);
    assertThat(text(elemente, "Stunden Leistung 21").seite()).isEqualTo(2);
    assertThat(aufSeite(elemente, 2))
        .first()
        .isEqualTo(new Druckelement.Linie(2, 71, 785, 503, 785, 1, SCHWARZ));
    // Kein Element einer Folgeseite liegt ueber ihrem oberen oder unter ihrem unteren Rand, und
    // keines ist eine Flaeche — das Blatt bleibt ohne Unterlage, auch ohne einen Streifen.
    assertThat(elemente)
        .filteredOn(element -> element.seite() >= 2)
        .isNotEmpty()
        .allSatisfy(
            element -> {
              assertThat(element).isNotInstanceOf(Druckelement.Flaeche.class);
              assertThat(oben(element)).isLessThanOrEqualTo(785);
              assertThat(unten(element)).isGreaterThanOrEqualTo(57);
            });
  }

  @Test
  void setze_givenSchalterAn_thenAbsenderzeileUndSteuerBankzeileStehenAnIhrerLage() {
    // When
    final List<Druckelement> elemente = Briefbogenlayout.setze(daten(30), bogen(VORGABE));

    // Then — die Absenderzeile 61 mm unter der Blattkante, 7 Punkt, auf der Kante des
    // Anschriftfelds; die Steuer- und Bankzeile auf jeder Seite 4 mm ueber deren unterem Rand.
    assertThat(texte(elemente))
        .filteredOn(text -> text.groesse() == 7 || text.groesse() == 8)
        .extracting(
            Druckelement.Text::seite,
            Druckelement.Text::x,
            Druckelement.Text::y,
            Druckelement.Text::text)
        .containsExactly(
            tuple(1, 57, 669, "Max Mustermann · Muster Str. 34a · 28197 Bremen"),
            tuple(
                1,
                71,
                68,
                "USt-IdNr. DE 01010101010 · Bankverbindung: MLP Bank Heidelberg · IBAN DE00 1234"),
            tuple(
                2,
                71,
                68,
                "USt-IdNr. DE 01010101010 · Bankverbindung: MLP Bank Heidelberg · IBAN DE00 1234"));
  }

  @Test
  void setze_givenSchalterAus_thenOhneAbsenderzeileUndOhneSteuerBankzeile() {
    // When — jeder Schalter fuer sich abgeschaltet.
    final List<Druckelement> ohneAbsenderzeile =
        Briefbogenlayout.setze(daten(1), bogen(false, true, VORGABE));
    final List<Druckelement> ohneSteuerBankZeile =
        Briefbogenlayout.setze(daten(1), bogen(true, false, VORGABE));

    // Then — es fehlt jeweils genau die abgeschaltete Zeile.
    assertThat(texte(ohneAbsenderzeile))
        .noneMatch(text -> text.groesse() == 7)
        .anyMatch(text -> text.text().startsWith("USt-IdNr. "));
    assertThat(texte(ohneSteuerBankZeile))
        .anyMatch(text -> text.groesse() == 7)
        .noneMatch(text -> text.text().contains("USt-IdNr.") || text.text().contains("Bank"));
  }

  @Test
  void setze_givenSteuerBankZeileAus_thenReichtDerInhaltBisAnDenUnterenRand() {
    // Given — dieselbe lange Tabelle, die Zeile einmal an, einmal aus.
    final RechnungDruckdaten daten = daten(30);

    // When
    final int mitZeile =
        tiefsterInhaltAufSeiteEins(Briefbogenlayout.setze(daten, bogen(true, true, VORGABE)));
    final int ohneZeile =
        tiefsterInhaltAufSeiteEins(Briefbogenlayout.setze(daten, bogen(true, false, VORGABE)));

    // Then — mit der Zeile endet Seite eins nach 20 Positionen bei 88, knapp ueber der Untergrenze
    // 87; ohne sie haelt die Seite eine Position mehr und endet bei 72, ueber dem Rand bei 57.
    assertThat(mitZeile).isEqualTo(88);
    assertThat(ohneZeile).isEqualTo(72);
  }

  @Test
  void setze_givenSteuerBankZeileAnOhneAngaben_thenPlatzBleibtFrei() {
    // Given — keine Steuernummer, keine USt-IdNr., keine Bankverbindung; der Schalter steht an.
    final Belegabsender ohneAngaben = absender(null, null, null);

    // When
    final List<Druckelement> mitAngaben = Briefbogenlayout.setze(daten(30), bogen(VORGABE));
    final List<Druckelement> leer = Briefbogenlayout.setze(daten(ohneAngaben, 30), bogen(VORGABE));
    final List<Druckelement> aus =
        Briefbogenlayout.setze(daten(ohneAngaben, 30), bogen(true, false, VORGABE));

    // Then — keine Zeile im Satz, die Untergrenze aber dieselbe wie mit Angaben und hoeher als
    // ohne den Schalter.
    assertThat(texte(leer)).noneMatch(text -> text.groesse() == 8);
    assertThat(tiefsterInhaltAufSeiteEins(leer))
        .isEqualTo(tiefsterInhaltAufSeiteEins(mitAngaben))
        .isGreaterThan(tiefsterInhaltAufSeiteEins(aus));
  }

  @Test
  void setze_givenNurDieBankverbindung_thenStehtNurSieInDerZeile() {
    // When
    final List<Druckelement> elemente =
        Briefbogenlayout.setze(daten(absender(null, null, "MLP Bank"), 1), bogen(VORGABE));

    // Then
    assertThat(texte(elemente))
        .filteredOn(text -> text.groesse() == 8)
        .extracting(Druckelement.Text::text)
        .containsExactly("Bankverbindung: MLP Bank");
  }

  @Test
  void setze_givenNurDieSteuernummer_thenStehtNurSieInDerZeile() {
    // When
    final List<Druckelement> elemente =
        Briefbogenlayout.setze(daten(absender("12/345/67890", null, null), 1), bogen(VORGABE));

    // Then
    assertThat(texte(elemente))
        .filteredOn(text -> text.groesse() == 8)
        .extracting(Druckelement.Text::text)
        .containsExactly("Steuernummer 12/345/67890");
  }

  @Test
  void setze_givenGroesseremInhaltBeginn_thenTabelleBrichtFrueherUm() {
    // Given — derselbe Beleg mit 54 Positionen; einmal beginnt der Inhalt bei 98,5 mm, einmal bei
    // 198,5 mm. Bei der Vorgabe oeffnet Position 21 die zweite Seite.
    final RechnungDruckdaten daten = daten(54);
    final Randmasse tiefer = new Randmasse(250, 200, 1985, 200, 200, 200);

    // When
    final List<Druckelement> vorgabe = Briefbogenlayout.setze(daten, bogen(VORGABE));
    final List<Druckelement> spaeter = Briefbogenlayout.setze(daten, bogen(tiefer));

    // Then — mit dem spaeteren Beginn braucht er eine Seite mehr, und dieselbe Zeile steht auf der
    // Folgeseite tiefer, weil Seite eins weniger Positionen haelt.
    assertThat(seiten(vorgabe)).isEqualTo(2);
    assertThat(seiten(spaeter)).isEqualTo(3);
    assertThat(text(vorgabe, "Stunden Leistung 21"))
        .extracting(Druckelement.Text::seite, Druckelement.Text::y)
        .containsExactly(2, 774);
    assertThat(text(spaeter, "Stunden Leistung 21"))
        .extracting(Druckelement.Text::seite, Druckelement.Text::y)
        .containsExactly(2, 486);
  }

  @Test
  void setze_givenAndereRaender_thenFolgtDerInhaltIhnen() {
    // When — 40 mm links, 30 mm rechts, Beginn bei 120 mm.
    final List<Druckelement> elemente = Briefbogenlayout.setze(daten(1), bogen(ANDERE));

    // Then — Ort und Datum am rechten Rand, Titel und Tabelle am linken, alles ab 502.
    assertThat(text(elemente, "Bremen, 28.09.2026"))
        .extracting(Druckelement.Text::x, Druckelement.Text::y, Druckelement.Text::ausrichtung)
        .containsExactly(510, 502, Ausrichtung.RECHTS);
    assertThat(text(elemente, "Rechnung 0003-2026"))
        .extracting(Druckelement.Text::x, Druckelement.Text::y)
        .containsExactly(113, 448);
    assertThat(text(elemente, "Anzahl").x()).isEqualTo(117);
  }

  @Test
  void setze_givenAndereRaender_thenBeginntEineFolgeseiteAnIhremOberenRand() {
    // When — 30 mm oben und unten auf Folgeseiten.
    final List<Druckelement> elemente = Briefbogenlayout.setze(daten(30), bogen(ANDERE));

    // Then — die Tabelle setzt oben bei 757 fort, die Steuer- und Bankzeile steht bei 96.
    assertThat(aufSeite(elemente, 2))
        .first()
        .isEqualTo(new Druckelement.Linie(2, 113, 757, 545, 757, 1, SCHWARZ));
    assertThat(texte(aufSeite(elemente, 2)))
        .filteredOn(text -> text.groesse() == 8)
        .extracting(Druckelement.Text::y)
        .containsExactly(96);
  }

  @Test
  void setze_givenVerschiedeneUntereRaender_thenGiltAufJederSeiteIhrEigener() {
    // Given — Seite eins endet 40 mm ueber der Blattkante (113 Punkte), Folgeseiten bei 20 mm.
    final Randmasse raender = new Randmasse(250, 200, 985, 400, 200, 200);

    // When
    final List<Druckelement> elemente = Briefbogenlayout.setze(daten(30), bogen(raender));

    // Then — Seite eins haelt mit Untergrenze 143 nur 16 Positionen; die Steuer- und Bankzeile
    // steht je Seite 4 mm ueber deren unterem Rand.
    assertThat(tiefsterInhaltAufSeiteEins(elemente)).isEqualTo(152);
    assertThat(text(elemente, "Stunden Leistung 17").seite()).isEqualTo(2);
    assertThat(texte(elemente))
        .filteredOn(text -> text.groesse() == 8)
        .extracting(Druckelement.Text::seite, Druckelement.Text::y)
        .containsExactly(tuple(1, 124), tuple(2, 68));
  }

  @Test
  void setze_givenOhneEigenenNamen_thenOhneAbsenderzeileUndOhneNamenszeile() {
    // Given — der Probedruck ohne eigenen Namen, mit einer Position. Bei einem unteren Rand von
    // 75,5 mm (214 Punkte, mit der Steuer- und Bankzeile 244) endet der Gruss genau auf der
    // Untergrenze; die Namenszeile darunter haette dort keinen Platz mehr.
    final Probedruckdaten ohneName = Probedruckdaten.von(eigene(null)).mitEinerPosition();
    final Probedruckdaten mitName =
        Probedruckdaten.von(eigene("Max Mustermann")).mitEinerPosition();
    final Briefbogen knapp = bogen(new Randmasse(250, 200, 985, 755, 200, 200));

    // When
    final List<Druckelement> ohne = Briefbogenlayout.setze(ohneName, knapp);
    final List<Druckelement> mit = Briefbogenlayout.setze(mitName, knapp);

    // Then — ohne Namen entsteht der Satz, ohne Absenderzeile und ohne Namenszeile, und der
    // Schluss bleibt auf Seite eins: Unter dem Gruss ist keine Hoehe fuer den Namen reserviert.
    assertThat(texte(ohne)).noneMatch(text -> text.groesse() == 7);
    assertThat(inhalte(ohne)).doesNotContain("Max Mustermann", "");
    assertThat(text(ohne, "Mit freundlichen Grüßen"))
        .extracting(Druckelement.Text::seite, Druckelement.Text::y)
        .containsExactly(1, 244);
    assertThat(ohne.get(ohne.indexOf(text(ohne, "Mit freundlichen Grüßen")) + 1))
        .isEqualTo(
            new Druckelement.Text(
                1,
                71,
                225,
                Schrift.NORMAL,
                8,
                SCHWARZ,
                Ausrichtung.LINKS,
                "Steuernummer 12/345/67890 · Bankverbindung: MLP Bank Heidelberg"));
    // Mit Namen reicht der Schluss tiefer und rueckt darum auf ein neues Blatt.
    assertThat(text(mit, "Max Mustermann · Muster Str. 34a · 28197 Bremen").y()).isEqualTo(669);
    assertThat(text(mit, "Mit freundlichen Grüßen").seite()).isEqualTo(2);
    assertThat(texte(mit).getLast())
        .extracting(Druckelement.Text::seite, Druckelement.Text::text)
        .containsExactly(2, "Steuernummer 12/345/67890 · Bankverbindung: MLP Bank Heidelberg");
    assertThat(text(mit, "Max Mustermann"))
        .extracting(Druckelement.Text::seite, Druckelement.Text::y)
        .containsExactly(2, 727);
  }

  @Test
  void setze_thenAnschriftfeldUndAbsenderzeileUnabhaengigVonRaendern() {
    // When — zwei verschiedene Randsaetze.
    final List<Druckelement> vorgabe = Briefbogenlayout.setze(daten(1), bogen(VORGABE));
    final List<Druckelement> andere = Briefbogenlayout.setze(daten(1), bogen(ANDERE));

    // Then — Anschriftfeld nach DIN 5008 Form B: linke Kante 20 mm, erste Grundlinie 66,7 mm unter
    // der Blattkante; darueber die Absenderzeile bei 61 mm. In beiden Saetzen an derselben Stelle.
    final List<Druckelement.Text> erwartet =
        List.of(
            new Druckelement.Text(
                1,
                57,
                669,
                Schrift.NORMAL,
                7,
                SCHWARZ,
                Ausrichtung.LINKS,
                "Max Mustermann · Muster Str. 34a · 28197 Bremen"),
            new Druckelement.Text(
                1,
                57,
                653,
                Schrift.NORMAL,
                11,
                SCHWARZ,
                Ausrichtung.LINKS,
                "Franz Mustermann GmbH"),
            new Druckelement.Text(
                1, 57, 640, Schrift.NORMAL, 11, SCHWARZ, Ausrichtung.LINKS, "Muster-Str. 24"),
            new Druckelement.Text(
                1, 57, 617, Schrift.NORMAL, 11, SCHWARZ, Ausrichtung.LINKS, "28217 Bremen"),
            new Druckelement.Text(
                1, 57, 604, Schrift.NORMAL, 11, SCHWARZ, Ausrichtung.LINKS, "Deutschland"));
    assertThat(vorgabe.subList(0, 5)).isEqualTo(erwartet);
    assertThat(andere.subList(0, 5)).isEqualTo(erwartet);
  }

  @Test
  void setze_givenEigeneAnschriftOhneOrt_thenStehtNurDasDatumUndDieAbsenderzeileNurMitDemNamen() {
    // Given
    final Belegabsender ohneAnschrift =
        new Belegabsender(
            "Max Mustermann",
            null,
            new Anschrift(null, null, null, null),
            null,
            null,
            "12/345/67890",
            null,
            null,
            null);

    // When
    final List<Druckelement> elemente =
        Briefbogenlayout.setze(daten(ohneAnschrift, 1), bogen(VORGABE));

    // Then
    assertThat(inhalte(elemente)).startsWith("Max Mustermann").contains("28.09.2026");
  }

  @Test
  void setze_thenAllesSchwarz() {
    // When — eine lange Rechnung mit allen Angaben und beiden Zeilen.
    final List<Druckelement> elemente = Briefbogenlayout.setze(daten(30), bogen(VORGABE));

    // Then — jedes Element traegt Schwarz, keines eine Farbe der Kupferwolke.
    assertThat(elemente)
        .isNotEmpty()
        .allSatisfy(element -> assertThat(farbe(element)).isEqualTo(SCHWARZ));
  }

  @Test
  void setze_thenOhneRandstreifenUndOhneKopfUndFussDerKupferwolke() {
    // When
    final List<Druckelement> elemente = Briefbogenlayout.setze(daten(30), bogen(VORGABE));

    // Then — keine Flaeche, also kein Randstreifen; weder Berufsbezeichnung, Webadresse, Telefon
    // noch E-Mail aus dem Kopf; keine Linie ausserhalb der Tabelle wie die des Kopfes oder Fusses.
    assertThat(elemente).noneMatch(Druckelement.Flaeche.class::isInstance);
    assertThat(inhalte(elemente))
        .noneMatch(
            inhalt ->
                inhalt.contains("AGILE COACH")
                    || inhalt.contains("mmuster.org")
                    || inhalt.contains("0173")
                    || inhalt.contains("Tel "));
    assertThat(elemente)
        .filteredOn(Druckelement.Linie.class::isInstance)
        .map(Druckelement.Linie.class::cast)
        .allSatisfy(
            linie -> {
              assertThat(linie.vonX()).isBetween(71, 503);
              assertThat(linie.bisX()).isBetween(71, 503);
            });
  }
}
