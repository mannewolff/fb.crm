package org.mwolff.fbcrm.angebot.application;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.domain.Angebotsposition;

/**
 * Die Angaben, mit denen ein Entwurf fortgeschrieben wird (Kriterium 6, E8).
 *
 * <p>Der Entwurf wird als Ganzes geschrieben: Was hier steht, ersetzt den bisherigen Stand
 * vollstaendig. Die Liste <b>ist</b> die Reihenfolge — die Plaetze vergibt der Bestand daraus neu
 * (E24).
 *
 * <p>Ohne Angebotsdatum: Es entsteht beim Anlegen und aendert sich nicht (Kriterium 3).
 *
 * @param gueltigBis letzter Tag der Gueltigkeit; im Entwurf frei, auch vor dem Angebotsdatum (E27)
 * @param leistungsbeschreibung einleitender Text, oder {@code null}
 * @param zahlungsbedingungen Zahlungsbedingungen, oder {@code null}
 * @param positionen die vollstaendige Positionsliste in der gewuenschten Reihenfolge
 */
public record EntwurfDaten(
    LocalDate gueltigBis,
    @Nullable String leistungsbeschreibung,
    @Nullable String zahlungsbedingungen,
    List<Angebotsposition> positionen) {

  /** Nimmt die Positionen als Kopie: Der Aufrufer darf seine Liste danach weiterverwenden. */
  public EntwurfDaten {
    positionen = List.copyOf(positionen);
  }
}
