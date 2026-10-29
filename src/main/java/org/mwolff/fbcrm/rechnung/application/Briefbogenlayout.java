package org.mwolff.fbcrm.rechnung.application;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.rechnung.domain.Belegabsender;
import org.mwolff.fbcrm.rechnung.domain.Briefbogen;
import org.mwolff.fbcrm.rechnung.domain.Randmasse;

/**
 * Setzt die Rechnung auf das eigene Briefpapier des Betreibers (Plan #363, E7, E10, E11, E12).
 *
 * <p>Das zweite Layout neben {@link Rechnungslayout}: derselbe Beleg, aber auf fremdem Papier. Wie
 * dort gehen die Daten hinein und die Folge der {@link Druckelement}e kommt heraus — keine
 * Bibliothek, keine Uhr, kein Bestand. Das Briefpapier selbst kennt der Satz nicht; es legt erst
 * der Drucker darunter. Vom {@link Briefbogen} liest er nur die beiden Schalter und die Raender.
 *
 * <p><b>Alles Eingedruckte ist schwarz</b>, Text wie Linien. Randstreifen, Kopf und Fuss der
 * Kupferwolke fehlen: Was den Absender ausmacht, traegt das Briefpapier.
 *
 * <p><b>Fest, unabhaengig von den Raendern</b> (E11): Anschriftfeld und Absenderzeile stehen nach
 * DIN 5008 Form B, sonst erscheint die Anschrift nicht im Fenster des Kuverts. <b>Den Raendern
 * folgend</b> (E12): ab dem Beginn des Inhalts Ort und Datum, Titel, Anrede, Einleitung, Tabelle,
 * Summen, Zahlungsziel, Gruss und Name, mit den Abstaenden der Kupferwolke. Folgeseiten tragen
 * ihren eigenen oberen und unteren Rand; die Tabelle behaelt die Spaltenbreiten der Kupferwolke und
 * beginnt am linken Rand.
 *
 * <p><b>Die Steuer- und Bankzeile</b> (E10) steht auf jeder Seite ueber deren unterem Rand. Ihren
 * Platz haelt der Satz am Schalter frei, nicht am Inhalt: Steht er an, endet alles darueber um ihre
 * Hoehe hoeher, auch wenn die Zeile mangels Angaben leer bleibt. So bleibt die Randpruefung eine
 * Funktion ueber Raendern und Schaltern allein.
 *
 * <p><b>Was fehlt, hinterlaesst keine Luecke</b>, wie bei der Kupferwolke. Fehlt der eigene Name —
 * im Probedruck moeglich, auf einer gestellten Rechnung nicht —, fallen Absenderzeile und
 * Namenszeile weg, und unter dem Gruss bleibt keine Hoehe fuer ihn reserviert (E14).
 */
public final class Briefbogenlayout {

  // --- Blatt (A4 in ganzen Punkten) ---

  /** Breite des Blattes; an ihr misst auch die Randpruefung den rechten Rand. */
  static final int BLATT_BREITE = 595;

  /** Hoehe des Blattes. */
  private static final int BLATT_HOEHE = 842;

  /** Text wie Linien. */
  private static final Farbe SCHWARZ = new Farbe(0, 0, 0);

  // --- Fest nach DIN 5008 Form B (E11), in Zehntel-Millimetern von der Blattkante ---

  /** Linke Kante von Anschriftfeld und Absenderzeile: 20 mm. */
  private static final int ANSCHRIFT_LINKS = Randmasse.inPunkten(200);

  /** Grundlinie der Absenderzeile: 61 mm unter der oberen Blattkante. */
  private static final int ABSENDERZEILE = BLATT_HOEHE - Randmasse.inPunkten(610);

  /** Erste Grundlinie der Anschrift: 4 mm unter der Oberkante des Feldes bei 62,7 mm. */
  private static final int EMPFAENGER_OBEN = BLATT_HOEHE - Randmasse.inPunkten(627 + 40);

  /** Zeilenabstand der Anschrift, wie auf der Kupferwolke. */
  private static final int ZEILE = 13;

  /** Zusaetzlicher Abstand vor der Ortszeile der Anschrift, wie auf der Kupferwolke. */
  private static final int ABSATZ = 10;

