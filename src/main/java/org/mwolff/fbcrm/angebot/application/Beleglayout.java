package org.mwolff.fbcrm.angebot.application;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.Einheit;
import org.mwolff.fbcrm.rechnung.application.Druckzeile;
import org.mwolff.fbcrm.rechnung.application.Schrift;
import org.mwolff.fbcrm.rechnung.domain.Belegabsender;
import org.mwolff.fbcrm.rechnung.domain.Belegempfaenger;

/**
 * Setzt die Druckdaten eines Angebots in eine Folge von Druckzeilen (E10).
 *
 * <p><b>Reine Rechnung, keine Bibliothek.</b> Hier entstehen Umbruch, Spalten und Seitenwechsel —
 * die knifflige Haelfte des PDF. Sie steht getrennt von {@code PdfBoxDrucker}, damit sie ohne
 * Fremdcode geprueft werden kann: Was hier falsch waere, faende man am fertigen PDF erst mit dem
 * Auge.
 *
 * <p><b>Schlicht ist die Vorgabe.</b> Das eigene Erscheinungsbild ist ausdrueckliches Nicht-Ziel
 * der fachlichen Quelle; gesetzt wird in einer Groesse und zwei Schnitten, alles linksbuendig an
 * festen Spalten. Ein rechter Satz der Betraege brauchte Schriftmetrik und damit die Bibliothek,
 * die hier gerade nicht vorkommt.
 *
 * <p><b>Der Netto-Hinweis gehoert dazu.</b> Das Nicht-Ziel „Umsatzsteuer im Angebot" nimmt die
 * Steuerrechnung heraus, verlangt aber den Satz unter der Summe — ohne ihn liest sich ein
 * Nettobetrag als Bruttopreis.
 */
/*
 * PMD.TooManyMethods: Der Beleg besteht aus benannten Bloecken — Absenderkopf, Anschriftenfeld,
 * Belegkopf, Absatz, Positionstabelle, Summenblock, Absenderfuss — und dazu den reinen Hilfsfunktionen
 * fuer Umbruch und Schreibweise. Jede ist kurz und an ihrem Namen zu erkennen; sie zu drei langen
 * Methoden zusammenzuziehen erfuellte die Regel und machte den Satz unlesbar. Die Regel zaehlt
 * Methoden, ohne ihre Laenge zu wiegen.
 */
@SuppressWarnings("PMD.TooManyMethods")
public final class Beleglayout {

  /*
   * Der Satzspiegel einer A4-Seite (595 x 842 pt) in Punkten, Ursprung unten links. Ganzzahlig,
   * weil ein halber Punkt auf Papier nicht zu sehen ist und weil der Seitenumbruch dadurch eine
   * Rechnung wird, deren Ergebnis ein Test genau benennen kann.
   */
  private static final int LINKS = 57;
  private static final int OBEN = 790;
  private static final int UNTEN = 85;
  private static final int TEXTBREITE = 481;

  /* Die Spalten der Positionstabelle. Der Abstand zur naechsten Spalte ist der Umbruchspielraum. */
  private static final int X_MENGE = 300;
  private static final int X_EINHEIT = 345;
  private static final int X_EINZELPREIS = 400;
  private static final int X_BETRAG = 470;
  private static final int SPALTENABSTAND = 10;
  private static final int BEZEICHNUNG_BREITE = X_MENGE - LINKS - SPALTENABSTAND;

  /* Schriftgroessen und die Zeilenhoehen, die zu ihnen gehoeren. */
  private static final int KLEIN = 8;
  private static final int NORMALGROESSE = 10;
  private static final int TITELGROESSE = 16;
  private static final int KLEINE_ZEILE = 11;
  private static final int ZEILE = 14;
  private static final int TITELZEILE = 22;
  private static final int ABSATZABSTAND = 28;
  private static final int ANSCHRIFTENFELD = 42;

  private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  /** Jede Folge von Zwischenraum ist eine Wortgrenze — auch ein Zeilenumbruch im Text. */
  private static final Pattern WORTGRENZE = Pattern.compile("\\s+");

  /*
   * Die Beschriftung der Einheiten steht hier und nicht am Enum: Sie ist eine Eigenschaft dieses
   * Belegs und nicht der Einheit — „PERSONENTAG" waere fuer einen Kunden keine Beschriftung. Eine
   * Karte statt einer Verzweigung, damit eine vierte Einheit auffaellt, statt still in einen
   * falschen Zweig zu laufen.
   */
  private static final Map<Einheit, String> EINHEITEN =
      Map.of(
          Einheit.STUNDE,
          "Stunde",
          Einheit.PERSONENTAG,
          "Personentag",
          Einheit.PAUSCHAL,
          "Pauschal");

