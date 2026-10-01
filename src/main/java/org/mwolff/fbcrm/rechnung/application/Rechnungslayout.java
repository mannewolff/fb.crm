package org.mwolff.fbcrm.rechnung.application;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.ExcludeFromJacocoGeneratedReport;
import org.mwolff.fbcrm.rechnung.application.RechnungDruckdaten.Druckposition;
import org.mwolff.fbcrm.rechnung.domain.Belegabsender;

/**
 * Setzt die Rechnung nach der verbindlichen Vorlage {@code docs/vorlage-rechnung.pdf} (E10).
 *
 * <p>Eine reine Rechnung: hinein gehen {@link RechnungDruckdaten}, heraus kommt die Folge der
 * {@link Druckelement}e. Keine Bibliothek, keine Uhr, kein Bestand — derselbe Beleg ergibt immer
 * dieselbe Folge, und ein Test kann jedes Stueck davon benennen. Gezeichnet wird erst danach, von
 * einem {@link Belegdrucker}.
 *
 * <p><b>Ganzzahlig in Punkten auf A4</b>, Ursprung unten links wie bei {@link Druckelement}. Die
 * Masse, die Reihenfolge und die Farben sind an der Vorlage abgenommen: der Randstreifen 20 Punkte
 * breit in ihrem Ton, der Satzspiegel von 71 bis 540, die Grundlinien der Bloecke, die
 * Spaltenkanten der Tabelle und ihre Linienstaerken. Ein halber Punkt faellt dabei auf die nachste
 * ganze Zahl; auf Papier ist der Unterschied nicht zu sehen, und ganze Zahlen machen den
 * Seitenumbruch zu einer Rechnung, deren Ergebnis ein Test genau benennen kann.
 *
 * <p><b>Was fehlt, hinterlaesst keine Luecke.</b> Berufsbezeichnung, Webadresse, Telefon, E-Mail,
 * Bankverbindung und jede Angabe einer Anschrift duerfen fehlen. Jeder Block wird deshalb von oben
 * nach unten aus den gefuellten Zeilen gesetzt; eine fehlende Zeile verbraucht keine Hoehe.
 *
 * <p><b>Der Umbruch in der Spalte wird geschaetzt.</b> Wie breit ein Text wirklich ist, weiss nur
 * der Drucker — er hat die Schrift. Der Satz rechnet deshalb mit einer mittleren Zeichenbreite von
 * {@value #ZEICHENBREITE_ZAEHLER}/{@value #ZEICHENBREITE_NENNER} der Schriftgroesse und bricht
 * lieber eine Silbe zu frueh um als eine zu spaet.
 */
/*
 * PMD.TooManyMethods: Der Satz eines ganzen Dokuments ist eine lange Folge von Schritten, und jeder
 * Schritt traegt hier seinen Namen — Kopf, Anschrift, Tabelle, Summenblock, Schluss, Fuss, dazu die
 * Setz- und Schreibhilfen. Die Schwelle der Regel liegt bei zehn Methoden. Sie zu halten hiesse,
 * die Schritte entweder zu wenigen grossen Methoden zusammenzuziehen oder die Masse des
 * Satzspiegels ueber mehrere Klassen zu verteilen, die sie sich alle teilen muessten — beides waere
 * schlechter zu lesen und zu aendern als die Folge kleiner, benannter Schritte an einer Stelle.
 * Arbeitspaket #181 verlangt zudem ausdruecklich eine Layoutklasse.
 */
@SuppressWarnings("PMD.TooManyMethods")
public final class Rechnungslayout {

  // --- Blatt und Satzspiegel (Vorlage: A4, Rand links 70,775, Linienende 540,225) ---

  /** Hoehe des Blattes in Punkten. */
  private static final int BLATT_HOEHE = 842;

  /** Linke Kante des Satzspiegels. */
  private static final int RAND_LINKS = 71;

  /** Rechte Kante des Satzspiegels; hier endet, was rechtsbuendig steht. */
  private static final int RAND_RECHTS = 540;

  // --- Farben (Vorlage) ---

  /** Der Ton des Randstreifens und der eigenen Webadresse im Kopf. */
  private static final Farbe LEITFARBE = new Farbe(47, 140, 151);