  // --- Den Raendern folgend (E12): die Abstaende der Kupferwolke ---

  /** Von Ort und Datum zum Titel. */
  private static final int ABSTAND_TITEL = 54;

  /** Vom Titel zur Anrede. */
  private static final int ABSTAND_ANREDE = 27;

  /** Von der Anrede zum Einleitungssatz. */
  private static final int ABSTAND_EINLEITUNG = 24;

  /** Vom Einleitungssatz zur oberen Linie der Tabelle. */
  private static final int ABSTAND_TABELLE = 32;

  /** Von der Tabelle zum Satz ueber das Zahlungsziel. */
  private static final int ABSTAND_NACH_TABELLE = 40;

  /** Vom Zahlungsziel zum Gruss. */
  private static final int ABSTAND_GRUSS = 42;

  /** Vom Gruss zum Namen darunter. */
  private static final int ABSTAND_NAME = 16;

  // --- Spaltenbreiten der Tabelle, wie auf der Kupferwolke ---

  /** Die Spalte „Anzahl". */
  private static final int BREITE_ANZAHL = 65;

  /** Die Spalte „Position". */
  private static final int BREITE_POSITION = 235;

  /** Die Spalte „Einzelpreis". */
  private static final int BREITE_EINZELPREIS = 65;

  /** Die Spalte „Gesamtpreis". */
  private static final int BREITE_GESAMTPREIS = 67;

  // --- Schriftgroessen ---

  /** Ort und Datum, wie auf der Kupferwolke. */
  private static final int GROESSE_KLEIN = 9;

  /** Lauftext, Anschrift, Titel. */
  private static final int GROESSE_TEXT = 11;

  /** Die Absenderzeile ueber der Anschrift (E11). */
  private static final int GROESSE_ABSENDERZEILE = 7;

  /** Die Steuer- und Bankzeile (E10). */
  private static final int GROESSE_STEUER_BANK = 8;

  // --- Steuer- und Bankzeile (E10) ---

  /** Abstand ihrer Grundlinie vom unteren Rand der Seite: 4 mm. */
  private static final int STEUER_BANK_GRUNDLINIE = Randmasse.inPunkten(40);

  /**
   * Was sie ueber dem unteren Rand belegt: 4 mm bis zur Grundlinie, ihre Schrift und noch einmal 4
   * mm Luft zu dem, was darueber endet — zusammen 30 Punkte.
   */
  private static final int STEUER_BANK_HOEHE = 2 * STEUER_BANK_GRUNDLINIE + GROESSE_STEUER_BANK;

  private final RechnungDruckdaten daten;

  /** Der eigene Name; er darf nur im Probedruck fehlen. */
  private final @Nullable String name;

  private final Briefbogen bogen;

  /** Der linke Rand in Punkten; hier beginnen Titel, Tabelle und Schluss. */
  private final int links;

  /** Der untere Rand der ersten Seite in Punkten. */
  private final int untenErsteSeite;

  /** Der untere Rand jeder Folgeseite in Punkten. */
  private final int untenFolgeseite;

  private final List<Druckelement> elemente = new ArrayList<>();

  /** Die Tabelle fuehrt die laufende Seite; auf einem neuen Blatt entsteht zuerst nichts. */
  private final Positionstabelle tabelle;

  private Briefbogenlayout(
      final RechnungDruckdaten daten, final @Nullable String name, final Briefbogen bogen) {
    this.daten = daten;
    this.name = name;
    this.bogen = bogen;
    final Randmasse raender = bogen.raender();
    this.links = Randmasse.inPunkten(raender.links());
    this.untenErsteSeite = Randmasse.inPunkten(raender.unten());
    this.untenFolgeseite = Randmasse.inPunkten(raender.folgeUnten());
    final int folgeseiteOben = BLATT_HOEHE - Randmasse.inPunkten(raender.folgeOben());
    this.tabelle =
        new Positionstabelle(
            satzspiegel(untenErsteSeite, folgeseiteOben),
            satzspiegel(untenFolgeseite, folgeseiteOben),
            elemente,
            blatt -> {});
  }

