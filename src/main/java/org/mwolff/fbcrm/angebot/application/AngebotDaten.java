package org.mwolff.fbcrm.angebot.application;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;

/**
 * Die Angaben, mit denen ein Angebot geaendert wird — in jedem Status (Issue #127, Kriterium 5).
 *
 * <p>Das Angebot wird als Ganzes geschrieben: Was hier steht, ersetzt den bisherigen Stand
 * vollstaendig. Die Liste <b>ist</b> die Reihenfolge — die Plaetze vergibt der Bestand daraus neu
 * (E24). Die Firma steht nicht darin: Sie bleibt die, bei der das Angebot angelegt wurde.
 *
 * @param angebotDatum Datum des Angebots
 * @param ansprechpartnerId Kennung des Ansprechpartners bei der Firma, oder {@code null}
 * @param beschreibung der Text des Angebots, oder {@code null}
 * @param positionen die vollstaendige Positionsliste in der gewuenschten Reihenfolge
 */
public record AngebotDaten(
    LocalDate angebotDatum,
    @Nullable Long ansprechpartnerId,
    @Nullable String beschreibung,
    List<Angebotsposition> positionen) {

  /** Nimmt die Positionen als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public AngebotDaten {
    positionen = List.copyOf(positionen);
  }
}