  /** Der Lauftext des Belegs. */
  private static final Farbe DUNKEL = new Farbe(36, 53, 57);

  /** Nebenangaben: Kopf rechts, Absenderzeile, Ort und Datum, Fuss. */
  private static final Farbe GEDAEMPFT = new Farbe(95, 122, 127);

  /** Die Berufsbezeichnung ueber dem Namen. */
  private static final Farbe BERUF = new Farbe(91, 171, 181);

  /** Die Linie unter dem Kopf. */
  private static final Farbe LINIE_HELL = new Farbe(216, 236, 238);

  /** Die Linie unter der Absenderzeile und ueber dem Fuss. */
  private static final Farbe LINIE = new Farbe(192, 216, 219);

  /** Linien und Text der Tabelle; die Vorlage setzt sie schwarz. */
  private static final Farbe TABELLENFARBE = new Farbe(0, 0, 0);

  // --- Randstreifen ---

  /** Breite des farbigen Streifens am linken Blattrand. */
  private static final int STREIFEN_BREITE = 20;

  // --- Kopf ---

  /** Grundlinie der obersten Zeile links im Kopf. */
  private static final int KOPF_OBEN = 777;

  /** Abstand der Berufsbezeichnung zum Namen. */
  private static final int ABSTAND_BERUF_NAME = 42;

  /** Abstand des Namens zur Webadresse. */
  private static final int ABSTAND_NAME_WEB = 19;

  /** Grundlinie der obersten Zeile rechts im Kopf. */
  private static final int KOPF_RECHTS_OBEN = 776;

  /** Zeilenabstand im rechten Kopfblock. */
  private static final int KOPF_ZEILE = 13;

  /** Die Linie unter dem Kopf. */
  private static final int KOPFLINIE = 686;

  /** Grundlinie der kleinen Absenderzeile ueber der Anschrift. */
  private static final int ABSENDERZEILE = 661;

  /** Die Linie unter der Absenderzeile. */
  private static final int ANSCHRIFTLINIE = 656;

  // --- Anschrift, Ort und Datum, Anrede ---

  /** Grundlinie der ersten Zeile der Empfaengeranschrift. */
  private static final int EMPFAENGER_OBEN = 639;

  /** Zeilenabstand im Lauftext. */
  private static final int ZEILE = 13;

  /** Zusaetzlicher Abstand vor der Ortszeile der Anschrift. */
  private static final int ABSATZ = 10;

  /** Grundlinie von Ort und Datum, rechts neben der Anschrift. */
  private static final int ORT_DATUM = 604;

  /** Grundlinie des Titels „Rechnung &lt;Nummer&gt;". */
  private static final int TITEL = 550;

  /** Grundlinie der Anrede. */
  private static final int ANREDE_OBEN = 523;

  /** Grundlinie des Einleitungssatzes. */
  private static final int EINLEITUNG_OBEN = 499;

  // --- Tabelle ---

  /** Obere Linie der Tabelle auf der ersten Seite. */
  private static final int TABELLE_OBEN = 467;

  /** Die Spaltenkanten von links nach rechts; die letzte ist die rechte Kante der Tabelle. */
  private static final int[] SPALTENKANTEN = {71, 136, 371, 436, 503};

  /** Kante zwischen Anzahl und Position. */
  private static final int KANTE_POSITION = SPALTENKANTEN[1];

  /** Kante zwischen Position und Einzelpreis. */
  private static final int KANTE_EINZELPREIS = SPALTENKANTEN[2];

  /** Kante zwischen Einzelpreis und Gesamtpreis. */
  private static final int KANTE_GESAMTPREIS = SPALTENKANTEN[3];

  /** Rechte Kante der Tabelle. */
  private static final int TABELLE_RECHTS = SPALTENKANTEN[4];

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

  /** Tiefster Punkt, den eine Tabellenzeile erreichen darf. */
  private static final int TABELLE_UNTERGRENZE = 120;

  /** Hoehe des Summenblocks samt Doppelstrich; so viel bleibt fuer ihn reserviert. */
  private static final int SUMMENBLOCK_HOEHE = 3 * ZEILENHOEHE + DOPPELSTRICH;

  // --- Schluss ---