  /**
   * Setzt eine Rechnung auf den Briefbogen und liefert ihre Druckelemente in der Reihenfolge des
   * Satzes.
   *
   * @param daten alles, was auf dem Dokument steht
   * @param bogen die Schalter und Raender, mit denen gesetzt wird
   * @return die gesetzten Elemente, alle schwarz
   */
  public static List<Druckelement> setze(final RechnungDruckdaten daten, final Briefbogen bogen) {
    return new Briefbogenlayout(daten, daten.absender().name(), bogen).gesetzt();
  }

  /**
   * Setzt den Probedruck auf den Briefbogen; sein eigener Name darf fehlen (E14).
   *
   * @param probe die Beispieldaten mit den echten eigenen Angaben
   * @param bogen die Schalter und Raender, mit denen gesetzt wird
   * @return die gesetzten Elemente, alle schwarz
   */
  static List<Druckelement> setze(final Probedruckdaten probe, final Briefbogen bogen) {
    return new Briefbogenlayout(probe.druckdaten(), probe.name(), bogen).gesetzt();
  }

  /**
   * Ob auf einer Folgeseite die erste Position der Probe Platz hat — die dritte Bedingung der
   * Randpruefung (Kriterium 13; E9), gestellt an dieselbe Tabelle, die den Satz umbricht.
   *
   * @param probe die Beispieldaten; gemessen wird ihre erste Position
   * @param bogen die Schalter und Raender, mit denen gesetzt wuerde
   * @return ob eine Folgeseite diese Position fasst
   */
  static boolean folgeseiteFasst(final Probedruckdaten probe, final Briefbogen bogen) {
    return new Briefbogenlayout(probe.druckdaten(), probe.name(), bogen)
        .tabelle.passtAufFolgeseite(probe.druckdaten().positionen().getFirst());
  }

  private List<Druckelement> gesetzt() {
    anschrift();
    final int beginn = BLATT_HOEHE - Randmasse.inPunkten(bogen.raender().inhaltOben());
    schluss(tabelle.setze(daten, einleitung(beginn)));
    steuerBankZeilen();
    return List.copyOf(elemente);
  }

  /**
   * Die Masse der Tabelle auf einer Seite: Spalten ab dem linken Rand, Untergrenze ueber dem
   * unteren Rand dieser Seite — um die Steuer- und Bankzeile hoeher, wenn ihr Schalter an ist.
   */
  private Satzspiegel satzspiegel(final int unten, final int folgeseiteOben) {
    final int untergrenze = unten + (bogen.steuerBankZeile() ? STEUER_BANK_HOEHE : 0);
    final int kantePosition = links + BREITE_ANZAHL;
    final int kanteEinzelpreis = kantePosition + BREITE_POSITION;
    final int kanteGesamtpreis = kanteEinzelpreis + BREITE_EINZELPREIS;
    return new Satzspiegel(
        links,
        kantePosition,
        kanteEinzelpreis,
        kanteGesamtpreis,
        kanteGesamtpreis + BREITE_GESAMTPREIS,
        untergrenze,
        untergrenze,
        folgeseiteOben);
  }

  // --- Bloecke ---

  /** Die Absenderzeile, wenn sie eingeschaltet ist und es einen Namen gibt, und die Anschrift. */
  private void anschrift() {
    final Anschrift eigene = daten.absender().anschrift();
    if (bogen.absenderzeile() && name != null) {
      text(
          ANSCHRIFT_LINKS,
          ABSENDERZEILE,
          GROESSE_ABSENDERZEILE,
          Ausrichtung.LINKS,
          String.join(
              " · ",
              Belegschreibweisen.gefuellt(
                  name, eigene.strasse(), Belegschreibweisen.ortszeile(eigene))));
    }
    final Anschrift firma = daten.empfaenger().anschrift();
    final List<String> oben = new ArrayList<>();
    oben.add(daten.empfaenger().firma());
    oben.addAll(Belegschreibweisen.gefuellt(firma.strasse()));
    final int nachOben = stapel(EMPFAENGER_OBEN, oben);
    stapel(
        nachOben - ABSATZ,
        Belegschreibweisen.gefuellt(Belegschreibweisen.ortszeile(firma), firma.land()));
  }

