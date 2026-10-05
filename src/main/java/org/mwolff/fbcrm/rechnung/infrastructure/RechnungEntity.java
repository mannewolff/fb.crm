package org.mwolff.fbcrm.rechnung.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.common.Anschrift;
import org.mwolff.fbcrm.rechnung.domain.Belegabsender;
import org.mwolff.fbcrm.rechnung.domain.Belegempfaenger;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Die Zeile der Tabelle {@code rechnung} nach {@code V18__rechnung.sql}.
 *
 * <p>Das Angebot steht als blosse Kennung und nicht als Beziehung, aus demselben Grund wie bei
 * {@code AngebotEntity}: Angebot und Rechnung sind eigene Wurzeln mit eigenem Lebenszyklus, und
 * eine Beziehung laedt das eine mit dem anderen, ohne dass es gebraucht wuerde. Die Positionen
 * liegen aus demselben Grund in {@link RechnungPositionEntity}; ihre Reihenfolge fuehrt {@code
 * JpaRechnungRepository}.
 *
 * <p>{@code zustand} geht als Text in die Datenbank ({@link EnumType#STRING}) und nicht als
 * Ordnungszahl: Der CHECK der Migration nennt die Werte im Klartext, und ein neuer Zustand darf die
 * Bedeutung der bestehenden Zeilen nicht verschieben.
 *
 * <p><b>Die beiden Kopien stehen in Einzelspalten, wandern aber als Ganzes.</b> Die Fabrik {@link
 * #aus} und die Getter nehmen und liefern {@link Belegempfaenger} und {@link Belegabsender}; die
 * Zerlegung auf {@code empfaenger_*} und {@code absender_*} findet nur hier statt. Das ist
 * dasselbe, was ein {@code @Embeddable} taete — von Hand, weil zwei Anschriften mit verschiedenen
 * Spaltenpraefixen sonst eine Kette von {@code @AttributeOverride} braeuchten. Fehlt die
 * Pflichtangabe der Kopie (Firmenname beim Empfaenger, Name beim Absender), gibt es die Kopie nicht
 * — genau das ist der Entwurf, und genau das haelt auch der CHECK {@code rechnung_entwurf} fest.
 *
 * <p>Einen Ansprechpartner fuehrt die Kopie des Empfaengers nicht (siehe Belegempfaenger und die
 * Spaltenliste in V18); beim Lesen steht dort {@code null}.
 */
/*
 * Neunundzwanzig Spalten ergeben neunundzwanzig Felder. Sechzehn davon tragen die beiden Kopien
 * von Empfaenger und Absender (R8) — sie stehen einzeln, weil eine Kopie aus Spalten besteht und
 * nicht aus einem Textblock. Die Zeile ist die Zeile der Tabelle; sie in Teilzeilen zu zerlegen,
 * nur um die Zahl zu druecken, braeuchte eine Kette von @AttributeOverride und klaerte nichts.
 */
@SuppressWarnings("PMD.TooManyFields")
@Entity
@Table(name = "rechnung")
class RechnungEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "angebot_id", nullable = false)
  private long angebotId;

  @Enumerated(EnumType.STRING)
  @Column(name = "zustand", nullable = false, length = 20)
  private Rechnungszustand zustand;

  @Column(name = "rechnung_datum", nullable = false)
  private LocalDate rechnungDatum;

  @Column(name = "leistungszeitraum", length = 100)
  private @Nullable String leistungszeitraum;

  @Column(name = "nummer", length = 50)
  private @Nullable String nummer;

  @Column(name = "steuersatz", precision = 5, scale = 2)
  private @Nullable BigDecimal steuersatz;

  @Column(name = "zahlungsziel_tage")
  private @Nullable Integer zahlungszielTage;

  @Column(name = "gestellt_am")
  private @Nullable Instant gestelltAm;

  @Column(name = "pdf_schluessel", length = 300)
  private @Nullable String pdfSchluessel;

  @Column(name = "empfaenger_firma", length = 200)
  private @Nullable String empfaengerFirma;

  @Column(name = "empfaenger_strasse", length = 200)
  private @Nullable String empfaengerStrasse;

  @Column(name = "empfaenger_plz", length = 20)
  private @Nullable String empfaengerPlz;

  @Column(name = "empfaenger_ort", length = 200)
  private @Nullable String empfaengerOrt;

  @Column(name = "empfaenger_land", length = 100)
  private @Nullable String empfaengerLand;

  @Column(name = "absender_name", length = 200)
  private @Nullable String absenderName;

  @Column(name = "absender_berufsbezeichnung", length = 200)
  private @Nullable String absenderBerufsbezeichnung;

  @Column(name = "absender_strasse", length = 200)
  private @Nullable String absenderStrasse;

  @Column(name = "absender_plz", length = 20)
  private @Nullable String absenderPlz;

  @Column(name = "absender_ort", length = 200)
  private @Nullable String absenderOrt;

  @Column(name = "absender_land", length = 100)
  private @Nullable String absenderLand;

  @Column(name = "absender_email", length = 320)
  private @Nullable String absenderEmail;

  @Column(name = "absender_telefon", length = 50)
  private @Nullable String absenderTelefon;

  @Column(name = "absender_steuernummer", length = 50)
  private @Nullable String absenderSteuernummer;

  @Column(name = "absender_umsatzsteuer_id", length = 50)
  private @Nullable String absenderUmsatzsteuerId;

  @Column(name = "absender_bankverbindung", length = 200)
  private @Nullable String absenderBankverbindung;

  @Column(name = "absender_webadresse", length = 200)
  private @Nullable String absenderWebadresse;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /*
   * JPA verlangt einen parameterlosen Konstruktor und fuellt die Felder danach selbst; NullAway
   * sieht diesen Weg nicht und meldete sonst nicht initialisierte Felder.
   */
  @SuppressWarnings("NullAway.Init")
  protected RechnungEntity() {
    // Von Hibernate benutzt.
  }

  /**
   * Die Zeile zu einer Rechnung — jedes Feld kommt aus dem Fachobjekt (Plan #238, A5).
   *
   * <p>Die Zeile holt sich ihren Stand selbst, statt ihn als Liste von vierzehn Parametern zu
   * bekommen: Der Adapter nennt dann am Aufruf nur noch, <i>was</i> abgebildet wird, und ein neues
   * Feld der Rechnung landet hier und nicht zusaetzlich in jeder Aufrufstelle.
   *
   * @param rechnung die Rechnung, deren Stand die Zeile tragen soll
   * @return die Zeile; ihre Kennung ist die der Rechnung und damit {@code null}, solange die
   *     Rechnung noch nicht geschrieben wurde
   */
  static RechnungEntity aus(final Rechnung rechnung) {
    return new RechnungEntity(rechnung);
  }

  private RechnungEntity(final Rechnung rechnung) {
    this.id = rechnung.id();
    this.angebotId = rechnung.angebotId();
    this.zustand = rechnung.zustand();
    this.rechnungDatum = rechnung.rechnungDatum();
    this.leistungszeitraum = rechnung.leistungszeitraum();
    this.nummer = rechnung.nummer();
    this.steuersatz = rechnung.steuersatz();
    this.zahlungszielTage = rechnung.zahlungszielTage();
    this.gestelltAm = rechnung.gestelltAm();
    this.pdfSchluessel = rechnung.pdfSchluessel();
    uebernehmeEmpfaenger(rechnung.empfaenger());
    uebernehmeAbsender(rechnung.absender());
    this.createdAt = rechnung.createdAt();
    this.updatedAt = rechnung.updatedAt();
  }

  private void uebernehmeEmpfaenger(final @Nullable Belegempfaenger empfaenger) {
    if (empfaenger == null) {
      return;
    }
    final Anschrift anschrift = empfaenger.anschrift();
    this.empfaengerFirma = empfaenger.firma();
    this.empfaengerStrasse = anschrift.strasse();
    this.empfaengerPlz = anschrift.plz();
    this.empfaengerOrt = anschrift.ort();
    this.empfaengerLand = anschrift.land();
  }

  private void uebernehmeAbsender(final @Nullable Belegabsender absender) {
    if (absender == null) {
      return;
    }
    final Anschrift anschrift = absender.anschrift();
    this.absenderName = absender.name();
    this.absenderBerufsbezeichnung = absender.berufsbezeichnung();
    this.absenderStrasse = anschrift.strasse();
    this.absenderPlz = anschrift.plz();
    this.absenderOrt = anschrift.ort();
    this.absenderLand = anschrift.land();
    this.absenderEmail = absender.email();
    this.absenderTelefon = absender.telefon();
    this.absenderSteuernummer = absender.steuernummer();
    this.absenderUmsatzsteuerId = absender.umsatzsteuerId();
    this.absenderBankverbindung = absender.bankverbindung();
    this.absenderWebadresse = absender.webadresse();
  }

  @Nullable Long getId() {
    return id;
  }

  long getAngebotId() {
    return angebotId;
  }

  Rechnungszustand getZustand() {
    return zustand;
  }

  LocalDate getRechnungDatum() {
    return rechnungDatum;
  }

  @Nullable String getLeistungszeitraum() {
    return leistungszeitraum;
  }

  @Nullable String getNummer() {
    return nummer;
  }

  @Nullable BigDecimal getSteuersatz() {
    return steuersatz;
  }

  @Nullable Integer getZahlungszielTage() {
    return zahlungszielTage;
  }

  @Nullable Instant getGestelltAm() {
    return gestelltAm;
  }

  @Nullable String getPdfSchluessel() {
    return pdfSchluessel;
  }

  /** Die Kopie des Empfaengers, oder {@code null} — dann ist die Rechnung noch Entwurf. */
  @Nullable Belegempfaenger getEmpfaenger() {
    if (empfaengerFirma == null) {
      return null;
    }
    return new Belegempfaenger(
        empfaengerFirma,
        new Anschrift(empfaengerStrasse, empfaengerPlz, empfaengerOrt, empfaengerLand),
        null);
  }

  /** Die Kopie der eigenen Angaben, oder {@code null} — dann ist die Rechnung noch Entwurf. */
  @Nullable Belegabsender getAbsender() {
    if (absenderName == null) {
      return null;
    }
    return new Belegabsender(
        absenderName,
        absenderBerufsbezeichnung,
        new Anschrift(absenderStrasse, absenderPlz, absenderOrt, absenderLand),
        absenderEmail,
        absenderTelefon,
        absenderSteuernummer,
        absenderUmsatzsteuerId,
        absenderBankverbindung,
        absenderWebadresse);
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }
}