  private static final String NETTO_HINWEIS = "Alle Beträge netto, zzgl. gesetzlicher Umsatzsteuer";

  private Beleglayout() {}

  /**
   * Der gesetzte Beleg: alle Pflichtinhalte aus Kriterium 15 in Leserichtung.
   *
   * @param daten was auf dem Beleg steht
   * @return die Druckzeilen, Seite fuer Seite und von oben nach unten
   */
  public static List<Druckzeile> zeilen(final AngebotDruckdaten daten) {
    final Satz satz = new Satz();
    absenderkopf(satz, daten.absender());
    empfaengerfeld(satz, daten.empfaenger());
    belegkopf(satz, daten);
    absatz(satz, daten.leistungsbeschreibung(), TEXTBREITE);
    positionstabelle(satz, daten.positionen());
    summenblock(satz, daten.summe());
    absatzMitUeberschrift(satz, "Zahlungsbedingungen", daten.zahlungsbedingungen());
    absenderfuss(satz, daten.absender());
    return satz.fertig();
  }

  /** Die kleine Absenderzeile ueber dem Anschriftenfeld — wie auf einem Briefumschlagfenster. */
  private static void absenderkopf(final Satz satz, final Belegabsender absender) {
    final Anschrift anschrift = absender.anschrift();
    satz.zeile(
        verbunden(
            ", ",
            absender.name(),
            anschrift.strasse(),
            verbunden(" ", anschrift.plz(), anschrift.ort())),
        Schrift.NORMAL,
        KLEIN,
        KLEINE_ZEILE);
    satz.leerraum(ABSATZABSTAND);
  }

  /**
   * Das Anschriftenfeld des Empfaengers.
   *
   * <p>Jede Angabe darf fehlen — eine fehlende schreibt keine leere Zeile, sonst klaffte im
   * Anschriftenfeld eine Luecke. Der Ansprechpartner ist nicht noetig (Kriterium 12).
   */
  private static void empfaengerfeld(final Satz satz, final Belegempfaenger empfaenger) {
    final Anschrift anschrift = empfaenger.anschrift();
    satz.zeileWennGefuellt(empfaenger.firma(), NORMALGROESSE, ZEILE);
    satz.zeileWennGefuellt(empfaenger.ansprechpartner(), NORMALGROESSE, ZEILE);
    satz.zeileWennGefuellt(anschrift.strasse(), NORMALGROESSE, ZEILE);
    satz.zeileWennGefuellt(verbunden(" ", anschrift.plz(), anschrift.ort()), NORMALGROESSE, ZEILE);
    satz.zeileWennGefuellt(anschrift.land(), NORMALGROESSE, ZEILE);
    satz.leerraum(ANSCHRIFTENFELD);
  }

  /** Titel, Angebotsdatum und Gueltigkeit (Kriterium 15). */
  private static void belegkopf(final Satz satz, final AngebotDruckdaten daten) {
    satz.zeile("Angebot " + daten.nummer(), Schrift.FETT, TITELGROESSE, TITELZEILE);
    satz.zeile(
        "Angebotsdatum: " + DATUM.format(daten.angebotDatum()),
        Schrift.NORMAL,
        NORMALGROESSE,
        ZEILE);
    satz.zeile(
        "Gültig bis: " + DATUM.format(daten.gueltigBis()), Schrift.NORMAL, NORMALGROESSE, ZEILE);
    satz.leerraum(ABSATZABSTAND);
  }

  /** Ein umgebrochener Absatz; ein fehlender Text setzt nichts, auch keinen Abstand. */
  private static void absatz(final Satz satz, final @Nullable String text, final int breite) {
    if (text == null || text.isEmpty()) {
      return;
    }
    for (final String zeile : umbrochen(text, breite, NORMALGROESSE)) {
      satz.zeile(zeile, Schrift.NORMAL, NORMALGROESSE, ZEILE);
    }
    satz.leerraum(ABSATZABSTAND);
  }

  /** Ein Absatz mit fetter Ueberschrift; fehlt der Text, fehlt auch die Ueberschrift. */
  private static void absatzMitUeberschrift(
      final Satz satz, final String ueberschrift, final @Nullable String text) {
    if (text == null || text.isEmpty()) {
      return;
    }
    satz.zeile(ueberschrift, Schrift.FETT, NORMALGROESSE, ZEILE);
    absatz(satz, text, TEXTBREITE);
  }