  /** Abstand der Tabelle zum Satz ueber das Zahlungsziel. */
  private static final int ABSTAND_NACH_TABELLE = 40;

  /** Abstand des Zahlungsziels zum Gruss. */
  private static final int ABSTAND_GRUSS = 42;

  /** Abstand des Grusses zum Namen darunter. */
  private static final int ABSTAND_NAME = 16;

  /** Hoehe des Schlussblocks unterhalb seiner ersten Zeile. */
  private static final int SCHLUSS_HOEHE = ABSTAND_GRUSS + ABSTAND_NAME;

  /** Tiefster Punkt, den Lauftext erreichen darf; darunter beginnt der Fuss. */
  private static final int INHALT_UNTEN = 100;

  /** Oberste Grundlinie auf einer Folgeseite; dort steht kein Kopf. */
  private static final int FOLGESEITE_OBEN = 780;

  // --- Fuss ---

  /** Die Linie ueber dem Fuss. */
  private static final int FUSSLINIE = 69;

  /** Grundlinie der ersten Fusszeile. */
  private static final int FUSS_OBEN = 55;

  /** Zeilenabstand im Fuss. */
  private static final int FUSS_ZEILE = 9;

  // --- Schriftgroessen ---

  /** Berufsbezeichnung, Kopf rechts, Ort und Datum. */
  private static final int GROESSE_KLEIN = 9;

  /** Der Name im Kopf. */
  private static final int GROESSE_NAME = 26;

  /** Lauftext, Anschrift, Titel. */
  private static final int GROESSE_TEXT = 11;

  /** Die Tabelle. */
  private static final int GROESSE_TABELLE = 10;

  /** Die Absenderzeile ueber der Anschrift. */
  private static final int GROESSE_ABSENDERZEILE = 7;

  /** Der Fuss. */
  private static final int GROESSE_FUSS = 8;

  // --- Linienstaerken ---

  /** Eine gewoehnliche Linie. */
  private static final int STRICH = 1;

  /** Die kraeftige Linie unter der Kopfzeile, der letzten Position und der Steuer. */
  private static final int STRICH_KRAEFTIG = 2;

  // --- Umbruch ---

  /** Zaehler der geschaetzten mittleren Zeichenbreite, gemessen an der Schriftgroesse. */
  private static final int ZEICHENBREITE_ZAEHLER = 11;

  /** Nenner der geschaetzten mittleren Zeichenbreite. */
  private static final int ZEICHENBREITE_NENNER = 20;

  // --- Feste Wortlaute ---

  /** Die Anrede der Vorlage; sie nennt keine Person, weil der Beleg an die Firma geht. */
  private static final String ANREDE = "Sehr geehrte Damen und Herren,";

  /** Der Gruss der Vorlage; eine Unterschrift steht nicht darunter. */
  private static final String GRUSS = "Mit freundlichen Grüßen";

  /** Die Wortgrenze, an der ein Positionstext umbrochen wird. */
  private static final Pattern WORTGRENZE = Pattern.compile(" ");

  /** Das Datum der Vorlage: „28.09.2026". */
  private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private final RechnungDruckdaten daten;

  private final List<Druckelement> elemente = new ArrayList<>();

  private int seite = 1;

  private Rechnungslayout(final RechnungDruckdaten daten) {
    this.daten = daten;
  }

  /**
   * Setzt die Rechnung und liefert ihre Druckelemente in der Reihenfolge des Satzes.
   *
   * @param daten alles, was auf dem Dokument steht
   * @return die gesetzten Elemente; Seite 1 beginnt mit dem Randstreifen
   */
  public static List<Druckelement> setze(final RechnungDruckdaten daten) {
    return new Rechnungslayout(daten).gesetzt();
  }

  private List<Druckelement> gesetzt() {
    randstreifen();
    kopf();
    anschrift();
    einleitung();
    schluss(tabelle());
    fuss();
    return List.copyOf(elemente);
  }

  // --- Bloecke ---

  /** Der farbige Streifen an der linken Seite, ueber die ganze Hoehe der laufenden Seite. */
  private void randstreifen() {
    elemente.add(new Druckelement.Flaeche(seite, 0, 0, STREIFEN_BREITE, BLATT_HOEHE, LEITFARBE));
  }

