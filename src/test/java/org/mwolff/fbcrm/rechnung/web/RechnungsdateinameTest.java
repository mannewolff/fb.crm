package org.mwolff.fbcrm.rechnung.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Der Name, unter dem das Dokument einer Rechnung beim Empfaenger landet (#160, Kriterium 24).
 *
 * <p>Gegenstand ist allein die Rechnungsnummer, denn nur sie ist frei: Ihr Muster steht in den
 * Einstellungen und darf zwischen den Platzhaltern beliebigen Text tragen. Geprueft wird deshalb
 * die uebliche Nummer und das, was ein Muster an Zeichen hineinbringen kann, die in einem
 * Dateinamen nichts zu suchen haben.
 */
class RechnungsdateinameTest {

  @Test
  void fuer_givenAPlainNummer_thenItCarriesItUnchanged() {
    // When / Then
    assertThat(Rechnungsdateiname.fuer("0001-2026")).isEqualTo("Rechnung-0001-2026.pdf");
  }

  @Test
  void fuer_givenASlashInTheNummer_thenItBecomesAHyphen() {
    // Given — „2026/3" ist mit dem Muster {JJJJ}/{N} eine gueltige Nummer; ein Schraegstrich im
    // Dateinamen ist bei jedem Empfaenger ein Pfadtrenner.

    // When / Then
    assertThat(Rechnungsdateiname.fuer("2026/3")).isEqualTo("Rechnung-2026-3.pdf");
  }

  @Test
  void fuer_givenSpacesAndAnUmlaut_thenBothBecomeHyphens() {
    // Given — ein Muster darf freien Text tragen, und der kann alles enthalten.

    // When / Then
    assertThat(Rechnungsdateiname.fuer("RE 7 Jänner")).isEqualTo("Rechnung-RE-7-J-nner.pdf");
  }

  @Test
  void fuer_givenAnUnderscoreAndADot_thenTheyStay() {
    // Given — Unterstrich und Punkt sind in einem Dateinamen harmlos.

    // When / Then
    assertThat(Rechnungsdateiname.fuer("2026_1.2")).isEqualTo("Rechnung-2026_1.2.pdf");
  }
}
