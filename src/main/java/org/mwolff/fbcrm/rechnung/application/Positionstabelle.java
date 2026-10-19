package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;
import org.mwolff.fbcrm.rechnung.application.RechnungDruckdaten.Druckposition;

/**
 * Die Positionstabelle des Belegs: Spaltenkoepfe, Positionszeilen mit Spaltenumbruch, Seitenwechsel
 * und Summenblock (Plan #363, E7).
 *
 * <p>Kupferwolke und Briefbogen setzen die Tabelle nach derselben Regel; was sie unterscheidet,
 * bringen sie mit: ihren {@link Satzspiegel} und einen Rueckruf, der festlegt, was auf einem neuen
 * Blatt zuerst entsteht — bei der Kupferwolke der Randstreifen, auf dem Briefbogen nichts. Der
 * Rueckruf hoert die Nummer des neuen Blattes.
 *
 * <p>Die Tabelle fuehrt die laufende Seite des ganzen Belegs: Sie ist es, die umbricht, und auch
 * der Block darunter bekommt seinen Platz von ihr ({@link #platzFuer}). Nach dem Satz nennt {@link
 * #seite()} darum die Zahl der benutzten Seiten.
 *
 * <p><b>Der Umbruch in der Spalte wird geschaetzt.</b> Wie breit ein Text wirklich ist, weiss nur
 * der Drucker — er hat die Schrift. Der Satz rechnet deshalb mit einer mittleren Zeichenbreite von
 * {@value #ZEICHENBREITE_ZAEHLER}/{@value #ZEICHENBREITE_NENNER} der Schriftgroesse und bricht
 * lieber eine Silbe zu frueh um als eine zu spaet.
 */
/*
 * PMD.TooManyMethods: Die Tabelle ist eine Folge benannter Satzschritte — Kopfzeile, Position,
 * Summenzeile, Rahmen, Seitenwechsel, Umbruch — und dazu die eine Frage, ob der Rest der Seite die
 * Untergrenze haelt. Die Schwelle der Regel liegt bei zehn Methoden. Sie zu halten hiesse, die
 * Schritte zu wenigen grossen Methoden zusammenzuziehen oder den Umbruch abzuspalten, der allein
 * fuer diese Spalte gilt — beides waere schlechter zu lesen als die Folge kleiner Schritte an einer
 * Stelle.
 */
@SuppressWarnings("PMD.TooManyMethods")
final class Positionstabelle {

  /** Linien und Text der Tabelle; die Vorlage setzt sie schwarz. */
  private static final Farbe TABELLENFARBE = new Farbe(0, 0, 0);

  /** Hoehe der Kopfzeile der Tabelle. */
  private static final int KOPFZEILE_HOEHE = 18;

  /** Hoehe einer Zeile der Tabelle. */
  private static final int ZEILENHOEHE = 16;

  /** Abstand des Textes zur Spaltenkante. */
  private static final int INNENABSTAND = 4;

  /** Abstand der Grundlinie von der unteren Linie ihrer Zeile. */
  private static final int GRUNDLINIE = 5;

  /** Abstand der beiden Striche unter dem Bruttobetrag. */
  private static final int DOPPELSTRICH = 2;

  /** Hoehe des Summenblocks samt Doppelstrich; so viel bleibt fuer ihn reserviert. */
  private static final int SUMMENBLOCK_HOEHE = 3 * ZEILENHOEHE + DOPPELSTRICH;

  /** Die Schriftgroesse der Tabelle. */
  private static final int GROESSE_TABELLE = 10;

  /** Eine gewoehnliche Linie. */
  private static final int STRICH = 1;

  /** Die kraeftige Linie unter der Kopfzeile, der letzten Position und der Steuer. */
  private static final int STRICH_KRAEFTIG = 2;

  /** Zaehler der geschaetzten mittleren Zeichenbreite, gemessen an der Schriftgroesse. */
  private static final int ZEICHENBREITE_ZAEHLER = 11;

  /** Nenner der geschaetzten mittleren Zeichenbreite. */
  private static final int ZEICHENBREITE_NENNER = 20;

  /** Die Wortgrenze, an der ein Positionstext umbrochen wird. */
  private static final Pattern WORTGRENZE = Pattern.compile(" ");

  private final Satzspiegel spiegel;

  private final List<Druckelement> elemente;

  private final IntConsumer neuesBlatt;

  private int laufendeSeite = 1;

  /**
   * Eine Tabelle, die ihre Elemente an die Folge des Belegs anhaengt.
   *
   * @param spiegel die Masse, in denen sie steht
   * @param elemente die Folge des Belegs; die Tabelle haengt an, der Rueckruf darf es auch
   * @param neuesBlatt was auf einem neuen Blatt zuerst entsteht; er hoert dessen Nummer
   */
  Positionstabelle(
      final Satzspiegel spiegel, final List<Druckelement> elemente, final IntConsumer neuesBlatt) {
    this.spiegel = spiegel;
    this.elemente = elemente;
    this.neuesBlatt = neuesBlatt;
  }

