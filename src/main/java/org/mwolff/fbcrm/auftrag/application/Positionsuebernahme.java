package org.mwolff.fbcrm.auftrag.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.auftrag.domain.Auftragsposition;
import org.mwolff.fbcrm.common.Abrechnungsmodus;

/**
 * Die Pruefung und der Bau der uebernommenen Positionen (Kriterium 2, F2, Plan E20).
 *
 * <p>Eine eigene Klasse und kein Teil von {@link AuftragAnlegenUseCase}: Dort steht der Ablauf des
 * Anlegens — vier Vorbedingungen, Nummer, Schreiben, Ereignis —, hier die Regel, was aus einer
 * Positionswahl werden darf. Beides in einer Klasse liest sich als ein langer Vorgang und zieht
 * deren Kopplung ueber die Grenze, die {@code PMD.CouplingBetweenObjects} zieht.
 *
 * <p><b>Was der Absender nicht aendern darf, kommt aus dem Angebot</b> (Plan E7): Bezeichnung,
 * Abrechnungsmodus, Einheit und Einzelpreis liest diese Klasse aus der Quelle; aus der Wahl kommen
 * nur Menge und „Stunden je Personentag". Einer Anfrage den Preis zu glauben, hiesse, genau die
 * Angabe vom Absender zu nehmen, die Kriterium 2 schuetzt.
 *
 * <p><b>Alle Beanstandungen auf einmal, nicht die erste.</b> Wer zwei Zeilen zu hoch gesetzt hat,
 * soll das in einem Durchgang erfahren. Beanstandet wird je Zeile unter ihrem Index in der
 * <b>Anfrage</b> und nicht unter dem Platz im Angebot: Die Maske kennt ihre eigene Reihenfolge,
 * nicht die der Quelle.
 */
final class Positionsuebernahme {

  /** Die Beanstandung eines Platzes, den das Angebot nicht hat. */
  static final String PLATZ_UNBEKANNT = "Diese Position gibt es im Angebot nicht.";

  /** Die Beanstandung desselben Platzes zum zweiten Mal. */
  static final String PLATZ_DOPPELT = "Diese Position ist schon uebernommen.";

  /** Die Beanstandung einer Menge oberhalb der vereinbarten. */
  static final String MENGE_ZU_GROSS =
      "Mehr als im Angebot vereinbart laesst sich nicht beauftragen.";

  /** Die Beanstandung einer Wahl, die nichts beauftragt. */
  static final String NICHTS_BESTELLT = "Mindestens eine Position braucht eine Menge.";

  /** Die Beanstandung des Faktors an einer Position, die nicht nach Aufwand abgerechnet wird. */
  static final String STUNDEN_UNERWARTET =
      "Nur eine Aufwandsposition traegt Stunden je Personentag.";

  /** Die Beanstandung des fehlenden Faktors an einer Aufwandsposition. */
  static final String STUNDEN_FEHLEN = "Eine Aufwandsposition braucht Stunden je Personentag.";

  /** Der Schluessel der Liste als Ganzes. */
  static final String FELD_POSITIONEN = "positionen";

  private Positionsuebernahme() {}

  /**
   * Die Positionen des Auftrags, gebaut aus dem Angebot und der Wahl des Anwenders.
   *
   * @param angebot das Angebot, aus dem uebernommen wird
   * @param wahlen die Wahl des Anwenders in ihrer Reihenfolge
   * @throws AuftragsuebernahmeUngueltig wenn die Wahl nicht zum Angebot passt
   */
  static List<Auftragsposition> aus(final Angebot angebot, final List<AuftragPositionwahl> wahlen) {
    final Map<String, List<String>> fehler = new LinkedHashMap<>();
    // Vor der Schleife und nicht in ihr: Auch eine Zeile, deren Platz nicht stimmt, ist erkennbar
    // als Bestellung gemeint — sonst stuende neben ihrer Beanstandung auch noch "nichts bestellt".
    if (wahlen.stream().noneMatch(wahl -> wahl.menge().signum() > 0)) {
      melde(fehler, FELD_POSITIONEN, NICHTS_BESTELLT);
    }
    final List<Auftragsposition> positionen = new ArrayList<>(wahlen.size());
    final Set<Integer> schonGewaehlt = new HashSet<>();
    for (int index = 0; index < wahlen.size(); index++) {
      final AuftragPositionwahl wahl = wahlen.get(index);
      final String feld = FELD_POSITIONEN + "[" + index + "]";
      if (wahl.platz() < 1 || wahl.platz() > angebot.positionen().size()) {
        melde(fehler, feld + ".platz", PLATZ_UNBEKANNT);
      } else if (!schonGewaehlt.add(wahl.platz())) {
        // Zweimal derselbe Platz waere eine hinzugefuegte Position, und die verbietet F2.
        melde(fehler, feld + ".platz", PLATZ_DOPPELT);
      } else {
        positionen.add(uebernommen(angebot.positionen().get(wahl.platz() - 1), wahl, feld, fehler));
      }
    }
    if (!fehler.isEmpty()) {
      throw new AuftragsuebernahmeUngueltig(fehler);
    }
    return positionen;
  }

  /* Eine Position: Menge und Stundenfaktor kommen aus der Wahl, alles andere aus dem Angebot. */
  private static Auftragsposition uebernommen(
      final Angebotsposition quelle,
      final AuftragPositionwahl wahl,
      final String feld,
      final Map<String, List<String>> fehler) {
    if (wahl.menge().compareTo(quelle.menge()) > 0) {
      melde(fehler, feld + ".menge", MENGE_ZU_GROSS);
    }
    pruefeStunden(quelle.abrechnungsmodus(), wahl.stundenJePersonentag(), feld, fehler);
    return new Auftragsposition(
        quelle.bezeichnung(),
        quelle.abrechnungsmodus(),
        wahl.menge(),
        quelle.einheit(),
        quelle.einzelpreis(),
        wahl.stundenJePersonentag());
  }

  /*
   * "Stunden je Personentag" haengt am Abrechnungsmodus der Quelle und nicht an der Einheit (E10):
   * Eine Aufwandsposition braucht den Faktor, eine Festpreisposition darf ihn nicht tragen. Der
   * Ausdruck nennt beide Modi, statt einen auszunehmen — ein dritter Modus faellt so durch beide.
   */
  private static void pruefeStunden(
      final Abrechnungsmodus modus,
      final @Nullable BigDecimal stundenJePersonentag,
      final String feld,
      final Map<String, List<String>> fehler) {
    final @Nullable String beanstandung =
        switch (modus) {
          case AUFWAND -> stundenJePersonentag == null ? STUNDEN_FEHLEN : null;
          case FESTPREIS -> stundenJePersonentag == null ? null : STUNDEN_UNERWARTET;
        };
    if (beanstandung != null) {
      melde(fehler, feld + ".stundenJePersonentag", beanstandung);
    }
  }

  private static void melde(
      final Map<String, List<String>> fehler, final String feld, final String meldung) {
    fehler.computeIfAbsent(feld, unbekannt -> new ArrayList<>()).add(meldung);
  }
}