  /**
   * Die Positionen mit Menge, Einheit, Einzelpreis und Betrag (Kriterium 15).
   *
   * <p>Reicht die Seite nicht, laeuft die Tabelle auf der naechsten weiter — mit ihrem Spaltenkopf,
   * weil eine fortgesetzte Tabelle ohne Kopf nicht zu lesen ist. Eine lange Bezeichnung wird in
   * ihrer Spalte umgebrochen; die Zahlen stehen auf der ersten ihrer Zeilen.
   */
  private static void positionstabelle(final Satz satz, final List<Angebotsposition> positionen) {
    tabellenkopf(satz);
    for (final Angebotsposition position : positionen) {
      final List<String> bezeichnung =
          umbrochen(position.bezeichnung(), BEZEICHNUNG_BREITE, NORMALGROESSE);
      satz.zelle(LINKS, bezeichnung.getFirst(), Schrift.NORMAL, NORMALGROESSE);
      zahlenspalten(satz, position);
      naechsteTabellenzeile(satz);
      for (final String weitere : bezeichnung.subList(1, bezeichnung.size())) {
        satz.zelle(LINKS, weitere, Schrift.NORMAL, NORMALGROESSE);
        naechsteTabellenzeile(satz);
      }
    }
    satz.leerraum(ZEILE);
  }

  private static void tabellenkopf(final Satz satz) {
    satz.zelle(LINKS, "Bezeichnung", Schrift.FETT, NORMALGROESSE);
    satz.zelle(X_MENGE, "Menge", Schrift.FETT, NORMALGROESSE);
    satz.zelle(X_EINHEIT, "Einheit", Schrift.FETT, NORMALGROESSE);
    satz.zelle(X_EINZELPREIS, "Einzelpreis", Schrift.FETT, NORMALGROESSE);
    satz.zelle(X_BETRAG, "Betrag", Schrift.FETT, NORMALGROESSE);
    satz.vorschub(ZEILE);
  }

  private static void zahlenspalten(final Satz satz, final Angebotsposition position) {
    satz.zelle(X_MENGE, menge(position.menge()), Schrift.NORMAL, NORMALGROESSE);
    satz.zelle(X_EINHEIT, einheit(position.einheit()), Schrift.NORMAL, NORMALGROESSE);
    satz.zelle(X_EINZELPREIS, geld(position.einzelpreis()), Schrift.NORMAL, NORMALGROESSE);
    satz.zelle(X_BETRAG, geld(position.betrag()), Schrift.NORMAL, NORMALGROESSE);
  }

  private static void naechsteTabellenzeile(final Satz satz) {
    if (satz.vorschub(ZEILE)) {
      tabellenkopf(satz);
    }
  }

  /** Die Angebotssumme und der Netto-Hinweis darunter (Kriterium 15, E10). */
  private static void summenblock(final Satz satz, final BigDecimal summe) {
    satz.zelle(LINKS, "Angebotssumme", Schrift.FETT, NORMALGROESSE);
    satz.zelle(X_BETRAG, geld(summe), Schrift.FETT, NORMALGROESSE);
    satz.vorschub(ZEILE);
    satz.zeile(NETTO_HINWEIS, Schrift.NORMAL, KLEIN, KLEINE_ZEILE);
    satz.leerraum(ABSATZABSTAND);
  }

  /** Kontakt, Steuernummern und Bankverbindung aus „Eigene Angaben" (Kriterium 15). */
  private static void absenderfuss(final Satz satz, final Belegabsender absender) {
    satz.zeileWennGefuellt(beschriftet("E-Mail", absender.email()), KLEIN, KLEINE_ZEILE);
    satz.zeileWennGefuellt(beschriftet("Telefon", absender.telefon()), KLEIN, KLEINE_ZEILE);
    satz.zeileWennGefuellt(
        beschriftet("Steuernummer", absender.steuernummer()), KLEIN, KLEINE_ZEILE);
    satz.zeileWennGefuellt(
        beschriftet("USt-IdNr.", absender.umsatzsteuerId()), KLEIN, KLEINE_ZEILE);
    satz.zeileWennGefuellt(
        beschriftet("Bankverbindung", absender.bankverbindung()), KLEIN, KLEINE_ZEILE);
  }

  /** {@code Name: Wert}, oder der Leerstring — eine Beschriftung ohne Wert sagt nichts. */
  private static String beschriftet(final String name, final @Nullable String wert) {
    if (wert == null) {
      return "";
    }
    return name + ": " + wert;
  }

  /** Verbindet die gefuellten Teile; fehlende und leere fallen samt ihrem Trenner heraus. */
  private static String verbunden(final String trenner, final @Nullable String... teile) {
    final StringBuilder text = new StringBuilder();
    for (final String teil : teile) {
      if (teil == null || teil.isEmpty()) {
        continue;
      }
      if (!text.isEmpty()) {
        text.append(trenner);
      }
      text.append(teil);
    }
    return text.toString();
  }