  /**
   * Die laufende Seite; nach dem Satz die Zahl der benutzten Seiten.
   *
   * @return die Nummer der Seite, ab 1
   */
  int seite() {
    return laufendeSeite;
  }

  /**
   * Setzt die Tabelle mit ihren Positionen und dem Summenblock auf die laufende Seite.
   *
   * @param daten die Positionen und Summen
   * @param oben die Hoehe der oberen Linie der Tabelle
   * @return die Hoehe der untersten Linie der Tabelle
   */
  int setze(final RechnungDruckdaten daten, final int oben) {
    int hoehe = oben;
    int rahmenOben = hoehe;
    querlinie(hoehe, STRICH);
    hoehe -= KOPFZEILE_HOEHE;
    spaltenkoepfe(hoehe + GRUNDLINIE);
    querlinie(hoehe, STRICH_KRAEFTIG);

    final List<Druckposition> positionen = daten.positionen();
    for (int stelle = 0; stelle < positionen.size(); stelle++) {
      final boolean letzte = stelle == positionen.size() - 1;
      final List<String> zeilen = umbrochen(positionstext(positionen.get(stelle)));
      final int reserve = letzte ? SUMMENBLOCK_HOEHE : 0;
      final int benoetigt = zeilen.size() * ZEILENHOEHE + reserve;
      if (!haeltDieUntergrenze(hoehe - benoetigt, spiegel.tabelleUntergrenze())) {
        senkrechte(rahmenOben, hoehe);
        hoehe = seitenwechsel();
        rahmenOben = hoehe;
        querlinie(hoehe, STRICH);
      }
      hoehe = positionszeile(positionen.get(stelle), zeilen, hoehe);
      querlinie(hoehe, letzte ? STRICH_KRAEFTIG : STRICH);
    }

    hoehe = summenzeile(hoehe, "Gesamtbetrag netto", daten.netto(), STRICH);
    final String steuer = "+ " + Belegschreibweisen.zahl(daten.steuersatz()) + "% Mehrwertsteuer";
    hoehe = summenzeile(hoehe, steuer, daten.steuer(), STRICH_KRAEFTIG);
    hoehe = summenzeile(hoehe, "Gesamtbetrag brutto", daten.brutto(), STRICH);
    querlinie(hoehe - DOPPELSTRICH, STRICH);
    senkrechte(rahmenOben, hoehe);
    return hoehe - DOPPELSTRICH;
  }

  /**
   * Wo ein Block beginnt, der von {@code hoehe} aus {@code tiefe} Punkte nach unten reicht: dort,
   * wenn er die Inhaltsuntergrenze haelt, sonst oben auf einem neuen Blatt. Zerrissen wird er
   * nicht.
   *
   * @param hoehe die Grundlinie seiner ersten Zeile auf der laufenden Seite
   * @param tiefe wie weit er unter seine erste Zeile reicht
   * @return die Grundlinie seiner ersten Zeile
   */
  int platzFuer(final int hoehe, final int tiefe) {
    if (haeltDieUntergrenze(hoehe - tiefe, spiegel.inhaltUntergrenze())) {
      return hoehe;
    }
    return seitenwechsel();
  }

  private void spaltenkoepfe(final int grundlinie) {
    tabellentext(spiegel.links() + INNENABSTAND, grundlinie, Ausrichtung.LINKS, "Anzahl");
    tabellentext(spiegel.kantePosition() + INNENABSTAND, grundlinie, Ausrichtung.LINKS, "Position");
    tabellentext(
        spiegel.kanteEinzelpreis() + INNENABSTAND, grundlinie, Ausrichtung.LINKS, "Einzelpreis");
    tabellentext(
        spiegel.kanteGesamtpreis() + INNENABSTAND, grundlinie, Ausrichtung.LINKS, "Gesamtpreis");
  }

