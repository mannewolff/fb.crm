package org.mwolff.fbcrm.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Die Kopfzeilen, mit denen eine Datei zum Herunterladen hinausgeht (Plan #150, E5).
 *
 * <p>Gegenstand ist allein der Dateiname, denn nur er kommt von aussen. Geprueft werden die drei
 * Zeichenklassen, die die Zeile zerreissen oder den Namen verfaelschen koennten: ein Umlaut (nicht
 * ASCII), ein Anfuehrungszeichen und ein Rueckstrich (beide beenden das Anfuehrungszeichen-Paar des
 * Rueckfalls von innen). Jeder Fall wird an <b>beiden</b> Formen geprueft — der ASCII-Rueckfall
 * muss brauchbar bleiben, und die Stern-Form muss den Namen vollstaendig tragen.
 */
class AnlagekopfTest {

  @Test
  void contentDisposition_thenAlwaysOffersTheFileForSaving() {
    // Given — E5: der Weg antwortet immer mit attachment, nie mit inline.

    // When
    final String kopf = Anlagekopf.contentDisposition("Bericht.pdf");

    // Then
    assertThat(kopf).startsWith("attachment; ");
  }

  @Test
  void contentDisposition_givenAPlainName_thenBothFormsCarryItUnchanged() {
    // When
    final String kopf = Anlagekopf.contentDisposition("Bericht.pdf");

    // Then
    assertThat(kopf)
        .isEqualTo("attachment; filename=\"Bericht.pdf\"; filename*=UTF-8''Bericht.pdf");
  }

  @Test
  void contentDisposition_givenAnUmlaut_thenTheFallbackMasksItAndTheStarFormEncodesIt() {
    // Given — ein Umlaut ist kein ASCII und hat im Rueckfall keinen Platz.

    // When
    final String kopf = Anlagekopf.contentDisposition("Übergabe.pdf");

    // Then
    assertThat(kopf)
        .isEqualTo("attachment; filename=\"_bergabe.pdf\"; filename*=UTF-8''%C3%9Cbergabe.pdf");
  }

  @Test
  void contentDisposition_givenAQuote_thenTheFallbackMasksItAndTheStarFormEncodesIt() {
    // Given — ein Anfuehrungszeichen beendete das Paar des Rueckfalls.

    // When
    final String kopf = Anlagekopf.contentDisposition("Be\"richt.pdf");

    // Then
    assertThat(kopf)
        .isEqualTo("attachment; filename=\"Be_richt.pdf\"; filename*=UTF-8''Be%22richt.pdf");
  }

  @Test
  void contentDisposition_givenABackslash_thenTheFallbackMasksItAndTheStarFormEncodesIt() {
    // Given — ein Rueckstrich maskierte im Rueckfall das naechste Zeichen.

    // When
    final String kopf = Anlagekopf.contentDisposition("Be\\richt.pdf");

    // Then
    assertThat(kopf)
        .isEqualTo("attachment; filename=\"Be_richt.pdf\"; filename*=UTF-8''Be%5Cricht.pdf");
  }

  @Test
  void contentDisposition_givenASpace_thenOnlyTheStarFormEncodesIt() {
    // Given — ein Leerzeichen ist im Rueckfall zulaessig (es steht in Anfuehrungszeichen), in der
    // Stern-Form aber keine attr-char.

    // When
    final String kopf = Anlagekopf.contentDisposition("Zwei Worte.pdf");

    // Then
    assertThat(kopf)
        .isEqualTo("attachment; filename=\"Zwei Worte.pdf\"; filename*=UTF-8''Zwei%20Worte.pdf");
  }

  @Test
  void contentDisposition_givenTheFirstAttrChar_thenBothFormsCarryItUnchanged() {
    // Given — „A" ist das erste Zeichen der attr-char-Liste. Es steht hier, weil die Pruefung mit
    // einer Grenze arbeitet: Nur ein Fund unterhalb von 0 heisst „nicht in der Liste". Faellt die
    // Grenze um eins, verliert das erste Zeichen seinen Platz und wird prozentkodiert.

    // When
    final String kopf = Anlagekopf.contentDisposition("Angebot.pdf");

    // Then
    assertThat(kopf)
        .isEqualTo("attachment; filename=\"Angebot.pdf\"; filename*=UTF-8''Angebot.pdf");
  }

  @Test
  void sandkasten_thenNamesTheHeaderAndThePolicyForTheContentRoute() {
    // Given — E5: die letzte Schranke, falls ein Empfaenger den Inhalt doch rendert.

    // When / Then
    assertThat(Anlagekopf.INHALTSREGEL).isEqualTo("Content-Security-Policy");
    assertThat(Anlagekopf.SANDKASTEN).isEqualTo("sandbox");
  }
}