  /**
   * Bricht einen Text auf die Breite um.
   *
   * <p>Umgebrochen wird an Wortgrenzen; ein Wort, das allein nicht passt, wird hart getrennt —
   * sonst liefe es aus dem Satzspiegel. Zeilenumbrueche im Text zaehlen dabei als Wortgrenze: Der
   * Drucker kann kein Steuerzeichen setzen, und ein schlichter Beleg braucht keine Absatzstruktur.
   */
  private static List<String> umbrochen(final String text, final int breite, final int groesse) {
    final int maxZeichen = zeichenJeZeile(breite, groesse);
    final List<String> zeilen = new ArrayList<>();
    final StringBuilder aktuell = new StringBuilder();
    for (final String wort : WORTGRENZE.splitAsStream(text.strip()).toList()) {
      for (final String teil : getrennt(wort, maxZeichen)) {
        if (aktuell.isEmpty()) {
          aktuell.append(teil);
        } else if (aktuell.length() + 1 + teil.length() <= maxZeichen) {
          aktuell.append(' ').append(teil);
        } else {
          zeilen.add(aktuell.toString());
          aktuell.setLength(0);
          aktuell.append(teil);
        }
      }
    }
    zeilen.add(aktuell.toString());
    return zeilen;
  }

  /** Ein Wort in Stuecke, die einzeln in die Breite passen. */
  private static List<String> getrennt(final String wort, final int maxZeichen) {
    final List<String> teile = new ArrayList<>();
    String rest = wort;
    while (rest.length() > maxZeichen) {
      teile.add(rest.substring(0, maxZeichen));
      rest = rest.substring(maxZeichen);
    }
    teile.add(rest);
    return teile;
  }

  /**
   * Wie viele Zeichen in eine Breite passen.
   *
   * <p>Eine Naeherung: Helvetica ist keine Festbreitenschrift, ein Zeichen ist dort im Mittel etwa
   * halb so breit wie die Schrift hoch. Genau messen liesse sich das nur mit der Schriftmetrik der
   * Bibliothek — und die bleibt nach E10 aus dieser Haelfte heraus.
   */
  private static int zeichenJeZeile(final int breite, final int groesse) {
    return breite * 2 / groesse;
  }

  private static String einheit(final Einheit einheit) {
    return Objects.requireNonNull(EINHEITEN.get(einheit));
  }

  private static String geld(final BigDecimal betrag) {
    return gesetzt("#,##0.00", betrag) + " €";
  }

  private static String menge(final BigDecimal menge) {
    return gesetzt("#,##0.##", menge);
  }

  /** Deutsche Schreibweise, unabhaengig von der Sprache des Betriebssystems. */
  private static String gesetzt(final String muster, final BigDecimal wert) {
    return new DecimalFormat(muster, DecimalFormatSymbols.getInstance(Locale.GERMANY)).format(wert);
  }

  /**
   * Der laufende Satz: die gesammelten Zeilen, die Seite und die Grundlinie.
   *
   * <p>Der einzige veraenderliche Zustand dieses Layouts, und er lebt nur innerhalb eines Aufrufs
   * von {@link Beleglayout#zeilen}. Der Seitenumbruch steht hier an einer Stelle: Jeder Vorschub,
   * der unter den Satzspiegel reichte, beginnt stattdessen eine neue Seite.
   */
  private static final class Satz {

    private final List<Druckzeile> zeilen = new ArrayList<>();
    private int seite = 1;
    private int y = OBEN;

    /** Setzt einen Text an eine Spalte der laufenden Grundlinie, ohne vorzuschieben. */
    void zelle(final int x, final String text, final Schrift schrift, final int groesse) {
      zeilen.add(new Druckzeile(seite, x, y, schrift, groesse, text));
    }

    /** Setzt eine Zeile an den linken Rand und schiebt um ihre Hoehe vor. */
    void zeile(final String text, final Schrift schrift, final int groesse, final int hoehe) {
      zelle(LINKS, text, schrift, groesse);
      vorschub(hoehe);
    }

    /** Dasselbe, aber nur fuer einen Text, der etwas sagt. */
    void zeileWennGefuellt(final @Nullable String text, final int groesse, final int hoehe) {
      if (text == null || text.isEmpty()) {
        return;
      }
      zeile(text, Schrift.NORMAL, groesse, hoehe);
    }

    /**
     * Schiebt die Grundlinie um eine Hoehe vor.
     *
     * @return {@code true}, wenn dabei eine neue Seite begonnen wurde
     */
    boolean vorschub(final int hoehe) {
      if (y - hoehe < UNTEN) {
        seite++;
        y = OBEN;
        return true;
      }
      y -= hoehe;
      return false;
    }

    /** Abstand zwischen zwei Bloecken. */
    void leerraum(final int hoehe) {
      vorschub(hoehe);
    }

    List<Druckzeile> fertig() {
      return List.copyOf(zeilen);
    }
  }
}
