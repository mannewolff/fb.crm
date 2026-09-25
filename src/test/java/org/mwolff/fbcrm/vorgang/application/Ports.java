package org.mwolff.fbcrm.vorgang.application;

import java.io.InputStream;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.vorgang.domain.AnhangSpeicher;
import org.mwolff.fbcrm.vorgang.domain.Eintrag;
import org.mwolff.fbcrm.vorgang.domain.EintragRepository;
import org.mwolff.fbcrm.vorgang.domain.NummernkreisRepository;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;

/**
 * In-Memory-Doppel der Ports, die die Schreibwege des Vorgangs brauchen (CLAUDE-java.md §4).
 *
 * <p>Fakes statt Mocks, weil die Regeln dieses Pakets an Zustand haengen: Das Speichern vergibt
 * Kennungen, der Nummernkreis zaehlt hoch, und die Zusage „ein abgewiesenes Anlegen verbraucht
 * keine Nummer" (Kriterium 8) ist nur an einem Zaehler ablesbar, der wirklich zaehlt. Mit Mocks
 * stuende dort eine Interaktionspruefung, die dasselbe nur behauptet.
 *
 * <p>Umgesetzt ist allein, was die Schreibwege aufrufen. Die Lesewege haben ihre eigenen Tests; ein
 * Doppel, das sie mitspielte, waere ein zweiter Bestand ohne Leser — deshalb steht dort {@link
 * #nichtGebraucht()}, das laut wird, falls ein Weg doch danach greift.
 */
final class Ports {

  private Ports() {}

  private static UnsupportedOperationException nichtGebraucht() {
    return new UnsupportedOperationException("Diesen Weg braucht kein Schreibweg des Vorgangs.");
  }

  /** Der Bestand der Vorgaenge: speichert, vergibt Kennungen, liest nach. */
  static final class Vorgaenge implements VorgangRepository {

    private final Map<Long, Vorgang> bestand = new LinkedHashMap<>();
    private long naechsteId = 1L;

    @Override
    public Vorgang save(final Vorgang vorgang) {
      final Long id = vorgang.id() == null ? Long.valueOf(naechsteId++) : vorgang.id();
      final Vorgang gespeichert =
          new Vorgang(
              id,
              vorgang.nummer(),
              vorgang.titel(),
              vorgang.firmaId(),
              vorgang.ansprechpartnerId(),
              vorgang.abgeschlossen(),
              vorgang.createdAt(),
              vorgang.updatedAt());
      bestand.put(id, gespeichert);
      return gespeichert;
    }

    @Override
    public Optional<Vorgang> findById(final long id) {
      return Optional.ofNullable(bestand.get(Long.valueOf(id)));
    }

    /** Alles, was gespeichert wurde — in der Reihenfolge des ersten Speicherns. */
    List<Vorgang> alle() {
      return List.copyOf(bestand.values());
    }

    @Override
    public List<Vorgang> uebersicht(
        final String suche, final Long nummer, final boolean auchAbgeschlossene) {
      throw nichtGebraucht();
    }

    @Override
    public List<Vorgang> findByFirma(final long firmaId) {
      throw nichtGebraucht();
    }

    @Override
    public long zaehleAlle() {
      throw nichtGebraucht();
    }
  }

  /** Der Nummernkreis: zaehlt ab 1 und merkt sich, wie oft gezogen wurde. */
  static final class Nummernkreis implements NummernkreisRepository {

    private long naechste = 1L;
    private int zuege;

    @Override
    public long naechsteNummer() {
      zuege++;
      return naechste++;
    }

    /** Wie oft eine Nummer gezogen wurde — der Nachweis zu Kriterium 8. */
    int zuege() {
      return zuege;
    }
  }

  /**
   * Der Bestand der Historieneintraege: speichert, vergibt Kennungen, liest nach.
   *
   * <p>Jedes Speichern vermerkt sich im uebergebenen Protokoll. Nur daran ist E8 ablesbar — dass
   * beim Anhang das Objekt <b>vor</b> der Zeile geschrieben wird —, weil die Reihenfolge zweier
   * Ports ohne gemeinsamen Zeugen nicht pruefbar ist.
   */
  static final class Eintraege implements EintragRepository {

    /** Der Vermerk, den ein Speichern hinterlaesst. */
    static final String ZEILE = "zeile";

    private final List<String> protokoll;
    private final Map<Long, Eintrag> bestand = new LinkedHashMap<>();
    private long naechsteId = 1L;

    Eintraege(final List<String> protokoll) {
      this.protokoll = protokoll;
    }

    /** Legt den Eintrag ohne Protokolleintrag in den Bestand und liefert ihn mit Kennung. */
    Eintrag mit(final Eintrag eintrag) {
      final Eintrag gespeichert = gespeichert(eintrag);
      bestand.put(gespeichert.requireId(), gespeichert);
      return gespeichert;
    }