  /**
   * Eine Position; die Zahlen stehen auf der ersten ihrer Zeilen.
   *
   * @return die Hoehe der Linie unter der Position
   */
  private int positionszeile(
      final Druckposition position, final List<String> zeilen, final int oben) {
    final int erste = oben - ZEILENHOEHE + GRUNDLINIE;
    tabellentext(
        spiegel.kantePosition() - INNENABSTAND,
        erste,
        Ausrichtung.RECHTS,
        Belegschreibweisen.zahl(position.anzahl()));
    tabellentext(
        spiegel.kanteGesamtpreis() - INNENABSTAND,
        erste,
        Ausrichtung.RECHTS,
        Belegschreibweisen.betrag(position.einzelpreis()));
    tabellentext(
        spiegel.rechts() - INNENABSTAND,
        erste,
        Ausrichtung.RECHTS,
        Belegschreibweisen.betrag(position.gesamtpreis()));
    int grundlinie = erste;
    for (final String zeile : zeilen) {
      tabellentext(spiegel.kantePosition() + INNENABSTAND, grundlinie, Ausrichtung.LINKS, zeile);
      grundlinie -= ZEILENHOEHE;
    }
    return oben - zeilen.size() * ZEILENHOEHE;
  }

  /**
   * Eine Zeile des Summenblocks: links die Beschriftung, rechts der Betrag.
   *
   * @return die Hoehe der Linie unter der Zeile
   */
  private int summenzeile(
      final int oben, final String beschriftung, final BigDecimal wert, final int staerke) {
    final int unten = oben - ZEILENHOEHE;
    tabellentext(
        spiegel.kanteEinzelpreis() - INNENABSTAND,
        unten + GRUNDLINIE,
        Ausrichtung.RECHTS,
        beschriftung);
    tabellentext(
        spiegel.rechts() - INNENABSTAND,
        unten + GRUNDLINIE,
        Ausrichtung.RECHTS,
        Belegschreibweisen.betrag(wert));
    querlinie(unten, staerke);
    return unten;
  }

  /**
   * Ob der Rest der Seite die Untergrenze noch haelt — die eine Frage, die ueber jeden
   * Seitenwechsel des Satzes entscheidet. Die Grenze selbst gehoert noch zur Seite.
   */
  private static boolean haeltDieUntergrenze(final int rest, final int untergrenze) {
    return rest >= untergrenze;
  }

  private int seitenwechsel() {
    laufendeSeite++;
    neuesBlatt.accept(laufendeSeite);
    return spiegel.folgeseiteOben();
  }

  // --- Setzhilfen ---

  private void tabellentext(
      final int kante, final int grundlinie, final Ausrichtung ausrichtung, final String text) {
    elemente.add(
        new Druckelement.Text(
            laufendeSeite,
            kante,
            grundlinie,
            Schrift.NORMAL,
            GROESSE_TABELLE,
            TABELLENFARBE,
            ausrichtung,
            text));
  }

  private void querlinie(final int hoehe, final int staerke) {
    elemente.add(
        new Druckelement.Linie(
            laufendeSeite,
            spiegel.links(),
            hoehe,
            spiegel.rechts(),
            hoehe,
            staerke,
            TABELLENFARBE));
  }

  private void senkrechte(final int oben, final int unten) {
    for (final int kante : spiegel.spaltenkanten()) {
      elemente.add(
          new Druckelement.Linie(laufendeSeite, kante, unten, kante, oben, STRICH, TABELLENFARBE));
    }
  }

  // --- Schreibweise und Umbruch der Spalte „Position" ---

  /** Die Einheit steht vor dem Text; eine Pauschale traegt nur ihren Text. */
  private static String positionstext(final Druckposition position) {
    return switch (position.einheit()) {
      case STUNDE -> "Stunden " + position.text();
      case PERSONENTAG -> "Personentage " + position.text();
      case PAUSCHAL -> position.text();
    };
  }

  /** Bricht den Text auf die Breite der Spalte „Position" um. */
  private List<String> umbrochen(final String text) {
    final int spalte = spiegel.positionsbreite() - 2 * INNENABSTAND;
    final int hoechstens =
        spalte * ZEICHENBREITE_NENNER / (GROESSE_TABELLE * ZEICHENBREITE_ZAEHLER);
    final List<String> zeilen = new ArrayList<>();
    final StringBuilder zeile = new StringBuilder();
    for (final String wort : WORTGRENZE.split(text, -1)) {
      for (final String stueck : zerlegt(wort, hoechstens)) {
        if (!zeile.isEmpty() && zeile.length() + 1 + stueck.length() > hoechstens) {
          zeilen.add(zeile.toString());
          zeile.setLength(0);
        }
        if (!zeile.isEmpty()) {
          zeile.append(' ');
        }
        zeile.append(stueck);
      }
    }
    zeilen.add(zeile.toString());
    return zeilen;
  }

  /** Ein Wort, das allein schon breiter ist als die Spalte, wird hart getrennt. */
  private static List<String> zerlegt(final String wort, final int hoechstens) {
    final List<String> stuecke = new ArrayList<>();
    for (int anfang = 0; anfang < wort.length(); anfang += hoechstens) {
      stuecke.add(wort.substring(anfang, Math.min(anfang + hoechstens, wort.length())));
    }
    return stuecke;
  }
}
