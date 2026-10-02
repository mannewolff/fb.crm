package org.mwolff.fbcrm.arbeitszeit.application;

import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.common.Feldfehler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Auf die gewaehlte Position darf keine Arbeitszeit gebucht werden (Issue #193, Antworten 2, 3 und
 * 5; Plan #194, A6, A7; Plan #218, E22).
 *
 * <p>Vier Lagen, zwei Saetze. Am <b>Angebot an einen Kunden</b> sind es drei: Die Position rechnet
 * nicht nach Aufwand ab, sie zaehlt nicht in Stunden, oder ihr Angebot ist noch nicht bestellt
 * beziehungsweise schon abgerechnet — dafuer steht {@link #MELDUNG_EXTERN}. An der <b>internen
 * Arbeit</b> ist es eine: Sie ist abgeschlossen, und dann wird auf keine ihrer Positionen mehr
 * gebucht; dafuer steht {@link #MELDUNG_INTERN}. Zwei Saetze, weil der erste an der internen Arbeit
 * falsch waere: Dort traegt jede Position Stunden ({@link Buchbarkeit}), und der Satz erscheint am
 * Feld der Positionswahl.
 *
 * <p>Die Meldung nennt die Bedingungen ihrer Art, damit der Anwender weiss, wo er suchen muss;
 * welche von ihnen verletzt ist, verraet sie nicht — die Auswahlliste der Maske zeigt ohnehin nur
 * buchbare Positionen, und wer hier ankommt, hat eine veraltete Liste.
 *
 * <p><b>Eine unbekannte Kennung fuehrt zur selben Antwort</b>, und zwar zu {@link #MELDUNG_EXTERN}:
 * Dass es die Position nicht gibt, ist fuer den Absender dasselbe wie „darauf wird nicht gebucht";
 * eine eigene Meldung dafuer verriete nur, welche Kennungen anderswo vergeben sind. Die Art eines
 * Angebots, das es nicht gibt, ist ohnehin nicht bestimmbar.
 *
 * <p>422 und nicht 404: Die Kennung kommt im Rumpf und nicht im Pfad — angefragt wird der
 * Zeiteintrag, und die Eingabe zu ihm ist wohlgeformt, aber fachlich nicht verarbeitbar. Ein {@link
 * Feldfehler} und nicht nur ein Status, weil die Maske ihre Meldungen aus {@code fieldErrors}
 * liest.
 */
@ResponseStatus(
    code = HttpStatus.UNPROCESSABLE_ENTITY,
    reason = PositionNichtBuchbar.MELDUNG_EXTERN)
public final class PositionNichtBuchbar extends Feldfehler {

  /** Was der Anwender am Angebot an einen Kunden liest — auch bei unbekannter Kennung. */
  public static final String MELDUNG_EXTERN =
      "Auf diese Position kann keine Arbeitszeit gebucht werden: Sie muss nach Aufwand in Stunden"
          + " abgerechnet werden, und ihr Angebot muss bestellt oder erledigt sein.";

  /** Was der Anwender an der internen Arbeit liest — dort traegt jede Position Stunden. */
  public static final String MELDUNG_INTERN =
      "Auf diese Position kann keine Arbeitszeit gebucht werden: Ihr Angebot muss laufen.";

  /** Das Feld, unter dem die Maske die gewaehlte Position fuehrt. */
  public static final String FELD = "angebotPositionId";

  private final String meldung;

  /*
   * Der Zugang laeuft ueber die beiden Fabriken: Welcher Satz gilt, haengt an der Art des Angebots,
   * und die soll der Aufrufer benennen muessen statt eine Zeichenkette zu waehlen.
   */
  private PositionNichtBuchbar(final String meldung) {
    super(meldung);
    this.meldung = meldung;
  }

  /** Die Lage am Angebot an einen Kunden — und die bei unbekannter Positionskennung. */
  public static PositionNichtBuchbar amKundenangebot() {
    return new PositionNichtBuchbar(MELDUNG_EXTERN);
  }

  /** Die Lage an der internen Arbeit: Sie ist abgeschlossen. */
  public static PositionNichtBuchbar anInternerArbeit() {
    return new PositionNichtBuchbar(MELDUNG_INTERN);
  }

  @Override
  public Map<String, List<String>> felder() {
    return Map.of(FELD, List.of(meldung));
  }
}
