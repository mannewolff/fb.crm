package org.mwolff.fbcrm.rechnung.application;

import java.util.ArrayList;
import java.util.List;
import org.mwolff.fbcrm.common.Anschrift;
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
 * <p><b>Die Tabelle setzt die {@link Positionstabelle}</b> im {@link Satzspiegel} der Vorlage, und
 * die Schreibweisen von Betrag, Zahl und zusammengesetzten Zeilen stehen in {@link
 * Belegschreibweisen} — beides teilt sich die Kupferwolke mit dem {@link Briefbogenlayout} (Plan
 * #363, E7), ebenso die festen Saetze von Anrede bis Gruss. Was hier bleibt, ist allein die
 * Gestaltung der Kupferwolke: Randstreifen, Kopf, Anschrift, Schluss und Fuss.
 */
/*
 * PMD.TooManyMethods: Der Satz eines ganzen Dokuments ist eine lange Folge von Schritten, und jeder
 * Schritt traegt hier seinen Namen — Randstreifen, Kopf, Anschrift, Einleitung, Schluss, Fuss, dazu
 * die Setzhilfen. Die Schwelle der Regel liegt bei zehn Methoden. Was sich Kupferwolke und
 * Briefbogen teilen — der Satzspiegel der Tabelle, die Tabelle selbst und die Schreibweisen —,
 * tragen schon eigene Klassen (Plan #363, E7). Die Schwelle zu halten hiesse jetzt nur noch, die
 * benannten Schritte zu wenigen grossen Methoden zusammenzuziehen — schlechter zu lesen und zu
 * aendern als ihre Folge an einer Stelle.
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

  // --- Schluss ---

  /** Abstand der Tabelle zum Satz ueber das Zahlungsziel. */
  private static final int ABSTAND_NACH_TABELLE = 40;

  /** Abstand des Zahlungsziels zum Gruss. */
  private static final int ABSTAND_GRUSS = 42;

  /** Abstand des Grusses zum Namen darunter. */
  private static final int ABSTAND_NAME = 16;

  /** Hoehe des Schlussblocks unterhalb seiner ersten Zeile. */
  private static final int SCHLUSS_HOEHE = ABSTAND_GRUSS + ABSTAND_NAME;

  /**
   * Der Satzspiegel der Tabelle nach der Vorlage: die Spaltenkanten von links nach rechts, unter
   * 120 faengt keine Tabellenzeile mehr an, unter 100 beginnt der Fuss, und auf einer Folgeseite
   * steht kein Kopf, sie beginnt bei 780.
   */
  private static final Satzspiegel SATZSPIEGEL =
      new Satzspiegel(RAND_LINKS, 136, 371, 436, 503, 120, 100, 780);

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

  /** Die Absenderzeile ueber der Anschrift. */
  private static final int GROESSE_ABSENDERZEILE = 7;

  /** Der Fuss. */
  private static final int GROESSE_FUSS = 8;

  // --- Linienstaerken ---

  /** Eine gewoehnliche Linie. */
  private static final int STRICH = 1;

  private final RechnungDruckdaten daten;

  private final List<Druckelement> elemente = new ArrayList<>();

  /** Die Tabelle fuehrt die laufende Seite; auf jedem neuen Blatt entsteht zuerst der Streifen. */
  private final Positionstabelle tabelle;

  private Rechnungslayout(final RechnungDruckdaten daten) {
    this.daten = daten;
    this.tabelle = new Positionstabelle(SATZSPIEGEL, elemente, this::randstreifen);
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
    randstreifen(tabelle.seite());
    kopf();
    anschrift();
    einleitung();
    schluss(tabelle.setze(daten, TABELLE_OBEN));
    fuss();
    return List.copyOf(elemente);
  }

  // --- Bloecke ---

  /** Der farbige Streifen an der linken Seite, ueber die ganze Hoehe des Blattes. */
  private void randstreifen(final int blatt) {
    elemente.add(new Druckelement.Flaeche(blatt, 0, 0, STREIFEN_BREITE, BLATT_HOEHE, LEITFARBE));
  }

  /** Links Berufsbezeichnung, Name und Webadresse; rechts die Erreichbarkeit. */
  private void kopf() {
    final Belegabsender absender = daten.absender();
    int hoehe = KOPF_OBEN;
    for (final String beruf : Belegschreibweisen.gefuellt(absender.berufsbezeichnung())) {
      links(hoehe, Schrift.FETT, GROESSE_KLEIN, BERUF, beruf);
      hoehe -= ABSTAND_BERUF_NAME;
    }
    links(hoehe, Schrift.FETT, GROESSE_NAME, DUNKEL, absender.name());
    hoehe -= ABSTAND_NAME_WEB;
    for (final String web : Belegschreibweisen.gefuellt(absender.webadresse())) {
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
        Belegschreibweisen.gefuellt(
            Belegschreibweisen.mit("Tel ", absender.telefon()),
            absender.email(),
            absender.webadresse()));
    return zeilen;
  }

  /** Die Absenderzeile, die Anschrift der Firma und rechts daneben Ort und Datum. */
  private void anschrift() {
    final Belegabsender absender = daten.absender();
    final Anschrift eigene = absender.anschrift();
    for (final String zeile :
        Belegschreibweisen.gefuellt(
            Belegschreibweisen.verbunden(
                " · ", absender.name(), eigene.strasse(), Belegschreibweisen.ortszeile(eigene)))) {
      links(ABSENDERZEILE, Schrift.NORMAL, GROESSE_ABSENDERZEILE, GEDAEMPFT, zeile);
    }
    querlinie(RAND_LINKS, RAND_RECHTS, ANSCHRIFTLINIE, STRICH, LINIE);

    final Anschrift firma = daten.empfaenger().anschrift();
    final List<String> oben = new ArrayList<>();
    oben.add(daten.empfaenger().firma());
    oben.addAll(Belegschreibweisen.gefuellt(firma.strasse()));
    final int nachOben = stapelLinks(EMPFAENGER_OBEN, oben);
    stapelLinks(
        nachOben - ABSATZ,
        Belegschreibweisen.gefuellt(Belegschreibweisen.ortszeile(firma), firma.land()));

    rechts(
        ORT_DATUM,
        GROESSE_KLEIN,
        GEDAEMPFT,
        Belegschreibweisen.ortUndDatum(eigene.ort(), daten.rechnungDatum()));
  }

  /** Titel, Anrede und der Satz, der die Tabelle ankuendigt. */
  private void einleitung() {
    links(TITEL, Schrift.FETT, GROESSE_TEXT, DUNKEL, "Rechnung " + daten.nummer());
    links(ANREDE_OBEN, Schrift.NORMAL, GROESSE_TEXT, DUNKEL, Belegschreibweisen.ANREDE);
    links(
        EINLEITUNG_OBEN,
        Schrift.NORMAL,
        GROESSE_TEXT,
        DUNKEL,
        Belegschreibweisen.einleitungssatz(daten.leistungszeitraum()));
  }

  /** Zahlungsziel, Gruss und Name; eine Unterschrift steht nicht darunter. */
  private void schluss(final int tabelleUnten) {
    int hoehe = tabelle.platzFuer(tabelleUnten - ABSTAND_NACH_TABELLE, SCHLUSS_HOEHE);
    links(
        hoehe,
        Schrift.NORMAL,
        GROESSE_TEXT,
        DUNKEL,
        Belegschreibweisen.zahlungssatz(daten.zahlungszielTage()));
    hoehe -= ABSTAND_GRUSS;
    links(hoehe, Schrift.NORMAL, GROESSE_TEXT, DUNKEL, Belegschreibweisen.GRUSS);
    hoehe -= ABSTAND_NAME;
    links(hoehe, Schrift.NORMAL, GROESSE_TEXT, DUNKEL, daten.absender().name());
  }

  /** Der Fuss steht auf jeder Seite; er entsteht, wenn feststeht, wie viele es sind. */
  private void fuss() {
    final Belegabsender absender = daten.absender();
    final List<String> zeilen =
        Belegschreibweisen.gefuellt(
            Belegschreibweisen.verbunden(" · ", absender.name(), absender.webadresse()),
            Belegschreibweisen.steuerzeile(absender),
            Belegschreibweisen.mit("Bankverbindung: ", absender.bankverbindung()));
    for (int blatt = 1; blatt <= tabelle.seite(); blatt++) {
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

  // --- Setzhilfen ---

  private void links(
      final int hoehe,
      final Schrift schrift,
      final int groesse,
      final Farbe farbe,
      final String text) {
    elemente.add(
        new Druckelement.Text(
            tabelle.seite(), RAND_LINKS, hoehe, schrift, groesse, farbe, Ausrichtung.LINKS, text));
  }

  private void rechts(final int hoehe, final int groesse, final Farbe farbe, final String text) {
    elemente.add(
        new Druckelement.Text(
            tabelle.seite(),
            RAND_RECHTS,
            hoehe,
            Schrift.NORMAL,
            groesse,
            farbe,
            Ausrichtung.RECHTS,
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
    elemente.add(new Druckelement.Linie(tabelle.seite(), von, hoehe, bis, hoehe, staerke, farbe));
  }

  // --- Zeilen aus freiwilligen Angaben ---

  private static List<String> anschriftzeilen(final Anschrift anschrift) {
    return Belegschreibweisen.gefuellt(
        anschrift.strasse(), Belegschreibweisen.ortszeile(anschrift), anschrift.land());
  }
}
