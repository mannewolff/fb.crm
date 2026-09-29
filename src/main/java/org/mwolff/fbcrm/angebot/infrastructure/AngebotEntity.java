package org.mwolff.fbcrm.angebot.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.angebot.domain.Belegabsender;
import org.mwolff.fbcrm.angebot.domain.Belegempfaenger;
import org.mwolff.fbcrm.common.Anschrift;

/**
 * Die Zeile der Tabelle {@code angebot} aus {@code V6__angebot.sql}.
 *
 * <p>Firma und Ansprechpartner stehen als blosse Kennungen und nicht als Beziehung: Angebot und
 * Firma sind eigene Wurzeln mit eigenem Lebenszyklus, und eine Beziehung laedt das eine mit dem
 * anderen, ohne dass es gebraucht wuerde (V10). Die Positionen liegen aus demselben Grund in {@link
 * AngebotPositionEntity} mit blosser Kennung; ihre Reihenfolge fuehrt {@code JpaAngebotRepository}.
 *
 * <p>Die beiden Anschriftskopien kommen als Ganzes herein ({@link #setzeEmpfaenger}, {@link
 * #setzeAbsender}) und gehen Spalte fuer Spalte heraus. Der Entwurf traegt sie gar nicht, und genau
 * das ist der Unterschied, den die gegenlaeufigen Checks der Migration halten: Ein Satz von
 * sechzehn Konstruktorparametern, die in jedem Entwurf {@code null} waeren, sagte dasselbe nur
 * unleserlicher.
 *
 * <p>{@code zustand} geht als Text in die Datenbank ({@link EnumType#STRING}) und nicht als
 * Ordnungszahl: Der Check der Migration nennt die Werte im Klartext, und ein sechster Zustand darf
 * die Bedeutung der bestehenden Zeilen nicht verschieben.
 */
/*
 * PMD.TooManyFields: Dreissig Spalten ergeben dreissig Felder. Sechzehn davon sind die
 * beiden Anschriftskopien, die nach R8 flach am Beleg stehen muessen — sie in eigene Tabellen zu
 * heben, machte aus einer Zeile drei, die immer zusammen gelesen werden. Die Zeile ist die Zeile der
 * Tabelle, nicht mehr.
 */
