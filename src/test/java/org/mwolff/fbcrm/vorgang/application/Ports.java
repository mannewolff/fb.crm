package org.mwolff.fbcrm.vorgang.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mwolff.fbcrm.firma.domain.Ansprechpartner;
import org.mwolff.fbcrm.firma.domain.AnsprechpartnerRepository;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
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