  /**
   * Ort und Datum am rechten Rand, darunter Titel, Anrede und Einleitungssatz am linken.
   *
   * @param beginn die Grundlinie von Ort und Datum: der eingestellte Beginn des Inhalts
   * @return die Hoehe der oberen Linie der Tabelle
   */
  private int einleitung(final int beginn) {
    final int rechts = BLATT_BREITE - Randmasse.inPunkten(bogen.raender().rechts());
    text(
        rechts,
        beginn,
        GROESSE_KLEIN,
        Ausrichtung.RECHTS,
        Belegschreibweisen.ortUndDatum(daten.absender().anschrift().ort(), daten.rechnungDatum()));
    int hoehe = beginn - ABSTAND_TITEL;
    elemente.add(
        new Druckelement.Text(
            tabelle.seite(),
            links,
            hoehe,
            Schrift.FETT,
            GROESSE_TEXT,
            SCHWARZ,
            Ausrichtung.LINKS,
            "Rechnung " + daten.nummer()));
    hoehe -= ABSTAND_ANREDE;
    text(links, hoehe, GROESSE_TEXT, Ausrichtung.LINKS, Belegschreibweisen.ANREDE);
    hoehe -= ABSTAND_EINLEITUNG;
    text(
        links,
        hoehe,
        GROESSE_TEXT,
        Ausrichtung.LINKS,
        Belegschreibweisen.einleitungssatz(daten.leistungszeitraum()));
    return hoehe - ABSTAND_TABELLE;
  }

  /** Zahlungsziel, Gruss und — wenn es ihn gibt — der Name; zerrissen wird der Block nicht. */
  private void schluss(final int tabelleUnten) {
    final int tiefe = name == null ? ABSTAND_GRUSS : ABSTAND_GRUSS + ABSTAND_NAME;
    int hoehe = tabelle.platzFuer(tabelleUnten - ABSTAND_NACH_TABELLE, tiefe);
    text(
        links,
        hoehe,
        GROESSE_TEXT,
        Ausrichtung.LINKS,
        Belegschreibweisen.zahlungssatz(daten.zahlungszielTage()));
    hoehe -= ABSTAND_GRUSS;
    text(links, hoehe, GROESSE_TEXT, Ausrichtung.LINKS, Belegschreibweisen.GRUSS);
    if (name != null) {
      text(links, hoehe - ABSTAND_NAME, GROESSE_TEXT, Ausrichtung.LINKS, name);
    }
  }

  /**
   * Die Steuer- und Bankzeile auf jeder Seite; sie entsteht, wenn feststeht, wie viele es sind.
   * Ohne Schalter oder ohne jede ihrer Angaben entsteht keine.
   */
  private void steuerBankZeilen() {
    final Belegabsender absender = daten.absender();
    final String zeile =
        Belegschreibweisen.verbunden(
            " · ",
            Belegschreibweisen.steuerzeile(absender),
            Belegschreibweisen.bankzeile(absender));
    if (!bogen.steuerBankZeile() || zeile == null) {
      return;
    }
    for (int blatt = 1; blatt <= tabelle.seite(); blatt++) {
      final int unten = blatt == 1 ? untenErsteSeite : untenFolgeseite;
      elemente.add(
          new Druckelement.Text(
              blatt,
              links,
              unten + STEUER_BANK_GRUNDLINIE,
              Schrift.NORMAL,
              GROESSE_STEUER_BANK,
              SCHWARZ,
              Ausrichtung.LINKS,
              zeile));
    }
  }

  // --- Setzhilfen ---

  /** Ein Text im Lauftextschnitt auf der laufenden Seite. */
  private void text(
      final int x,
      final int y,
      final int groesse,
      final Ausrichtung ausrichtung,
      final String text) {
    elemente.add(
        new Druckelement.Text(
            tabelle.seite(), x, y, Schrift.NORMAL, groesse, SCHWARZ, ausrichtung, text));
  }

  /** Die Zeilen der Anschrift untereinander; zurueck kommt die Grundlinie unter der letzten. */
  private int stapel(final int oben, final List<String> zeilen) {
    int hoehe = oben;
    for (final String zeile : zeilen) {
      text(ANSCHRIFT_LINKS, hoehe, GROESSE_TEXT, Ausrichtung.LINKS, zeile);
      hoehe -= ZEILE;
    }
    return hoehe;
  }
}
