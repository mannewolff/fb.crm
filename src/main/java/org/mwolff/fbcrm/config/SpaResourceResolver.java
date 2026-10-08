package org.mwolff.fbcrm.config;

import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Loest jeden Pfad auf, der keine Datei ist, auf {@code index.html} auf (E20).
 *
 * <p>Damit traegt der Server die Routen der Oberflaeche, ohne sie zu kennen: Wer {@code /anfragen}
 * neu laedt, bekommt die Anwendung und nicht 404. Ausgenommen sind {@code /api} und {@code
 * /actuator} — dort ist ein unbekannter Pfad wirklich unbekannt, und eine ausgelieferte Seite statt
 * eines 404 verwirrte jeden Aufrufer.
 *
 * <p>Autorisiert wird ausschliesslich an der API (E20): Eine SPA-Route traegt keine Daten. Die
 * Umleitung auf die Anmeldeseite leistet {@code ProtectedRoute} in der Oberflaeche.
 *
 * <p>Der Pfadschutz gegen {@code ../} bleibt der Oberklasse ueberlassen — deshalb geht die Suche
 * nach einer vorhandenen Datei ueber {@code super.getResource} und nicht ueber ein eigenes {@code
 * createRelative}.
 */
class SpaResourceResolver extends PathResourceResolver {

  private final Resource index;

  SpaResourceResolver(final Resource index) {
    super();
    this.index = index;
  }

  @Override
  protected @Nullable Resource getResource(final String resourcePath, final Resource location)
      throws IOException {
    if (istSchnittstellenPfad(resourcePath)) {
      return null;
    }
    final Resource vorhanden = super.getResource(resourcePath, location);
    if (vorhanden != null) {
      return vorhanden;
    }
    return index.exists() ? index : null;
  }

  /**
   * Ob der Pfad zur API oder zum Actuator gehoert.
   *
   * <p>Der fuehrende Schrägstrich faellt vorher weg: {@code ResourceHttpRequestHandler} reicht den
   * Pfad je nach Spring-Fassung mit oder ohne ihn herein.
   */
  private static boolean istSchnittstellenPfad(final String resourcePath) {
    final String pfad = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
    return "api".equals(pfad)
        || pfad.startsWith("api/")
        || "actuator".equals(pfad)
        || pfad.startsWith("actuator/");
  }
}
