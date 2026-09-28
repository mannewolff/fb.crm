package org.mwolff.fbcrm.auftrag.application;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.mwolff.fbcrm.auftrag.domain.Auftrag;
import org.mwolff.fbcrm.auftrag.domain.AuftragRepository;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Auswertung „Auftragsbestand": was noch vor mir liegt (Kriterien 12, 13).
 *
 * <p><b>Zwei Gruppen fehlen, und beide Filter sind Fragen der Auswertung.</b> Der abgeschlossene
 * Auftrag faellt schon in der Abfrage heraus (F7) — seine restlichen Tage werden nie abgerechnet.
 * Der Auftrag am abgeschlossenen Vorgang faellt hier heraus und kommt nach dem Wiedereroeffnen von
 * selbst zurueck (Kriterium 12); geschrieben wird dafuer nichts.
 *
 * <p><b>Der abgerechnete Betrag ist 0,00 € — an genau einer Stelle</b> (Plan E12). Es gibt noch
 * keine Rechnung; was abgerechnet ist, weiss allein sie. Kein Port mit leerer Umsetzung, denn eine
 * Portdefinition ohne zweiten Umsetzer ist Vorratsbau, und nichts davon in der Domaene: {@code
 * Auftrag.abgerechneterBetrag()} waere eine Behauptung ueber Rechnungen, die der Auftrag nicht
 * kennen kann.
 *
 * <p><b>Die Reihenfolge braucht einen zweiten Schluessel</b> (Plan E13): offener Rest absteigend,
 * bei gleichem Rest die hoehere Kennung zuerst. Kriterium 12 nennt nur den ersten; ohne den zweiten
 * lieferten zwei Aufrufe verschiedene Listen. Sortiert wird hier und nicht im Bestand — die Ordnung
 * ist eine Aussage der Ansicht, dieselbe Aufteilung wie in {@code PipelineUseCase}.
 *
 * <p>Die Zahl der Abfragen bleibt fest: eine fuer die Auftraege, eine fuer ihre Positionen, eine
 * fuer alle Vorgaenge und eine je <b>Firma</b>, nicht je Zeile (Muster {@code VorgangZeilen}).
 */
@Service
@Transactional(readOnly = true)
public class AuftragsbestandUseCase {

  /* Bis Idee #7 die Rechnung bringt, ist nichts abgerechnet — die eine Stelle dafuer (Plan E12). */
  private static final BigDecimal NICHTS_ABGERECHNET = BigDecimal.ZERO;

  private static final Comparator<AuftragsbestandZeile> REIHENFOLGE =
      Comparator.comparing(AuftragsbestandZeile::offenerRest, Comparator.reverseOrder())
          .thenComparing(AuftragsbestandZeile::auftragId, Comparator.reverseOrder());

  private final AuftragRepository auftraege;
  private final VorgangRepository vorgaenge;
  private final FirmaRepository firmen;

  public AuftragsbestandUseCase(
      final AuftragRepository auftraege,
      final VorgangRepository vorgaenge,
      final FirmaRepository firmen) {
    this.auftraege = auftraege;
    this.vorgaenge = vorgaenge;
    this.firmen = firmen;
  }

  /** Der Auftragsbestand: die laufenden Auftraege und die beiden Kennzahlen darueber. */
  public Auftragsbestand bestand() {
    final List<Auftrag> kandidaten = auftraege.bestandskandidaten();
    final Map<Long, Vorgang> jeVorgang =
        vorgaenge.findByIds(kennungen(kandidaten)).stream()
            .collect(Collectors.toMap(Vorgang::requireId, Function.identity()));
    final Map<Long, String> namen = new HashMap<>();
    return Auftragsbestand.of(
        kandidaten.stream()
            .filter(auftrag -> !vorgang(auftrag, jeVorgang).abgeschlossen())
            .map(auftrag -> zeile(auftrag, jeVorgang, namen))
            .sorted(REIHENFOLGE)
            .toList());
  }

  /* Jede Kennung einmal: An einem Vorgang darf mehr als ein Auftrag haengen — ein Rahmenauftrag. */
  private static List<Long> kennungen(final List<Auftrag> kandidaten) {
    return List.copyOf(
        kandidaten.stream()
            .map(Auftrag::vorgangId)
            .collect(Collectors.toCollection(LinkedHashSet::new)));
  }

  private AuftragsbestandZeile zeile(
      final Auftrag auftrag, final Map<Long, Vorgang> jeVorgang, final Map<Long, String> namen) {
    final Vorgang vorgang = vorgang(auftrag, jeVorgang);
    return AuftragsbestandZeile.of(
        auftrag, vorgang, firmaName(vorgang.firmaId(), namen), NICHTS_ABGERECHNET);
  }

  /*
   * Der Fremdschluessel auftrag.vorgang_id schliesst diesen Fall aus. Traete er trotzdem ein, waere
   * der Bestand kaputt — dann ist ein lauter Fehler die richtige Antwort und keine Zeile, die einen
   * Vorgang erfindet. Dieselbe Abwaegung wie in PipelineUseCase.
   */
  private static Vorgang vorgang(final Auftrag auftrag, final Map<Long, Vorgang> jeVorgang) {
    final Vorgang vorgang = jeVorgang.get(auftrag.vorgangId());
    if (vorgang == null) {
      throw new IllegalStateException(
          "Zum Auftrag gibt es keinen Vorgang mit der Kennung " + auftrag.vorgangId() + ".");
    }
    return vorgang;
  }

  /*
   * Gemerkt statt je Zeile geholt: Ein Freiberufler hat zu einer Firma oft mehrere Vorgaenge, und
   * der Auftragsbestand zeigt sie alle nebeneinander.
   */
  private String firmaName(final long firmaId, final Map<Long, String> namen) {
    final String bekannt = namen.get(firmaId);
    if (bekannt != null) {
      return bekannt;
    }
    final String name =
        firmen
            .findById(firmaId)
            .map(Firma::name)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Zum Vorgang gibt es keine Firma mit der Kennung " + firmaId + "."));
    namen.put(firmaId, name);
    return name;
  }
}
