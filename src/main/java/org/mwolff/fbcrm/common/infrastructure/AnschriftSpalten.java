package org.mwolff.fbcrm.common.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;

/**
 * Die vier Anschriftenspalten einer Tabelle — eingebettet und nicht je Tabelle wiederholt (Plan
 * #238, A6).
 *
 * <p>{@code firma} und {@code eigene_angaben} fuehren dieselben vier Spalten in derselben Laenge;
 * zweimal abgeschrieben waren sie das einzige Duplikat, das SonarCloud am Projekt fand. Weichen die
 * Spalten einer weiteren Tabelle in Laenge oder Nullbarkeit ab, bleibt die Abbildung hier und die
 * Entity nennt den Unterschied mit {@code @AttributeOverride}; ein Angleichen des Schemas waere
 * eine Migration und keine Umbenennung.
 *
 * <p>Die Annotationen stehen hier und nicht am Record {@link Anschrift}: Die Domaene bleibt frei
 * von JPA (CLAUDE-java.md §6.1).
 */
@Embeddable
public class AnschriftSpalten {

  /** Die Anschrift ohne jede Angabe — der Stand einer Firma, die nur ihren Namen hat (E9). */
  private static final Anschrift LEER = new Anschrift(null, null, null, null);

  @Column(name = "strasse", length = 200)
  private @Nullable String strasse;

  @Column(name = "plz", length = 20)
  private @Nullable String plz;

  @Column(name = "ort", length = 200)
  private @Nullable String ort;

  @Column(name = "land", length = 100)
  private @Nullable String land;

  /*
   * JPA verlangt einen parameterlosen Konstruktor und fuellt die Felder danach selbst.
   */
  protected AnschriftSpalten() {
    // Von Hibernate benutzt.
  }

  /**
   * Die Spalten zu einer Anschrift.
   *
   * @param anschrift die Anschrift, deren Angaben die Spalten tragen sollen
   * @return die Spalten; fehlende Angaben stehen als {@code null} und nicht als Leerstring (E9)
   */
  public static AnschriftSpalten aus(final Anschrift anschrift) {
    return new AnschriftSpalten(anschrift);
  }

  private AnschriftSpalten(final Anschrift anschrift) {
    this.strasse = anschrift.strasse();
    this.plz = anschrift.plz();
    this.ort = anschrift.ort();
    this.land = anschrift.land();
  }

  /**
   * Die Anschrift zu den Spalten einer gelesenen Zeile.
   *
   * <p>Hibernate laesst das eingebettete Objekt weg, wenn jede seiner Spalten {@code null} ist —
   * genau der Fall der Firma, die mit nichts als ihrem Namen angelegt wurde. Darum nimmt dieser Weg
   * die fehlenden Spalten entgegen und nicht erst die Zeile, die sie fuehrt.
   *
   * @param spalten die Spalten der Zeile, oder {@code null}, wenn keine von ihnen gefuellt war
   * @return die Anschrift; ohne jede Spalte die Anschrift ohne jede Angabe
   */
  public static Anschrift anschriftAus(final @Nullable AnschriftSpalten spalten) {
    return spalten == null ? LEER : spalten.alsAnschrift();
  }

  /**
   * Die Anschrift zu diesen Spalten.
   *
   * @return die Anschrift mit den vier Angaben dieser Spalten
   */
  public Anschrift alsAnschrift() {
    return new Anschrift(strasse, plz, ort, land);
  }
}
