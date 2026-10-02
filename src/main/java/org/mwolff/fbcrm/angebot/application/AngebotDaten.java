package org.mwolff.fbcrm.angebot.application;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Die Angaben, mit denen ein Angebot geaendert wird — in jedem Status (Issue #127, Kriterium 5).
 *
 * <p>Das Angebot wird als Ganzes geschrieben: Was hier steht, ersetzt den bisherigen Stand
 * vollstaendig. Die Liste <b>ist</b> die Reihenfolge — die Plaetze vergibt der Bestand daraus neu
 * (E24). Die Firma steht nicht darin: Sie bleibt die, bei der das Angebot angelegt wurde.
 *
 * <p><b>Die Art gehoert dazu</b> (Issue #227, Kriterium 8 von #207): {@code intern} sagt, welche
 * Art das Angebot <em>danach</em> hat. Der Status steht weiterhin nicht hier — er folgt der Art von
 * selbst ({@code Angebot.umgestellt(...)}) und hat sonst seine eigenen Wege.
 *
 * <p>Die Positionen reisen als {@link Positionsangabe} und nicht als {@code Angebotsposition}:
 * Menge, Einheit, Preis und Abrechnungsart duerfen auf dem Weg herein fehlen, weil ihre Pflicht an
 * der Art haengt (E7). Vollstaendig macht sie {@link AngebotAendernUseCase}.
 *
 * @param angebotDatum Datum des Angebots
 * @param ansprechpartnerId Kennung des Ansprechpartners bei der Firma, oder {@code null}
 * @param beschreibung der Text des Angebots, oder {@code null}
 * @param intern ob das Angebot danach die eigene interne Arbeit festhaelt
 * @param positionen die vollstaendige Positionsliste in der gewuenschten Reihenfolge
 */
public record AngebotDaten(
    LocalDate angebotDatum,
    @Nullable Long ansprechpartnerId,
    @Nullable String beschreibung,
    boolean intern,
    List<Positionsangabe> positionen) {

  /** Nimmt die Positionen als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public AngebotDaten {
    positionen = List.copyOf(positionen);
  }
}