  /** Links Berufsbezeichnung, Name und Webadresse; rechts die Erreichbarkeit. */
  private void kopf() {
    final Belegabsender absender = daten.absender();
    int hoehe = KOPF_OBEN;
    for (final String beruf : gefuellt(absender.berufsbezeichnung())) {
      links(hoehe, Schrift.FETT, GROESSE_KLEIN, BERUF, beruf);
      hoehe -= ABSTAND_BERUF_NAME;
    }
    links(hoehe, Schrift.FETT, GROESSE_NAME, DUNKEL, absender.name());
    hoehe -= ABSTAND_NAME_WEB;
    for (final String web : gefuellt(absender.webadresse())) {
      links(hoehe, Schrift.NORMAL, GROESSE_TEXT, LEITFARBE, web);
    }
    int rechteSpalte = KOPF_RECHTS_OBEN;
    for (final String zeile : kopfzeilenRechts(absender)) {
      rechts(rechteSpalte, GROESSE_KLEIN, GEDAEMPFT, zeile);
      rechteSpalte -= KOPF_ZEILE;
    }
    querlinie(RAND_LINKS, RAND_RECHTS, KOPFLINIE, STRICH, LINIE_HELL);
  }

  private static List<String> kopfzeilenRechts(final Belegabsender absender) {
    final List<String> zeilen = new ArrayList<>();
    zeilen.add(absender.name());
    zeilen.addAll(anschriftzeilen(absender.anschrift()));
    zeilen.addAll(
        gefuellt(mit("Tel ", absender.telefon()), absender.email(), absender.webadresse()));
    return zeilen;
  }

  /** Die Absenderzeile, die Anschrift der Firma und rechts daneben Ort und Datum. */
  private void anschrift() {
    final Belegabsender absender = daten.absender();
    final Anschrift eigene = absender.anschrift();
    for (final String zeile :
        gefuellt(verbunden(" · ", absender.name(), eigene.strasse(), ortszeile(eigene)))) {
      links(ABSENDERZEILE, Schrift.NORMAL, GROESSE_ABSENDERZEILE, GEDAEMPFT, zeile);
    }
    querlinie(RAND_LINKS, RAND_RECHTS, ANSCHRIFTLINIE, STRICH, LINIE);

    final Anschrift firma = daten.empfaenger().anschrift();
    final List<String> oben = new ArrayList<>();
    oben.add(daten.empfaenger().firma());
    oben.addAll(gefuellt(firma.strasse()));
    final int nachOben = stapelLinks(EMPFAENGER_OBEN, oben);
    stapelLinks(nachOben - ABSATZ, gefuellt(ortszeile(firma), firma.land()));

    rechts(ORT_DATUM, GROESSE_KLEIN, GEDAEMPFT, ortUndDatum(eigene.ort()));
  }

  private String ortUndDatum(final @Nullable String ort) {
    final String datum = DATUM.format(daten.rechnungDatum());
    if (ort == null) {
      return datum;
    }
    return ort + ", " + datum;
  }

  /** Titel, Anrede und der Satz, der die Tabelle ankuendigt. */
  private void einleitung() {
    links(TITEL, Schrift.FETT, GROESSE_TEXT, DUNKEL, "Rechnung " + daten.nummer());
    links(ANREDE_OBEN, Schrift.NORMAL, GROESSE_TEXT, DUNKEL, ANREDE);
    links(EINLEITUNG_OBEN, Schrift.NORMAL, GROESSE_TEXT, DUNKEL, einleitungssatz());
  }

  private String einleitungssatz() {
    final String zeitraum = daten.leistungszeitraum();
    if (zeitraum == null) {
      return "Für meine Leistungen stelle ich Ihnen in Rechnung";
    }
    return "Für meine Leistungen im " + zeitraum + " stelle ich Ihnen in Rechnung";
  }

