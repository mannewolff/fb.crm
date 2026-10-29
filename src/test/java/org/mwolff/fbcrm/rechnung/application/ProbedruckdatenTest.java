package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.mwolff.fbcrm.rechnung.application.RechnungDruckdaten.Druckposition;
import org.mwolff.fbcrm.rechnung.domain.Belegabsender;
import org.mwolff.fbcrm.rechnung.domain.Belegempfaenger;
import org.mwolff.fbcrm.rechnung.domain.Briefbogen;
import org.mwolff.fbcrm.rechnung.domain.Randmasse;

/** Die erfundenen Beispieldaten des Probedrucks mit den echten eigenen Angaben (Plan #363, E14). */
class ProbedruckdatenTest {

  /** Die Raender aus Kriterium 15 in Zehntel-Millimetern. */
  private static final Randmasse VORGABE = new Randmasse(250, 200, 985, 200, 200, 200);

  /** Kein Rand: die groesste Flaeche, die ein Briefbogen der Tabelle lassen kann. */
  private static final Randmasse OHNE_RAND = new Randmasse(0, 0, 0, 0, 0, 0);

  private static EigeneAngaben eigene(final @Nullable String name) {
    return new EigeneAngaben(
        name,
        "AGILE COACH",
        new Anschrift("Muster Str. 34a", "28197", "Bremen", null),
        "info@mmuster.org",
        "0173 0000 000",
        "mmuster.org",
        "12/345/67890",
        "DE 01010101010",
        "DE89370400440532013000",
        "COBADEFFXXX",
        "Max Mustermann",
        "MLP Bank Heidelberg");
  }

  private static int seiten(final List<Druckelement> elemente) {
    return elemente.stream().mapToInt(Druckelement::seite).max().orElseThrow();
  }

  @Test
  void von_givenEigeneAngaben_thenTraegtDerAbsenderDieEchtenAngabenUndDerNameStehtFuerSich() {
    // When
    final Probedruckdaten probe = Probedruckdaten.von(eigene("Max Mustermann"));

    // Then
    assertThat(probe.name()).isEqualTo("Max Mustermann");
    assertThat(probe.druckdaten().absender())
        .isEqualTo(
            new Belegabsender(
                "Max Mustermann",
                "AGILE COACH",
                new Anschrift("Muster Str. 34a", "28197", "Bremen", null),
                "info@mmuster.org",
                "0173 0000 000",
                "12/345/67890",
                "DE 01010101010",
                "MLP Bank Heidelberg",
                "mmuster.org",
                "DE89370400440532013000",
                "COBADEFFXXX",
                "Max Mustermann"));
  }

  @Test
  void von_givenKeinenEigenenNamen_thenFehltDerNameUndDerAbsenderBleibtOhneIhn() {
    // When
    final Probedruckdaten probe = Probedruckdaten.von(eigene(null));

    // Then — der Name fehlt; der Absender traegt an seiner Stelle nichts, gelesen wird name().
    assertThat(probe.name()).isNull();
    assertThat(probe.druckdaten().absender().name()).isEmpty();
  }

  @Test
  void von_thenSindEmpfaengerNummerDatumUndPositionenErfunden() {
    // When
    final RechnungDruckdaten daten = Probedruckdaten.von(eigene("Max Mustermann")).druckdaten();

    // Then
    assertThat(daten.empfaenger())
        .isEqualTo(
            new Belegempfaenger(
                "Beispiel GmbH",
                new Anschrift("Musterweg 1", "12345", "Musterstadt", null),
                null,
                null));
    assertThat(daten.nummer()).isEqualTo("Muster-001");
    assertThat(daten.rechnungDatum()).isEqualTo(LocalDate.of(2026, 9, 30));
    assertThat(daten.leistungszeitraum()).isEqualTo("September 2026");
    assertThat(daten.zahlungszielTage()).isEqualTo(14);
    assertThat(daten.positionen())
        .hasSize(24)
        .first()
        .isEqualTo(
            new Druckposition(
                new BigDecimal("8"),
                Einheit.STUNDE,
                "Beispielleistung 1, Konzeption und Umsetzung",
                new BigDecimal("95.00"),
                new BigDecimal("760.00")));
    assertThat(daten.positionen().getLast().text())
        .isEqualTo("Beispielleistung 24, Konzeption und Umsetzung");
  }

  @Test
  void von_thenStimmenDieSummenZuDenPositionen() {
    // When
    final RechnungDruckdaten daten = Probedruckdaten.von(eigene("Max Mustermann")).druckdaten();

    // Then — 24 mal 760,00, darauf 19 Prozent.
    assertThat(daten.netto()).isEqualTo(new BigDecimal("18240.00"));
    assertThat(daten.steuersatz()).isEqualTo(new BigDecimal("19"));
    assertThat(daten.steuer()).isEqualTo(new BigDecimal("3465.60"));
    assertThat(daten.brutto()).isEqualTo(new BigDecimal("21705.60"));
  }

  @Test
  void von_givenDieVorgabe_thenErzwingenDieBeispieldatenEineFolgeseite() {
    // Given
    final Probedruckdaten probe = Probedruckdaten.von(eigene("Max Mustermann"));

    // When
    final List<Druckelement> elemente =
        Briefbogenlayout.setze(probe, new Briefbogen(null, true, true, VORGABE));

    // Then
    assertThat(seiten(elemente)).isEqualTo(2);
  }

  @Test
  void von_givenKeinenRandUndKeineSteuerBankZeile_thenErzwingenSieDennochEineFolgeseite() {
    // Given — mehr Platz kann ein Briefbogen der Tabelle nicht lassen.
    final Probedruckdaten probe = Probedruckdaten.von(eigene("Max Mustermann"));

    // When
    final List<Druckelement> elemente =
        Briefbogenlayout.setze(probe, new Briefbogen(null, false, false, OHNE_RAND));

    // Then
    assertThat(seiten(elemente)).isGreaterThanOrEqualTo(2);
  }

  @Test
  void mitEinerPosition_thenHatDerProbedruckGenauEineSeite() {
    // Given
    final Probedruckdaten probe = Probedruckdaten.von(eigene("Max Mustermann"));

    // When
    final Probedruckdaten eine = probe.mitEinerPosition();

    // Then — dieselben Angaben, nur die erste Position, die Summen zu ihr.
    assertThat(eine.name()).isEqualTo("Max Mustermann");
    assertThat(eine.druckdaten().absender()).isEqualTo(probe.druckdaten().absender());
    assertThat(eine.druckdaten().positionen())
        .containsExactly(probe.druckdaten().positionen().getFirst());
    assertThat(eine.druckdaten().netto()).isEqualTo(new BigDecimal("760.00"));
    assertThat(eine.druckdaten().steuer()).isEqualTo(new BigDecimal("144.40"));
    assertThat(eine.druckdaten().brutto()).isEqualTo(new BigDecimal("904.40"));
    assertThat(seiten(Briefbogenlayout.setze(eine, new Briefbogen(null, true, true, VORGABE))))
        .isEqualTo(1);
  }
}
