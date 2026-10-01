package org.mwolff.fbcrm.arbeitszeit.application;

import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.common.Feldfehler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Auf die gewaehlte Position darf keine Arbeitszeit gebucht werden (Issue #193, Antworten 2, 3 und
 * 5; Plan #194, A6, A7).
 *
 * <p>Drei Lagen, eine Antwort: Die Position rechnet nicht nach Aufwand ab, sie zaehlt nicht in
 * Stunden, oder ihr Angebot ist noch nicht bestellt beziehungsweise schon abgerechnet. Die Meldung
 * nennt beide Bedingungen, damit der Anwender weiss, wo er suchen muss; welche von ihnen verletzt
 * ist, verraet sie nicht — die Auswahlliste der Maske zeigt ohnehin nur buchbare Positionen, und
 * wer hier ankommt, hat eine veraltete Liste.
 *
 * <p><b>Eine unbekannte Kennung fuehrt zur selben Antwort.</b> Dass es die Position nicht gibt, ist
 * fuer den Absender dasselbe wie „darauf wird nicht gebucht"; eine eigene Meldung dafuer verriete
 * nur, welche Kennungen anderswo vergeben sind.
 *
 * <p>422 und nicht 404: Die Kennung kommt im Rumpf und nicht im Pfad — angefragt wird der
 * Zeiteintrag, und die Eingabe zu ihm ist wohlgeformt, aber fachlich nicht verarbeitbar. Ein {@link
 * Feldfehler} und nicht nur ein Status, weil die Maske ihre Meldungen aus {@code fieldErrors}
 * liest.
 */
@ResponseStatus(code = HttpStatus.UNPROCESSABLE_ENTITY, reason = PositionNichtBuchbar.MELDUNG)
public final class PositionNichtBuchbar extends Feldfehler {

  /** Was der Anwender am Feld liest — derselbe Satz, den die Antwort als {@code detail} traegt. */
  public static final String MELDUNG =
      "Auf diese Position kann keine Arbeitszeit gebucht werden: Sie muss nach Aufwand in Stunden"
          + " abgerechnet werden, und ihr Angebot muss bestellt oder erledigt sein.";

  /** Das Feld, unter dem die Maske die gewaehlte Position fuehrt. */
  public static final String FELD = "angebotPositionId";

  @Override
  public Map<String, List<String>> felder() {
    return Map.of(FELD, List.of(MELDUNG));
  }
}
