package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;
import org.mwolff.fbcrm.rechnung.application.Randgrenzen.Rand;
import org.mwolff.fbcrm.rechnung.domain.Randmasse;

/**
 * Die Randpruefung aus Kriterium 13 und der groesste zulaessige Wert aus derselben Regel (Plan
 * #363, E9).
 *
 * <p>Die erwarteten Grenzwerte sind von Hand aus den Massen des Satzes gerechnet, nicht aus der
 * Pruefung selbst — sonst bestaetigte der Test nur, dass die Suche mit sich uebereinstimmt. Bei den
 * Vorgaben aus Kriterium 15 und beiden Schaltern an gilt in Punkten ({@code punkte = (zehntelMm *
 * 72 + 127) / 254}):
 *
 * <ul>
 *   <li>Die Tabelle ist 432 breit (65 + 235 + 65 + 67) und beginnt am linken Rand (71); sie endet
 *       hoechstens am rechten Rand (595 - 57 = 538).
 *   <li>Der Positionstext der Probe bricht auf zwei Zeilen um: 32 hoch.
 *   <li>Unter dem Beginn des Inhalts folgen Titel (54), Anrede (27), Einleitung (24), Tabelle (32),
 *       Kopfzeile (18), Position (32), Summen (50), Zahlungsziel (40), Gruss (42) und Name (16):
 *       zusammen 335.
 *   <li>Die Steuer- und Bankzeile hebt jede Untergrenze um 30.
 * </ul>
 */
class RandgrenzenTest {

  /** Die Raender aus Kriterium 15 in Zehntel-Millimetern. */
  private static final Randmasse VORGABE = new Randmasse(250, 200, 985, 200, 200, 200);

  private static EigeneAngaben leer() {
    return new EigeneAngaben(
        null,
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
        null);
  }

  private static EigeneAngaben gefuellt() {
    return new EigeneAngaben(
        "Max Mustermann",
        "AGILE COACH",
        new Anschrift("Muster Str. 34a", "28197", "Bremen", null),
        "info@mmuster.org",
        "0173 0000 000",
        "mmuster.org",
        "12/345/67890",
        "DE 01010101010",
        null,
        null,
        null,
        "MLP Bank Heidelberg");
  }

  private static boolean passt(final Randmasse masse) {
    return Randgrenzen.passt(masse, true, true);
  }

  // --- passt: die Vorgaben und die drei Abweisgruende einzeln ---

  @Test
  void passt_givenDieVorgaben_thenPasst() {
    // When / Then
    assertThat(passt(VORGABE)).isTrue();
  }

  @Test
  void passt_givenBeginnDesInhaltsZuTief_thenPasstAufSeiteEinsNichtMehr() {
    // Given — 563 - 335 = 228 ueber dem Blattfuss; mit 148,4 mm beginnt der Inhalt bei 842 - 421
    // = 421, und der Name endete bei 86, unter der Untergrenze 57 + 30 = 87.
    final Randmasse zuTief = new Randmasse(250, 200, 1484, 200, 200, 200);

    // When / Then
    assertThat(passt(zuTief)).isFalse();
    assertThat(passt(new Randmasse(250, 200, 1483, 200, 200, 200))).isTrue();
  }

  @Test
  void passt_givenLinkerUndRechterRandZuWeitInnen_thenStehtDieTabelleNichtInVollerBreite() {
    // Given — beide 40 mm: die Tabelle liefe von 113 bis 545, der rechte Rand liegt bei 482.
    final Randmasse zuSchmal = new Randmasse(400, 400, 985, 200, 200, 200);

    // When / Then
    assertThat(passt(zuSchmal)).isFalse();
  }

  @Test
  void passt_givenFolgeseiteZuKnapp_thenHatDortKeineTabellenzeilePlatz() {
    // Given — oben 425, also beginnt die Folgeseite bei 417; die Position reichte bis 385, die
    // Untergrenze liegt bei 397 + 30 = 427.
    final Randmasse zuKnapp = new Randmasse(250, 200, 985, 200, 1500, 1400);

    // When / Then
    assertThat(passt(zuKnapp)).isFalse();
  }

