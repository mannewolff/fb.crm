package org.mwolff.fbcrm.common.web;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Der Stand, auf dem ein schreibender Schritt beruht, gelesen aus dem Kopf {@code If-Match} (Plan
 * #415, E2, E3, E12).
 *
 * <p>Ein geschuetzter Weg nimmt diesen Typ als Parameter; {@link Leser} fuellt ihn, bevor der Weg
 * zum Zuge kommt. Fehlt der Kopf oder ist er unlesbar, wird mit 428 abgewiesen, und es wird nichts
 * geschrieben — ein freiwilliger Kopf waere ein stilles Loch im Schutz (E3).
 *
 * <p>Gelesen wird genau ein starkes Etikett aus Ziffern, etwa {@code "3"}: So vergibt der Server
 * seine Versionen. Ein schwaches Etikett ({@code W/"3"}), der Platzhalter {@code *} oder eine Liste
 * nennen keinen Stand, den der Handelnde gesehen hat, und werden wie ein fehlender Kopf behandelt.
 *
 * @param version die vorgelegte Version
 */
public record Bedingungskopf(long version) {

  /** Antworttext, wenn der Kopf fehlt oder unlesbar ist; er wiederholt den Kopf nicht. */
  public static final String MELDUNG =
      "Die Anfrage nennt den Stand nicht, auf dem die Änderung beruht.";

  /** Hoechstens 18 Ziffern: So passt jede Zahl in ein {@code long}. */
  private static final Pattern STARKES_ETIKETT = Pattern.compile("\"(\\d{1,18})\"");

  /**
   * Liest den Kopf.
   *
   * @param kopf der Wert von {@code If-Match}, oder {@code null}, wenn er fehlt
   * @return der vorgelegte Stand
   * @throws Fehlt wenn der Kopf fehlt oder keinen lesbaren Stand nennt
   */
  public static Bedingungskopf aus(final @Nullable String kopf) {
    if (kopf == null) {
      throw new Fehlt();
    }
    final Matcher etikett = STARKES_ETIKETT.matcher(kopf);
    if (!etikett.matches()) {
      throw new Fehlt();
    }
    return new Bedingungskopf(Long.parseLong(etikett.group(1)));
  }

  /** Der Kopf {@code If-Match} fehlt oder nennt keinen lesbaren Stand (E3). */
  @ResponseStatus(code = HttpStatus.PRECONDITION_REQUIRED, reason = MELDUNG)
  public static final class Fehlt extends RuntimeException {

    Fehlt() {
      super();
    }
  }

  /**
   * Fuellt einen Parameter vom Typ {@link Bedingungskopf} aus der Anfrage.
   *
   * <p>Registriert in {@link BedingungskopfKonfiguration}; ein Test mit {@code
   * MockMvcBuilders.standaloneSetup} haengt ihn mit {@code setCustomArgumentResolvers} selbst an.
   */
  public static final class Leser implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(final MethodParameter parameter) {
      return parameter.getParameterType() == Bedingungskopf.class;
    }

    @Override
    public Bedingungskopf resolveArgument(
        final MethodParameter parameter,
        final @Nullable ModelAndViewContainer mavContainer,
        final NativeWebRequest webRequest,
        final @Nullable WebDataBinderFactory binderFactory) {
      return aus(webRequest.getHeader(HttpHeaders.IF_MATCH));
    }
  }
}
