package org.mwolff.fbcrm.eigeneangaben.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.common.infrastructure.AnschriftSpalten;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngaben;

/**
 * Die eine Zeile der Tabelle {@code eigene_angaben} aus {@code V5__eigene_angaben.sql}.
 *
 * <p>Die Kennung wird nicht erzeugt, sondern gesetzt: Es gibt genau die Zeile {@link #ZEILE}, und
 * die Datenbank laesst ueber ihren CHECK keine zweite zu. Dass die Kennung von Anfang an steht,
 * macht aus jedem {@code save} ein UPDATE statt eines INSERT — genau das ist hier gewollt.
 */
@Entity
@Table(name = "eigene_angaben")
class EigeneAngabenEntity {

  /** Die Kennung der einen Zeile. */
  static final Short ZEILE = (short) 1;

  @Id
  @Column(name = "id", nullable = false)
  private Short id;

  @Column(name = "name", length = 200)
  private @Nullable String name;

  @Column(name = "berufsbezeichnung", length = 200)
  private @Nullable String berufsbezeichnung;

  /*
   * Dieselben vier Spalten wie an der Firma, in derselben Laenge; ein @AttributeOverride braucht
   * es darum nicht. Hibernate laesst das eingebettete Objekt weg, solange jede seiner Spalten NULL
   * ist — der Stand der Zeile direkt nach der Migration.
   */
  @Embedded private @Nullable AnschriftSpalten anschrift;

  @Column(name = "email", length = 320)
  private @Nullable String email;

  @Column(name = "telefon", length = 50)
  private @Nullable String telefon;

  @Column(name = "webadresse", length = 200)
  private @Nullable String webadresse;

  @Column(name = "steuernummer", length = 50)
  private @Nullable String steuernummer;

  @Column(name = "umsatzsteuer_id", length = 50)
  private @Nullable String umsatzsteuerId;

  @Column(name = "bankverbindung", length = 200)
  private @Nullable String bankverbindung;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /*
   * JPA verlangt einen parameterlosen Konstruktor und fuellt die Felder danach selbst; NullAway
   * sieht diesen Weg nicht und meldete sonst nicht initialisierte Felder.
   */
  @SuppressWarnings("NullAway.Init")
  protected EigeneAngabenEntity() {
    // Von Hibernate benutzt.
  }

  /**
   * Die eine Zeile zu den eigenen Angaben — jedes Feld kommt aus dem Fachobjekt (Plan #238, A5).
   *
   * <p>Die Kennung steht nicht in der Liste; sie ist fest ({@link #ZEILE}). Der Zeitpunkt der
   * Aenderung steht nicht an den Angaben selbst und kommt darum als zweiter Parameter dazu — wie
   * der Fremdschluessel an den Positionszeilen aus Issue #241.
   *
   * @param angaben die eigenen Angaben, deren Stand die Zeile tragen soll
   * @param geaendertAm Zeitpunkt der Aenderung
   * @return die eine Zeile
   */
  static EigeneAngabenEntity aus(final EigeneAngaben angaben, final Instant geaendertAm) {
    return new EigeneAngabenEntity(angaben, geaendertAm);
  }

  private EigeneAngabenEntity(final EigeneAngaben angaben, final Instant geaendertAm) {
    this.id = ZEILE;
    this.name = angaben.name();
    this.berufsbezeichnung = angaben.berufsbezeichnung();
    this.anschrift = AnschriftSpalten.aus(angaben.anschrift());
    this.email = angaben.email();
    this.telefon = angaben.telefon();
    this.webadresse = angaben.webadresse();
    this.steuernummer = angaben.steuernummer();
    this.umsatzsteuerId = angaben.umsatzsteuerId();
    this.bankverbindung = angaben.bankverbindung();
    this.updatedAt = geaendertAm;
  }

  Short getId() {
    return id;
  }

  @Nullable String getName() {
    return name;
  }

  @Nullable String getBerufsbezeichnung() {
    return berufsbezeichnung;
  }

  Anschrift getAnschrift() {
    return AnschriftSpalten.anschriftAus(anschrift);
  }

  @Nullable String getEmail() {
    return email;
  }

  @Nullable String getTelefon() {
    return telefon;
  }

  @Nullable String getWebadresse() {
    return webadresse;
  }

  @Nullable String getSteuernummer() {
    return steuernummer;
  }

  @Nullable String getUmsatzsteuerId() {
    return umsatzsteuerId;
  }

  @Nullable String getBankverbindung() {
    return bankverbindung;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }
}