  /**
   * Die Tabelle mit ihren Positionen und dem Summenblock.
   *
   * @return die Hoehe der untersten Linie der Tabelle
   */
  private int tabelle() {
    int hoehe = TABELLE_OBEN;
    int rahmenOben = hoehe;
    querlinie(RAND_LINKS, TABELLE_RECHTS, hoehe, STRICH, TABELLENFARBE);
    hoehe -= KOPFZEILE_HOEHE;
    spaltenkoepfe(hoehe + GRUNDLINIE);
    querlinie(RAND_LINKS, TABELLE_RECHTS, hoehe, STRICH_KRAEFTIG, TABELLENFARBE);

    final List<Druckposition> positionen = daten.positionen();
    for (int stelle = 0; stelle < positionen.size(); stelle++) {
      final boolean letzte = stelle == positionen.size() - 1;
      final List<String> zeilen = umbrochen(positionstext(positionen.get(stelle)));
      final int reserve = letzte ? SUMMENBLOCK_HOEHE : 0;
      final int benoetigt = zeilen.size() * ZEILENHOEHE + reserve;
      if (!haeltDieUntergrenze(hoehe - benoetigt, TABELLE_UNTERGRENZE)) {
        senkrechte(rahmenOben, hoehe);
        hoehe = seitenwechsel();
        rahmenOben = hoehe;
        querlinie(RAND_LINKS, TABELLE_RECHTS, hoehe, STRICH, TABELLENFARBE);
      }
      hoehe = positionszeile(positionen.get(stelle), zeilen, hoehe);
      querlinie(
          RAND_LINKS, TABELLE_RECHTS, hoehe, letzte ? STRICH_KRAEFTIG : STRICH, TABELLENFARBE);
    }

    hoehe = summenzeile(hoehe, "Gesamtbetrag netto", daten.netto(), STRICH);
    final String steuer = "+ " + ohneNachnullen(daten.steuersatz()) + "% Mehrwertsteuer";
    hoehe = summenzeile(hoehe, steuer, daten.steuer(), STRICH_KRAEFTIG);
    hoehe = summenzeile(hoehe, "Gesamtbetrag brutto", daten.brutto(), STRICH);
    querlinie(RAND_LINKS, TABELLE_RECHTS, hoehe - DOPPELSTRICH, STRICH, TABELLENFARBE);
    senkrechte(rahmenOben, hoehe);
    return hoehe - DOPPELSTRICH;
  }