  @Test
  void passt_givenDieSchalter_thenHaeltNurDieEingeschalteteSteuerUndBankzeileIhrenPlatzFrei() {
    // Given — Folgeseite oben 260 mm (737): ohne die Zeile endet die Position bei 73 ueber der
    // Untergrenze 57, mit ihr unter der Untergrenze 87.
    final Randmasse masse = new Randmasse(250, 200, 985, 200, 2600, 200);

    // When / Then
    assertThat(Randgrenzen.passt(masse, true, false)).isTrue();
    assertThat(Randgrenzen.passt(masse, false, false)).isTrue();
    assertThat(Randgrenzen.passt(masse, true, true)).isFalse();
    assertThat(Randgrenzen.passt(masse, false, true)).isFalse();
  }

  @Test
  void passt_givenLeereUndGefuellteEigeneAngaben_thenEntscheidenAlleinRaenderUndSchalter() {
    // Given — der untere Rand an seiner Grenze und einen Zehntel-Millimeter darueber. Die leeren
    // Angaben tragen weder Name noch Steuer- und Bankzeile; gemessen wird trotzdem mit beidem.
    final Randmasse anDerGrenze = new Randmasse(250, 200, 985, 700, 200, 200);
    final Randmasse darueber = new Randmasse(250, 200, 985, 701, 200, 200);
    final Probedruckdaten ohneAngaben = Probedruckdaten.von(leer());
    final Probedruckdaten mitAngaben = Probedruckdaten.von(gefuellt());

    // When / Then
    assertThat(Randgrenzen.passt(ohneAngaben, anDerGrenze, true, true)).isTrue();
    assertThat(Randgrenzen.passt(mitAngaben, anDerGrenze, true, true)).isTrue();
    assertThat(Randgrenzen.passt(ohneAngaben, darueber, true, true)).isFalse();
    assertThat(Randgrenzen.passt(mitAngaben, darueber, true, true)).isFalse();
    assertThat(passt(anDerGrenze)).isTrue();
    assertThat(passt(darueber)).isFalse();
  }

  // --- groesstZulaessig: dieselbe Regel, ueber die Zehntel-Millimeter gesucht ---

  @ParameterizedTest(name = "{0} hoechstens {1}")
  @CsvSource({
    // 71 + 432 <= 538 traegt links bis 106 Punkte.
    "LINKS, 375",
    // 595 - 503 = 92 Punkte rechter Rand.
    "RECHTS, 326",
    // 842 - 335 - 87 = 420 Punkte bis zum Beginn des Inhalts.
    "INHALT_OBEN, 1483",
    // 563 - 335 - 30 = 198 Punkte unterer Rand.
    "UNTEN, 700",
    // 842 - 32 - 87 = 723 Punkte oberer Rand der Folgeseite.
    "FOLGE_OBEN, 2552",
    // 785 - 32 - 30 = 723 Punkte unterer Rand der Folgeseite.
    "FOLGE_UNTEN, 2552"
  })
  void groesstZulaessig_givenDieVorgaben_thenPasstDieGrenzeUndDerNaechsteZehntelNicht(
      final Rand rand, final int grenze) {
    // When
    final OptionalInt gefunden = Randgrenzen.groesstZulaessig(rand, VORGABE, true, true);

    // Then
    assertThat(gefunden).hasValue(grenze);
    assertThat(passt(rand.mit(VORGABE, grenze))).isTrue();
    assertThat(passt(rand.mit(VORGABE, grenze + 1))).isFalse();
  }

  @Test
  void groesstZulaessig_givenDieSchalter_thenSuchtSieMitIhnen() {
    // When — ohne die Zeile liegt die Untergrenze der Folgeseite bei 57: 842 - 32 - 57 = 753.
    final OptionalInt gefunden =
        Randgrenzen.groesstZulaessig(Rand.FOLGE_OBEN, VORGABE, false, false);

    // Then
    assertThat(gefunden).hasValue(2658);
  }

  @Test
  void groesstZulaessig_givenAuchNullPasstNicht_thenGibtEsKeinenGrenzwert() {
    // Given — der Inhalt beginnt am Blattfuss; kein linker Rand macht daraus eine Seite.
    final Randmasse ohneAusweg = new Randmasse(250, 200, 2970, 200, 200, 200);

    // When
    final OptionalInt gefunden = Randgrenzen.groesstZulaessig(Rand.LINKS, ohneAusweg, true, true);

    // Then
    assertThat(gefunden).isEmpty();
    assertThat(new RandNichtMoeglich(Rand.LINKS, gefunden).getMessage())
        .isEqualTo(RandNichtMoeglich.MELDUNG_KEIN_WERT);
  }