    @Override
    public Eintrag save(final Eintrag eintrag) {
      protokoll.add(ZEILE);
      return mit(eintrag);
    }

    @Override
    public Optional<Eintrag> findById(final long id) {
      return Optional.ofNullable(bestand.get(Long.valueOf(id)));
    }

    /** Alles, was im Bestand liegt — in der Reihenfolge des ersten Speicherns. */
    List<Eintrag> alle() {
      return List.copyOf(bestand.values());
    }

    private Eintrag gespeichert(final Eintrag eintrag) {
      final Long id = eintrag.id() == null ? Long.valueOf(naechsteId++) : eintrag.id();
      return new Eintrag(
          id,
          eintrag.vorgangId(),
          eintrag.art(),
          eintrag.text(),
          eintrag.geschehenAm(),
          eintrag.herkunft(),
          eintrag.dateiName(),
          eintrag.dateiGroesse(),
          eintrag.objektSchluessel(),
          eintrag.createdAt(),
          eintrag.geaendertAm());
    }

    @Override
    public List<Eintrag> findByVorgang(final long vorgangId) {
      throw nichtGebraucht();
    }

    @Override
    public Map<Long, Instant> juengstesGeschehenJeVorgang(final Collection<Long> vorgangIds) {
      throw nichtGebraucht();
    }
  }

  /**
   * Der Objektspeicher: vergibt fortlaufende Schluessel in der Form aus E9 und vermerkt jedes
   * Ablegen im gemeinsamen Protokoll.
   *
   * <p>Der Datenstrom wird nicht gelesen, nur seine angekuendigte Groesse festgehalten. Was mit den
   * Bytes geschieht, ist Sache des Adapters und steht in {@code S3AnhangSpeicherIT}; hier zaehlt
   * allein, <b>wann</b> abgelegt wird und <b>was</b> danach in der Zeile steht.
   */
  static final class Speicher implements AnhangSpeicher {

    /** Der Vermerk, den ein Ablegen hinterlaesst. */
    static final String OBJEKT = "objekt";

    private final List<String> protokoll;
    private final Map<String, Long> abgelegt = new LinkedHashMap<>();

    Speicher(final List<String> protokoll) {
      this.protokoll = protokoll;
    }

    @Override
    public String ablegen(final long vorgangId, final InputStream inhalt, final long groesse) {
      protokoll.add(OBJEKT);
      final String schluessel = "vorgang/" + vorgangId + "/objekt-" + (abgelegt.size() + 1);
      abgelegt.put(schluessel, Long.valueOf(groesse));
      return schluessel;
    }

    /** Was abgelegt wurde: Schluessel auf angekuendigte Groesse. */
    Map<String, Long> abgelegt() {
      return Map.copyOf(abgelegt);
    }

    @Override
    public Optional<InputStream> lesen(final String objektSchluessel) {
      throw nichtGebraucht();
    }
  }

  /** Der Bestand der Firmen, nur zum Nachschlagen. */
  static final class Firmen implements FirmaRepository {

    private final Map<Long, Firma> bestand = new LinkedHashMap<>();

    /** Legt die Firma in den Bestand und liefert dieses Doppel zurueck. */
    Firmen mit(final Firma firma) {
      bestand.put(firma.requireId(), firma);
      return this;
    }

    @Override
    public Optional<Firma> findById(final long id) {
      return Optional.ofNullable(bestand.get(Long.valueOf(id)));
    }

    @Override
    public List<Firma> uebersicht(final String suche, final boolean auchStillgelegte) {
      throw nichtGebraucht();
    }

    @Override
    public Firma save(final Firma firma) {
      throw nichtGebraucht();
    }

    @Override
    public long zaehleAlle() {
      throw nichtGebraucht();
    }
  }

  /** Der Bestand der Ansprechpartner, nur zum Nachschlagen. */
  static final class Partner implements AnsprechpartnerRepository {

    private final Map<Long, Ansprechpartner> bestand = new LinkedHashMap<>();

    /** Legt den Ansprechpartner in den Bestand und liefert dieses Doppel zurueck. */
    Partner mit(final Ansprechpartner partner) {
      bestand.put(partner.requireId(), partner);
      return this;
    }

    @Override
    public Optional<Ansprechpartner> findById(final long id) {
      return Optional.ofNullable(bestand.get(Long.valueOf(id)));
    }

    @Override
    public Ansprechpartner save(final Ansprechpartner ansprechpartner) {
      throw nichtGebraucht();
    }

    @Override
    public List<Ansprechpartner> findByFirma(final long firmaId) {
      throw nichtGebraucht();
    }

    @Override
    public Map<Long, Long> zaehleAktiveJeFirma(final Collection<Long> firmaIds) {
      throw nichtGebraucht();
    }
  }
}
