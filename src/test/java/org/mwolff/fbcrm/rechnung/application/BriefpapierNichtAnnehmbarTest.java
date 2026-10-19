package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Die sechs Gruende, aus denen ein Briefpapier nicht angenommen wird, jeder mit seinem Satz am Feld
 * {@code datei} (Plan #363, E5 und E17).
 *
 * <p>Die drei inhaltsunabhaengigen Gruende — leer, zu gross, kein PDF — prueft spaeter der
 * Anwendungsfall; die uebrigen drei prueft der Adapter, und dort weist {@code
 * BriefpapierpruefungTest} sie am echten PDF nach. Hier steht nur, was jede Erzeugermethode sagt.
 */
class BriefpapierNichtAnnehmbarTest {

  @Test
  void leer_thenSaysTheFileIsEmpty() {
    // When / Then
    assertMeldung(BriefpapierNichtAnnehmbar.leer(), "Die Datei ist leer.");
  }

  @Test
  void zuGross_thenNamesTheOwnLimit() {
    // When / Then — die eigene Grenze, nicht die der uebrigen Uploads (E17).
    assertMeldung(BriefpapierNichtAnnehmbar.zuGross(), Briefpapiergrenze.MELDUNG);
  }

  @Test
  void keinPdf_thenSaysItIsNoPdf() {
    // When / Then
    assertMeldung(BriefpapierNichtAnnehmbar.keinPdf(), BriefpapierNichtAnnehmbar.MELDUNG_KEIN_PDF);
  }

  @Test
  void nichtLadbar_thenSaysEncryptedOrUnreadable() {
    // When / Then
    assertMeldung(
        BriefpapierNichtAnnehmbar.nichtLadbar(), BriefpapierNichtAnnehmbar.MELDUNG_NICHT_LADBAR);
  }

  @Test
  void nichtLadbar_givenUrsache_thenSameSentenceAndKeepsTheCause() {
    // Given
    final IOException parser = new IOException("Page tree root must be a dictionary");

    // When
    final BriefpapierNichtAnnehmbar abweisung = BriefpapierNichtAnnehmbar.nichtLadbar(parser);

    // Then — der Satz verraet nicht, wie weit der Parser kam; der Stapel behaelt es.
    assertMeldung(abweisung, BriefpapierNichtAnnehmbar.MELDUNG_NICHT_LADBAR);
    assertThat(abweisung).hasCause(parser);
  }

  @Test
  void zuVieleSeiten_thenNamesTheLimitOfTwoPages() {
    // When / Then
    assertMeldung(
        BriefpapierNichtAnnehmbar.zuVieleSeiten(),
        BriefpapierNichtAnnehmbar.MELDUNG_ZU_VIELE_SEITEN);
  }

  @Test
  void falschesFormat_thenNamesA4Portrait() {
    // When / Then
    assertMeldung(
        BriefpapierNichtAnnehmbar.falschesFormat(), BriefpapierNichtAnnehmbar.MELDUNG_FORMAT);
  }

  @Test
  void meldungen_thenAreSixDifferentSentences() {
    // When / Then — jeder Grund mit seinem eigenen Satz (Kriterium 2).
    assertThat(
            List.of(
                BriefpapierNichtAnnehmbar.MELDUNG_LEER,
                BriefpapierNichtAnnehmbar.MELDUNG_ZU_GROSS,
                BriefpapierNichtAnnehmbar.MELDUNG_KEIN_PDF,
                BriefpapierNichtAnnehmbar.MELDUNG_NICHT_LADBAR,
                BriefpapierNichtAnnehmbar.MELDUNG_ZU_VIELE_SEITEN,
                BriefpapierNichtAnnehmbar.MELDUNG_FORMAT))
        .doesNotHaveDuplicates()
        .allSatisfy(meldung -> assertThat(meldung).isNotBlank());
  }

  @Test
  void meldungen_thenNameFormatPagesAndEncryption() {
    // When / Then — der Satz nennt den Grund (Kriterium 2).
    assertThat(BriefpapierNichtAnnehmbar.MELDUNG_FORMAT).contains("A4 hochkant");
    assertThat(BriefpapierNichtAnnehmbar.MELDUNG_ZU_VIELE_SEITEN).contains("2 Seiten");
    assertThat(BriefpapierNichtAnnehmbar.MELDUNG_NICHT_LADBAR).contains("verschlüsselt");
  }

  private static void assertMeldung(
      final BriefpapierNichtAnnehmbar abweisung, final String meldung) {
    assertThat(abweisung).hasMessage(meldung);
    assertThat(abweisung.felder()).isEqualTo(Map.of("datei", List.of(meldung)));
  }
}
