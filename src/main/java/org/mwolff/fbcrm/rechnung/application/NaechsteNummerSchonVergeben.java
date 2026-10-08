package org.mwolff.fbcrm.rechnung.application;

import java.util.List;
import java.util.Map;
import org.mwolff.fbcrm.common.Feldfehler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die eingereichte naechste Nummer ergaebe eine Rechnungsnummer, die eine Rechnung schon traegt
 * (#160, Kriterium 19; Plan #169, E16).
 *
 * <p>Die Vorabpruefung der Einstellungen. Ohne sie koennte der Anwender den Zaehler
 * widerspruchsfrei auf eine Zahl stellen, die beim naechsten Stellen unvermeidlich an {@code
 * UNIQUE} scheitert — er erfuehre es erst, wenn er eine Rechnung stellen will, und zwar an einer
 * Maske, an der die Ursache nicht steht. Hier erfaehrt er es dort, wo er die Zahl eingetragen hat.
 *
 * <p>Nicht zu verwechseln mit {@link RechnungsnummerSchonVergeben}: Dort hat der Zaehler die Nummer
 * gezogen, hier hat der Anwender sie gesetzt. Darum ist dies ein {@link Feldfehler} und jenes
 * keiner — nur hier gibt es ein Feld, an dem die Meldung haengen kann.
 *
 * <p><b>422 und nicht 409:</b> Der Rumpf ist wohlgeformt, sein Wert aber fachlich nicht
 * verarbeitbar; derselbe Grund wie bei den uebrigen Feldfehlern dieses Projekts (vgl. {@code
 * angebot.application.AnsprechpartnerNichtWaehlbar}). Die 409 von {@link
 * RechnungsnummerSchonVergeben} traegt eine andere Aussage: Dort hat sich der Stand des
 * Nummernkreises hinter dem Ruecken des Anwenders geaendert.
 *
 * <p><b>Die Meldung nennt die Nummer</b>, weil der Anwender nur die laufende Zahl eingegeben hat —
 * welche Rechnungsnummer daraus mit seinem Muster entstuende, rechnet die Anwendung. Sie verraet
 * nichts, was er nicht ohnehin in seiner Rechnungsliste sieht.
 */
@ResponseStatus(code = HttpStatus.UNPROCESSABLE_ENTITY)
public final class NaechsteNummerSchonVergeben extends Feldfehler {

  /** Der Satz vor der Nummer; zusammen sind sie das {@code detail} der Antwort. */
  public static final String MELDUNG =
      "Mit dieser Nummer entstünde eine schon vergebene Rechnungsnummer: ";

  /** Das Feld, unter dem die Maske die naechste laufende Nummer fuehrt. */
  public static final String FELD = "naechsteNummer";

  private final String nummer;

  /**
   * @param nummer die Rechnungsnummer, die aus Muster und eingereichter Zahl entstuende
   */
  NaechsteNummerSchonVergeben(final String nummer) {
    super(MELDUNG + nummer);
    this.nummer = nummer;
  }

  @Override
  public Map<String, List<String>> felder() {
    return Map.of(FELD, List.of(MELDUNG + nummer));
  }
}