  @Test
  void groesstZulaessig_givenKaumPlatz_thenIstDerKleinsteZehntelDieGrenze() {
    // Given — rechts 57,4 mm (163 Punkte): die Tabelle hat genau 432 Punkte ab der Blattkante.
    // Ein linker Rand von 0,1 mm rundet noch auf 0 Punkte, ab 0,2 mm auf einen.
    final Randmasse knapp = new Randmasse(0, 574, 985, 200, 200, 200);

    // When / Then
    assertThat(Randgrenzen.groesstZulaessig(Rand.LINKS, knapp, true, true)).hasValue(1);
  }

  // --- abweichenderRand ---

  @Test
  void abweichenderRand_givenKeinerWeichtAb_thenKeiner() {
    // When / Then
    assertThat(Randgrenzen.abweichenderRand(VORGABE, VORGABE)).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({"LINKS", "RECHTS", "INHALT_OBEN", "UNTEN", "FOLGE_OBEN", "FOLGE_UNTEN"})
  void abweichenderRand_givenGenauEinerWeichtAb_thenDieser(final Rand rand) {
    // Given
    final Randmasse neu = rand.mit(VORGABE, rand.wert(VORGABE) + 1);

    // When / Then
    assertThat(Randgrenzen.abweichenderRand(VORGABE, neu)).contains(rand);
  }

  @Test
  void abweichenderRand_givenMehrereWeichenAb_thenDerErsteInDerReihenfolge() {
    // Given — Folgeseite unten, unten und rechts weichen ab; rechts steht vorn.
    final Randmasse neu = new Randmasse(250, 300, 985, 300, 200, 300);

    // When / Then
    assertThat(Randgrenzen.abweichenderRand(VORGABE, neu)).contains(Rand.RECHTS);
  }

  @Test
  void abweichenderRand_givenDieLetztenBeiden_thenFolgeseiteOben() {
    // Given
    final Randmasse neu = new Randmasse(250, 200, 985, 200, 300, 300);

    // When / Then
    assertThat(Randgrenzen.abweichenderRand(VORGABE, neu)).contains(Rand.FOLGE_OBEN);
  }

  // --- Rand ---

  @ParameterizedTest(name = "{0} am Feld {1}")
  @CsvSource({
    "LINKS, links",
    "RECHTS, rechts",
    "INHALT_OBEN, inhaltOben",
    "UNTEN, unten",
    "FOLGE_OBEN, folgeOben",
    "FOLGE_UNTEN, folgeUnten"
  })
  void feld_thenTraegtDerRandDenNamenSeinerKomponente(final Rand rand, final String feld) {
    // When / Then
    assertThat(rand.feld()).isEqualTo(feld);
  }

  @Test
  void mit_thenAendertNurDenEinenRand() {
    // Given
    final Randmasse masse = new Randmasse(1, 2, 3, 4, 5, 6);

    // When / Then
    assertThat(Rand.LINKS.mit(masse, 9)).isEqualTo(new Randmasse(9, 2, 3, 4, 5, 6));
    assertThat(Rand.RECHTS.mit(masse, 9)).isEqualTo(new Randmasse(1, 9, 3, 4, 5, 6));
    assertThat(Rand.INHALT_OBEN.mit(masse, 9)).isEqualTo(new Randmasse(1, 2, 9, 4, 5, 6));
    assertThat(Rand.UNTEN.mit(masse, 9)).isEqualTo(new Randmasse(1, 2, 3, 9, 5, 6));
    assertThat(Rand.FOLGE_OBEN.mit(masse, 9)).isEqualTo(new Randmasse(1, 2, 3, 4, 9, 6));
    assertThat(Rand.FOLGE_UNTEN.mit(masse, 9)).isEqualTo(new Randmasse(1, 2, 3, 4, 5, 9));
  }

  @Test
  void wert_thenLiestDerRandSeineKomponente() {
    // Given
    final Randmasse masse = new Randmasse(1, 2, 3, 4, 5, 6);

    // When / Then
    assertThat(Rand.values())
        .extracting(rand -> rand.wert(masse))
        .containsExactly(1, 2, 3, 4, 5, 6);
  }
}