  private void spaltenkoepfe(final int grundlinie) {
    tabellentext(RAND_LINKS + INNENABSTAND, grundlinie, Ausrichtung.LINKS, "Anzahl");
    tabellentext(KANTE_POSITION + INNENABSTAND, grundlinie, Ausrichtung.LINKS, "Position");
    tabellentext(KANTE_EINZELPREIS + INNENABSTAND, grundlinie, Ausrichtung.LINKS, "Einzelpreis");
    tabellentext(KANTE_GESAMTPREIS + INNENABSTAND, grundlinie, Ausrichtung.LINKS, "Gesamtpreis");
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
        KANTE_POSITION - INNENABSTAND,
        erste,
        Ausrichtung.RECHTS,
        ohneNachnullen(position.anzahl()));
    tabellentext(
        KANTE_GESAMTPREIS - INNENABSTAND,
        erste,
        Ausrichtung.RECHTS,
        betrag(position.einzelpreis()));
    tabellentext(
        TABELLE_RECHTS - INNENABSTAND, erste, Ausrichtung.RECHTS, betrag(position.gesamtpreis()));
    int grundlinie = erste;
    for (final String zeile : zeilen) {
      tabellentext(KANTE_POSITION + INNENABSTAND, grundlinie, Ausrichtung.LINKS, zeile);
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
        KANTE_EINZELPREIS - INNENABSTAND, unten + GRUNDLINIE, Ausrichtung.RECHTS, beschriftung);
    tabellentext(
        TABELLE_RECHTS - INNENABSTAND, unten + GRUNDLINIE, Ausrichtung.RECHTS, betrag(wert));
    querlinie(RAND_LINKS, TABELLE_RECHTS, unten, staerke, TABELLENFARBE);
    return unten;
  }

  /** Zahlungsziel, Gruss und Name; eine Unterschrift steht nicht darunter. */
  private void schluss(final int tabelleUnten) {
    int hoehe = tabelleUnten - ABSTAND_NACH_TABELLE;
    if (!haeltDieUntergrenze(hoehe - SCHLUSS_HOEHE, INHALT_UNTEN)) {
      hoehe = seitenwechsel();
    }
    links(hoehe, Schrift.NORMAL, GROESSE_TEXT, DUNKEL, zahlungssatz());
    hoehe -= ABSTAND_GRUSS;
    links(hoehe, Schrift.NORMAL, GROESSE_TEXT, DUNKEL, GRUSS);
    hoehe -= ABSTAND_NAME;
    links(hoehe, Schrift.NORMAL, GROESSE_TEXT, DUNKEL, daten.absender().name());
  }

  private String zahlungssatz() {
    if (daten.zahlungszielTage() == 0) {
      return "Bitte überweisen Sie den Betrag sofort";
    }
    return "Bitte überweisen Sie den Betrag innerhalb von " + daten.zahlungszielTage() + " Tagen";
  }

  /** Der Fuss steht auf jeder Seite; er entsteht, wenn feststeht, wie viele es sind. */
  private void fuss() {
    final Belegabsender absender = daten.absender();
    final List<String> zeilen =
        gefuellt(
            verbunden(" · ", absender.name(), absender.webadresse()),
            steuerzeile(absender),
            mit("Bankverbindung: ", absender.bankverbindung()));
    for (int blatt = 1; blatt <= seite; blatt++) {
      elemente.add(
          new Druckelement.Linie(
              blatt, RAND_LINKS, FUSSLINIE, RAND_RECHTS, FUSSLINIE, STRICH, LINIE));
      int hoehe = FUSS_OBEN;
      for (final String zeile : zeilen) {
        elemente.add(
            new Druckelement.Text(
                blatt,
                RAND_LINKS,
                hoehe,
                Schrift.NORMAL,
                GROESSE_FUSS,
                GEDAEMPFT,
                Ausrichtung.LINKS,
                zeile));
        hoehe -= FUSS_ZEILE;
      }
    }
  }

  /** Die Umsatzsteuer-Identifikationsnummer; ohne sie die Steuernummer. */
  private static @Nullable String steuerzeile(final Belegabsender absender) {
    if (absender.umsatzsteuerId() != null) {
      return mit("USt-IdNr. ", absender.umsatzsteuerId());
    }
    return mit("Steuernummer ", absender.steuernummer());
  }

  /*
   * Ob der Rest der Seite die Untergrenze noch haelt — die eine Frage, die ueber jeden
   * Seitenwechsel des Satzes entscheidet.
   *
   * Eine eigene Methode allein fuer den Vergleich, und methodengenau ausgenommen nach
   * CLAUDE-java.md §5.4: Ob hier „kleiner" oder „kleiner gleich" steht, ist nicht beobachtbar,
   * denn Gleichheit kann nicht eintreten. In der Tabelle ist jede Hoehe TABELLE_OBEN -
   * KOPFZEILE_HOEHE = 449 oder FOLGESEITE_OBEN = 780 minus ein Vielfaches von ZEILENHOEHE, davon
   * noch SUMMENBLOCK_HOEHE oder nichts abgezogen; modulo ZEILENHOEHE ergibt das 1, 15, 12 oder 10,
   * TABELLE_UNTERGRENZE dagegen 8. Beim Schluss verlangte Gleichheit eine Hoehe von genau 248, und
   * 248 liegt modulo ZEILENHOEHE ebenfalls bei 8. Ein Test dafuer waere nicht zu schreiben, ohne
   * die an der Vorlage abgenommenen Masse zu verbiegen.
   *
   * Dass hier entschieden wird, bleibt vollstaendig geprueft: Die Verzweigung traegt der Aufrufer,
   * und beide Richtungen stehen namentlich in RechnungslayoutTest — fuer die Tabelle
   * setze_givenSiebzehnPositionen_… gegen setze_givenAchtzehnPositionen_…, fuer den Schluss
   * setze_givenZwoelfPositionen_… gegen setze_givenDreizehnPositionen_….
   */
  @ExcludeFromJacocoGeneratedReport
  private static boolean haeltDieUntergrenze(final int rest, final int untergrenze) {
    return rest >= untergrenze;
  }

  private int seitenwechsel() {
    seite++;
    randstreifen();
    return FOLGESEITE_OBEN;
  }

  // --- Setzhilfen ---

  private void links(
      final int hoehe,
      final Schrift schrift,
      final int groesse,
      final Farbe farbe,
      final String text) {
    elemente.add(
        new Druckelement.Text(
            seite, RAND_LINKS, hoehe, schrift, groesse, farbe, Ausrichtung.LINKS, text));
  }

  private void rechts(final int hoehe, final int groesse, final Farbe farbe, final String text) {
    elemente.add(
        new Druckelement.Text(
            seite, RAND_RECHTS, hoehe, Schrift.NORMAL, groesse, farbe, Ausrichtung.RECHTS, text));
  }

  private void tabellentext(
      final int kante, final int grundlinie, final Ausrichtung ausrichtung, final String text) {
    elemente.add(
        new Druckelement.Text(
            seite,
            kante,
            grundlinie,
            Schrift.NORMAL,
            GROESSE_TABELLE,
            TABELLENFARBE,
            ausrichtung,
            text));
  }

  private int stapelLinks(final int oben, final List<String> zeilen) {
    int hoehe = oben;
    for (final String zeile : zeilen) {
      links(hoehe, Schrift.NORMAL, GROESSE_TEXT, DUNKEL, zeile);
      hoehe -= ZEILE;
    }
    return hoehe;
  }

  private void querlinie(
      final int von, final int bis, final int hoehe, final int staerke, final Farbe farbe) {
    elemente.add(new Druckelement.Linie(seite, von, hoehe, bis, hoehe, staerke, farbe));
  }

  private void senkrechte(final int oben, final int unten) {
    for (final int kante : SPALTENKANTEN) {
      elemente.add(new Druckelement.Linie(seite, kante, unten, kante, oben, STRICH, TABELLENFARBE));
    }
  }

  // --- Schreibweisen ---

  /** Die Einheit steht vor dem Text; eine Pauschale traegt nur ihren Text. */
  private static String positionstext(final Druckposition position) {
    return switch (position.einheit()) {
      case STUNDE -> "Stunden " + position.text();
      case PERSONENTAG -> "Personentage " + position.text();
      case PAUSCHAL -> position.text();
    };
  }

  /** Ein Betrag mit Tausenderpunkt, zwei Nachkommastellen und Eurozeichen: „1.200,00 €". */
  private static String betrag(final BigDecimal wert) {
    return String.format(Locale.GERMANY, "%,.2f €", wert);
  }

  /** Eine Zahl ohne Nachnullen: „3", „2,5", „19", „19,5". */
  private static String ohneNachnullen(final BigDecimal wert) {
    return wert.stripTrailingZeros().toPlainString().replace('.', ',');
  }

  // --- Zeilen aus freiwilligen Angaben ---

  /** Die gefuellten Werte in ihrer Reihenfolge; ein fehlender laesst keine Luecke. */
  private static List<String> gefuellt(final @Nullable String... werte) {
    return Arrays.stream(werte).filter(Objects::nonNull).toList();
  }

  /** Die gefuellten Werte, mit dem Trenner verbunden; {@code null}, wenn keiner gefuellt ist. */
  private static @Nullable String verbunden(final String trenner, final @Nullable String... werte) {
    final List<String> teile = gefuellt(werte);
    if (teile.isEmpty()) {
      return null;
    }
    return String.join(trenner, teile);
  }

  /** Der Wert mit seiner Beschriftung davor; {@code null}, wenn er fehlt. */
  private static @Nullable String mit(final String beschriftung, final @Nullable String wert) {
    if (wert == null) {
      return null;
    }
    return beschriftung + wert;
  }

  private static List<String> anschriftzeilen(final Anschrift anschrift) {
    return gefuellt(anschrift.strasse(), ortszeile(anschrift), anschrift.land());
  }

  private static @Nullable String ortszeile(final Anschrift anschrift) {
    return verbunden(" ", anschrift.plz(), anschrift.ort());
  }

  // --- Umbruch in der Spalte ---

  /** Bricht den Text auf die Breite der Spalte „Position" um. */
  private static List<String> umbrochen(final String text) {
    final int spalte = KANTE_EINZELPREIS - KANTE_POSITION - 2 * INNENABSTAND;
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
