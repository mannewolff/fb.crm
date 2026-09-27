package org.mwolff.fbcrm.angebot.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotsstand;
import org.mwolff.fbcrm.firma.domain.Firma;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.vorgang.domain.Vorgang;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Auswertung „Pipeline": alle offenen Angebote mit ihrer gewichteten Summe (Kriterien 23, 24,
 * 26).
 *
 * <p><b>Was offen ist, entscheidet dieser Anwendungsfall</b> und nicht der Bestand. Offen ist ein
 * Angebot, das heute den Stand {@link Angebotsstand#VERSENDET} traegt — damit fallen in
 * <b>einem</b> Ausdruck der Entwurf, das angenommene, das abgelehnte, das abgeloeste und das
 * abgelaufene Angebot heraus (Kriterium 23). „Abgelaufen" steht in keiner Spalte, sondern entsteht
 * aus dem Vergleich mit dem heutigen Tag der Geschaeftszone (E4, E12); die Abfrage grenzt die Menge
 * ein, sie trifft die Auswahl nicht. Dazu kommt der sechste Grund, den das Angebot selbst nicht
 * kennt: Am abgeschlossenen Vorgang zaehlt kein Angebot mehr.
 *
 * <p><b>Gerechnet wird in Java</b>, je Angebot und erst danach addiert ({@link PipelineZeile},
 * {@link Pipeline}) — nie als SQL-Aggregation: Eine zweite Rundungsimplementierung koennte von der
 * ersten abweichen, und genau das schliesst Kriterium 5 aus.
 *
 * <p><b>Die Reihenfolge</b> beantwortet „was kommt als naechstes": der erwartete
 * Entscheidungszeitpunkt aufsteigend, Zeilen ohne Datum zuletzt, bei gleichem Tag die groessere
 * Chance zuerst. Die hoehere Angebotskennung haengt als letzter Schluessel dahinter, damit zwei
 * Aufrufe dieselbe Liste liefern — der Bestand sagt keine Ordnung zu.
 *
 * <p>Sortiert wird hier und nicht im Bestand, aus demselben Grund wie in {@code
 * AngeboteDesVorgangsUseCase}: Die Ordnung ist eine Aussage der Ansicht. Und die Zahl der Abfragen
 * bleibt fest — eine fuer die Angebote, eine fuer ihre Positionen, eine fuer alle Vorgaenge und
 * eine je <b>Firma</b>, nicht je Zeile.
 */
@Service
@Transactional(readOnly = true)
public class PipelineUseCase {

  private static final Comparator<PipelineZeile> REIHENFOLGE =
      Comparator.comparing(
              PipelineZeile::entscheidungErwartetAm,
              Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(PipelineZeile::gewichteteSumme, Comparator.reverseOrder())
          .thenComparing(PipelineZeile::angebotId, Comparator.reverseOrder());

  private final AngebotRepository angebote;
  private final VorgangRepository vorgaenge;
  private final FirmaRepository firmen;
  private final Clock clock;

  public PipelineUseCase(
      final AngebotRepository angebote,
      final VorgangRepository vorgaenge,
      final FirmaRepository firmen,
      final Clock clock) {
    this.angebote = angebote;
    this.vorgaenge = vorgaenge;
    this.firmen = firmen;
    this.clock = clock;
  }

  /** Die Pipeline des heutigen Tages: die offenen Angebote und die beiden Summen darueber. */
  public Pipeline pipeline() {
    final LocalDate heute = AngebotAnsicht.heute(clock);
    final List<Angebot> offen =
        angebote.pipelinekandidaten(heute).stream()
            .filter(angebot -> angebot.stand(heute) == Angebotsstand.VERSENDET)
            .toList();
    final Map<Long, Vorgang> jeVorgang =
        vorgaenge.findByIds(kennungen(offen)).stream()
            .collect(Collectors.toMap(Vorgang::requireId, Function.identity()));
    final Map<Long, String> namen = new HashMap<>();
    return Pipeline.of(
        offen.stream()
            .filter(angebot -> !vorgang(angebot, jeVorgang).abgeschlossen())
            .map(angebot -> zeile(angebot, jeVorgang, namen))
            .sorted(REIHENFOLGE)
            .toList());
  }

  /* Jede Kennung einmal: An einem Vorgang darf mehr als ein offenes Angebot haengen (Kriterium 19). */
  private static List<Long> kennungen(final List<Angebot> offen) {
    return List.copyOf(
        offen.stream()
            .map(Angebot::vorgangId)
            .collect(Collectors.toCollection(LinkedHashSet::new)));
  }

  private PipelineZeile zeile(
      final Angebot angebot, final Map<Long, Vorgang> jeVorgang, final Map<Long, String> namen) {
    final Vorgang vorgang = vorgang(angebot, jeVorgang);
    return PipelineZeile.of(angebot, vorgang, firmaName(vorgang.firmaId(), namen));
  }

  /*
   * Der Fremdschluessel angebot.vorgang_id schliesst diesen Fall aus. Traete er trotzdem ein, waere
   * der Bestand kaputt — dann ist ein lauter Fehler die richtige Antwort und keine Zeile, die einen
   * Vorgang erfindet. Dieselbe Abwaegung wie bei der fehlenden Firma in der Vorgangsliste.
   */
  private static Vorgang vorgang(final Angebot angebot, final Map<Long, Vorgang> jeVorgang) {
    final Vorgang vorgang = jeVorgang.get(angebot.vorgangId());
    if (vorgang == null) {
      throw new IllegalStateException(
          "Zum Angebot gibt es keinen Vorgang mit der Kennung " + angebot.vorgangId() + ".");
    }
    return vorgang;
  }

  /*
   * Gemerkt statt je Zeile geholt, wie in der Vorgangsliste: Ein Freiberufler hat zu einer Firma
   * oft mehrere Vorgaenge, und die Pipeline zeigt sie alle nebeneinander.
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