@SuppressWarnings("PMD.TooManyFields")
@Entity
@Table(name = "angebot")
class AngebotEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "firma_id", nullable = false)
  private long firmaId;

  @Column(name = "ansprechpartner_id")
  private @Nullable Long ansprechpartnerId;

  @Column(name = "nummer", length = 20)
  private @Nullable String nummer;

  @Enumerated(EnumType.STRING)
  @Column(name = "zustand", nullable = false, length = 20)
  private Angebotszustand zustand;

  @Column(name = "angebot_datum", nullable = false)
  private LocalDate angebotDatum;

  @Column(name = "gueltig_bis", nullable = false)
  private LocalDate gueltigBis;

  @Column(name = "leistungsbeschreibung", columnDefinition = "text")
  private @Nullable String leistungsbeschreibung;

  @Column(name = "zahlungsbedingungen", columnDefinition = "text")
  private @Nullable String zahlungsbedingungen;

  @Column(name = "versendet_am")
  private @Nullable Instant versendetAm;

  @Column(name = "reaktion_am")
  private @Nullable Instant reaktionAm;

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

  @Column(name = "empfaenger_ansprechpartner", length = 200)
  private @Nullable String empfaengerAnsprechpartner;

  @Column(name = "absender_name", length = 200)
  private @Nullable String absenderName;

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

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /*
   * JPA verlangt einen parameterlosen Konstruktor und fuellt die Felder danach selbst; NullAway
   * sieht diesen Weg nicht und meldete sonst nicht initialisierte Felder.
   */
  @SuppressWarnings("NullAway.Init")
  protected AngebotEntity() {
    // Von Hibernate benutzt.
  }

  /*
   * PMD.ExcessiveParameterList: Vierzehn eigene Spalten ergeben vierzehn Parameter — dieselbe Lage
   * wie bei EigeneAngabenEntity. Die beiden Anschriftskopien stehen bewusst nicht darin; sie kommen
   * als Ganzes ueber setzeEmpfaenger und setzeAbsender und fehlen im Entwurf vollstaendig.
   */
  @SuppressWarnings("PMD.ExcessiveParameterList")
  AngebotEntity(
      final @Nullable Long id,
      final long firmaId,
      final @Nullable Long ansprechpartnerId,
      final @Nullable String nummer,
      final Angebotszustand zustand,
      final LocalDate angebotDatum,
      final LocalDate gueltigBis,
      final @Nullable String leistungsbeschreibung,
      final @Nullable String zahlungsbedingungen,
      final @Nullable Instant versendetAm,
      final @Nullable Instant reaktionAm,
      final @Nullable String pdfSchluessel,
      final Instant createdAt,
      final Instant updatedAt) {
    this.id = id;
    this.firmaId = firmaId;
    this.ansprechpartnerId = ansprechpartnerId;
    this.nummer = nummer;
    this.zustand = zustand;
    this.angebotDatum = angebotDatum;
    this.gueltigBis = gueltigBis;
    this.leistungsbeschreibung = leistungsbeschreibung;
    this.zahlungsbedingungen = zahlungsbedingungen;
    this.versendetAm = versendetAm;
    this.reaktionAm = reaktionAm;
    this.pdfSchluessel = pdfSchluessel;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /** Uebernimmt die Kopie der Empfaengeranschrift Spalte fuer Spalte (R8). */
  void setzeEmpfaenger(final Belegempfaenger empfaenger) {
    final Anschrift anschrift = empfaenger.anschrift();
    empfaengerFirma = empfaenger.firma();
    empfaengerStrasse = anschrift.strasse();
    empfaengerPlz = anschrift.plz();
    empfaengerOrt = anschrift.ort();
    empfaengerLand = anschrift.land();
    empfaengerAnsprechpartner = empfaenger.ansprechpartner();
  }

  /** Uebernimmt die Kopie der eigenen Angaben Spalte fuer Spalte (R8). */
  void setzeAbsender(final Belegabsender absender) {
    final Anschrift anschrift = absender.anschrift();
    absenderName = absender.name();
    absenderStrasse = anschrift.strasse();
    absenderPlz = anschrift.plz();
    absenderOrt = anschrift.ort();
    absenderLand = anschrift.land();
    absenderEmail = absender.email();
    absenderTelefon = absender.telefon();
    absenderSteuernummer = absender.steuernummer();
    absenderUmsatzsteuerId = absender.umsatzsteuerId();
    absenderBankverbindung = absender.bankverbindung();
  }

  @Nullable Long getId() {
    return id;
  }

  long getFirmaId() {
    return firmaId;
  }

  @Nullable Long getAnsprechpartnerId() {
    return ansprechpartnerId;
  }

  @Nullable String getNummer() {
    return nummer;
  }

  Angebotszustand getZustand() {
    return zustand;
  }

  LocalDate getAngebotDatum() {
    return angebotDatum;
  }

  LocalDate getGueltigBis() {
    return gueltigBis;
  }

  @Nullable String getLeistungsbeschreibung() {
    return leistungsbeschreibung;
  }

  @Nullable String getZahlungsbedingungen() {
    return zahlungsbedingungen;
  }

  @Nullable Instant getVersendetAm() {
    return versendetAm;
  }

  @Nullable Instant getReaktionAm() {
    return reaktionAm;
  }

  @Nullable String getPdfSchluessel() {
    return pdfSchluessel;
  }

  @Nullable String getEmpfaengerFirma() {
    return empfaengerFirma;
  }

  @Nullable String getEmpfaengerStrasse() {
    return empfaengerStrasse;
  }

  @Nullable String getEmpfaengerPlz() {
    return empfaengerPlz;
  }

  @Nullable String getEmpfaengerOrt() {
    return empfaengerOrt;
  }

  @Nullable String getEmpfaengerLand() {
    return empfaengerLand;
  }

  @Nullable String getEmpfaengerAnsprechpartner() {
    return empfaengerAnsprechpartner;
  }

  @Nullable String getAbsenderName() {
    return absenderName;
  }

  @Nullable String getAbsenderStrasse() {
    return absenderStrasse;
  }

  @Nullable String getAbsenderPlz() {
    return absenderPlz;
  }

  @Nullable String getAbsenderOrt() {
    return absenderOrt;
  }

  @Nullable String getAbsenderLand() {
    return absenderLand;
  }

  @Nullable String getAbsenderEmail() {
    return absenderEmail;
  }

  @Nullable String getAbsenderTelefon() {
    return absenderTelefon;
  }

  @Nullable String getAbsenderSteuernummer() {
    return absenderSteuernummer;
  }

  @Nullable String getAbsenderUmsatzsteuerId() {
    return absenderUmsatzsteuerId;
  }

  @Nullable String getAbsenderBankverbindung() {
    return absenderBankverbindung;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }
}
