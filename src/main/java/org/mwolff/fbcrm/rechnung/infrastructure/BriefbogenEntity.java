package org.mwolff.fbcrm.rechnung.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.rechnung.domain.Briefbogen;
import org.mwolff.fbcrm.rechnung.domain.Briefpapierblatt;
import org.mwolff.fbcrm.rechnung.domain.Randmasse;

/**
 * Die eine Zeile der Tabelle {@code briefbogen} aus {@code V26__briefbogen.sql} und {@code
 * V33__version.sql}.
 *
 * <p>Die Kennung wird nicht erzeugt, sondern gesetzt: Es gibt genau die Zeile {@link #ZEILE}, und
 * die Datenbank laesst ueber ihren CHECK keine zweite zu. Dass die Kennung von Anfang an steht,
 * macht aus jedem {@code save} ein UPDATE statt eines INSERT — genau das ist hier gewollt.
 *
 * <p>Die vier Spalten des Blattes sind gemeinsam NULL oder gemeinsam belegt; das haelt ein CHECK
 * der Tabelle fest. Am Schluessel allein entscheidet sich darum, ob ein Briefpapier hinterlegt ist.
 */
/*
 * Sechzehn Spalten ergeben sechzehn Felder: Blatt, Schalter, sechs Raender, Zeitpunkt, Stand und
 * Urheber. Die Zeile ist die Zeile der Tabelle; sie in Teilzeilen zu zerlegen, nur um die Zahl zu
 * druecken, braeuchte eine Kette von @AttributeOverride und klaerte nichts — wie bei RechnungEntity.
 */
@SuppressWarnings("PMD.TooManyFields")
@Entity
@Table(name = "briefbogen")
class BriefbogenEntity {

  /** Die Kennung der einen Zeile. */
  static final Short ZEILE = (short) 1;

  @Id
  @Column(name = "id", nullable = false)
  private Short id;

  @Column(name = "pdf_schluessel", length = 255)
  private @Nullable String pdfSchluessel;

  @Column(name = "dateiname", length = 255)
  private @Nullable String dateiname;

  @Column(name = "groesse")
  private @Nullable Long groesse;

  @Column(name = "seiten")
  private @Nullable Short seiten;

  @Column(name = "absenderzeile", nullable = false)
  private boolean absenderzeile;

  @Column(name = "steuer_bank_zeile", nullable = false)
  private boolean steuerBankZeile;

  @Column(name = "rand_links", nullable = false)
  private int randLinks;

  @Column(name = "rand_rechts", nullable = false)
  private int randRechts;

  @Column(name = "inhalt_oben", nullable = false)
  private int inhaltOben;

  @Column(name = "rand_unten", nullable = false)
  private int randUnten;

  @Column(name = "folge_oben", nullable = false)
  private int folgeOben;

  @Column(name = "folge_unten", nullable = false)
  private int folgeUnten;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /*
   * Der Stand der ganzen Zeile, gefuehrt von Hibernate (Plan #415, E1, E11): Schalter, Raender und
   * Briefpapier zaehlen ihn gemeinsam weiter. Ein UPDATE mit einem anderen Stand als dem
   * vorgelegten trifft keine Zeile und scheitert.
   */
  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "geaendert_von")
  private @Nullable Long geaendertVon;

  /*
   * JPA verlangt einen parameterlosen Konstruktor und fuellt die Felder danach selbst; NullAway
   * sieht diesen Weg nicht und meldete sonst nicht initialisierte Felder.
   */
  @SuppressWarnings("NullAway.Init")
  protected BriefbogenEntity() {
    // Von Hibernate benutzt.
  }

  /**
   * Die eine Zeile zum Briefbogen — jedes Feld kommt aus dem Fachobjekt.
   *
   * <p>Die Kennung steht nicht in der Liste; sie ist fest ({@link #ZEILE}). Zeitpunkt, Stand und
   * Urheber der Aenderung stehen nicht am Briefbogen selbst und kommen darum als Parameter dazu.
   *
   * @param briefbogen der Briefbogen, dessen Stand die Zeile tragen soll
   * @param geaendertAm Zeitpunkt der Aenderung
   * @param version der Stand, auf dem die Aenderung beruht
   * @param geaendertVon Kennung des handelnden Kontos
   * @return die eine Zeile
   */
  static BriefbogenEntity aus(
      final Briefbogen briefbogen,
      final Instant geaendertAm,
      final long version,
      final long geaendertVon) {
    return new BriefbogenEntity(briefbogen, geaendertAm, version, geaendertVon);
  }

  private BriefbogenEntity(
      final Briefbogen briefbogen,
      final Instant geaendertAm,
      final long version,
      final long geaendertVon) {
    this.id = ZEILE;
    this.version = version;
    this.geaendertVon = geaendertVon;
    final Briefpapierblatt blatt = briefbogen.blatt();
    if (blatt != null) {
      this.pdfSchluessel = blatt.schluessel();
      this.dateiname = blatt.dateiname();
      this.groesse = blatt.groesse();
      this.seiten = (short) blatt.seiten();
    }
    this.absenderzeile = briefbogen.absenderzeile();
    this.steuerBankZeile = briefbogen.steuerBankZeile();
    final Randmasse raender = briefbogen.raender();
    this.randLinks = raender.links();
    this.randRechts = raender.rechts();
    this.inhaltOben = raender.inhaltOben();
    this.randUnten = raender.unten();
    this.folgeOben = raender.folgeOben();
    this.folgeUnten = raender.folgeUnten();
    this.updatedAt = geaendertAm;
  }

  /**
   * Der Briefbogen, den diese Zeile traegt.
   *
   * @return das Fachobjekt; ohne Schluessel ohne Blatt
   */
  Briefbogen alsBriefbogen() {
    return new Briefbogen(
        blatt(),
        absenderzeile,
        steuerBankZeile,
        new Randmasse(randLinks, randRechts, inhaltOben, randUnten, folgeOben, folgeUnten));
  }

  private @Nullable Briefpapierblatt blatt() {
    if (pdfSchluessel == null) {
      return null;
    }
    return new Briefpapierblatt(
        pdfSchluessel,
        Objects.requireNonNull(dateiname),
        Objects.requireNonNull(groesse),
        Objects.requireNonNull(seiten));
  }

  Short getId() {
    return id;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }

  long getVersion() {
    return version;
  }

  @Nullable Long getGeaendertVon() {
    return geaendertVon;
  }
}
