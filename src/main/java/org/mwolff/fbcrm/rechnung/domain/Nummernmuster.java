package org.mwolff.fbcrm.rechnung.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Die Schreibweise der Rechnungsnummer als Wertobjekt (Plan #161, E4 und E5).
 *
 * <p>Ein Muster besteht aus festem Text und Platzhaltern in geschweiften Klammern:
 *
 * <ul>
 *   <li>{@code {N…}} ist die laufende Nummer. Die Anzahl der {@code N} ist die
 *       <em>Mindestbreite</em> — kuerzere Nummern werden links mit Nullen aufgefuellt, laengere
 *       gehen vollstaendig hinaus. {@code {N}} heisst also „ohne Auffuellen".
 *   <li>{@code {JJJJ}} oder {@code {JJ}} ist das Jahr, vierstellig oder zweistellig.
 * </ul>
 *
 * <p>Gueltig ist ein Muster mit genau einem Nummern-Platzhalter, hoechstens einem
 * Jahres-Platzhalter, hoechstens {@value #MAX_LAENGE} Zeichen und festem Text aus Buchstaben,
 * Ziffern und {@code -}, {@code _}, {@code /}, {@code .}. Die enge Zeichenliste haelt die Nummer
 * druck- und dateinamentauglich.
 *
 * <p>Dieselbe Regel steht in {@code frontend/src/lib/nummernmuster.ts} — dort speist sie die
 * Vorschau, waehrend der Benutzer tippt. Beide Fassungen fahren dieselbe Beispieltabelle ({@code
 * NummernmusterTest}, {@code nummernmuster.test.ts}); diese hier entscheidet beim Speichern.
 *
 * @param text das Muster, wie es der Benutzer geschrieben hat
 */
public record Nummernmuster(String text) {

  /** Die Spaltenbreite von {@code rechnung_einstellungen.nummer_muster}. */
  public static final int MAX_LAENGE = 50;

  /** Das Zaehlerjahr eines Musters ohne Jahres-Platzhalter: ein einziger, durchlaufender Kreis. */
  public static final int OHNE_JAHR = 0;

  private static final Pattern PLATZHALTER = Pattern.compile("\\{([^{}]*)\\}");
  private static final Pattern NUMMER = Pattern.compile("N+");
  private static final Pattern JAHR = Pattern.compile("JJ|JJJJ");
  private static final Pattern FESTER_TEXT = Pattern.compile("[A-Za-z0-9\\-_/.]*");

  private static final int KURZES_JAHR = 2;
  private static final int LANGES_JAHR = 4;
  private static final int JAHRHUNDERT = 100;

  public Nummernmuster {
    if (!istGueltig(text)) {
      throw new IllegalArgumentException("Kein gueltiges Muster einer Rechnungsnummer: " + text);
    }
  }

  /**
   * Ob die Schreibweise stimmt.
   *
   * <p>Die Pruefung antwortet und wirft nicht: Die Schicht darueber entscheidet, ob daraus eine
   * Meldung am Feld wird oder etwas anderes.
   *
   * @param text das zu pruefende Muster
   */
  public static boolean istGueltig(final String text) {
    if (text.length() > MAX_LAENGE) {
      return false;
    }
    int nummern = 0;
    int jahre = 0;
    final Matcher treffer = PLATZHALTER.matcher(text);
    while (treffer.find()) {
      final String inhalt = treffer.group(1);
      if (NUMMER.matcher(inhalt).matches()) {
        nummern++;
      } else if (JAHR.matcher(inhalt).matches()) {
        jahre++;
      } else {
        return false;
      }
    }
    // Der feste Text wird geprueft, nachdem die Platzhalter herausgefallen sind. Dass dabei
    // getrennte Stuecke aneinanderstossen, aendert nichts: Geprueft wird Zeichen fuer Zeichen.
    final String ohnePlatzhalter = PLATZHALTER.matcher(text).replaceAll("");
    return nummern == 1 && jahre <= 1 && FESTER_TEXT.matcher(ohnePlatzhalter).matches();
  }

  /**
   * Das Zaehlerjahr dieses Musters — der Schluessel, unter dem sein Nummernkreis zaehlt (#160,
   * Kriterium 16).
   *
   * <p>Traegt das Muster einen Jahres-Platzhalter, zaehlt jedes Jahr fuer sich und beginnt wieder
   * bei 1; ohne ihn gibt es einen einzigen, durchlaufenden Kreis, und der steht unter der {@value
   * #OHNE_JAHR}. Ein Jahr 0 gibt es sonst nicht, also ist der Schluessel eindeutig.
   *
   * <p>Die Entscheidung faellt hier und nur hier: Wer sie anderswo noch einmal traefe — beim Lesen,
   * beim Pflegen, beim Stellen —, koennte sie beim naechsten Mal anders treffen, und die Nummer
   * landete in einem anderen Kreis, als sie gezogen wurde.
   *
   * @param jahr das Kalenderjahr in {@code common.Geschaeftszone}
   */
  public int zaehlerjahr(final int jahr) {
    return PLATZHALTER
            .matcher(text)
            .results()
            .anyMatch(treffer -> JAHR.matcher(treffer.group(1)).matches())
        ? jahr
        : OHNE_JAHR;
  }

  /**
   * Die Rechnungsnummer aus laufender Nummer und Jahr.
   *
   * @param laufendeNummer die laufende Nummer, ab 1
   * @param jahr das Jahr, vierstellig
   */
  public String rechnungsnummer(final int laufendeNummer, final int jahr) {
    final StringBuilder gebaut = new StringBuilder(MAX_LAENGE);
    final Matcher treffer = PLATZHALTER.matcher(text);
    int ende = 0;
    while (treffer.find()) {
      gebaut
          .append(text, ende, treffer.start())
          .append(ersatz(treffer.group(1), laufendeNummer, jahr));
      ende = treffer.end();
    }
    return gebaut.append(text, ende, text.length()).toString();
  }

  private static String ersatz(final String inhalt, final int laufendeNummer, final int jahr) {
    if (NUMMER.matcher(inhalt).matches()) {
      return gefuellt(laufendeNummer, inhalt.length());
    }
    if (inhalt.length() == KURZES_JAHR) {
      return gefuellt(jahr % JAHRHUNDERT, KURZES_JAHR);
    }
    return gefuellt(jahr, LANGES_JAHR);
  }

  /*
   * Ohne Verzweigung, und das ist Absicht: Als Bedingung war die Mindestbreite nicht pruefbar —
   * bei genau passender Laenge liefern „auffuellen" und „nicht auffuellen" dieselbe Zeichenkette
   * (derselbe Grund wie in Dateiname, Issue #64).
   */
  private static String gefuellt(final int wert, final int breite) {
    final String ziffern = Integer.toString(wert);
    return "0".repeat(Math.max(0, breite - ziffern.length())) + ziffern;
  }
}
