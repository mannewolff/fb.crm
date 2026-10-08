package org.mwolff.fbcrm.angebot.web;

import java.io.IOException;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.mwolff.fbcrm.angebot.application.AngebotsanlageUseCase;
import org.mwolff.fbcrm.angebot.application.Anlageninhalt;
import org.mwolff.fbcrm.angebot.domain.Vorschauart;
import org.mwolff.fbcrm.common.web.Anlagekopf;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Die Wege der Anlagen am Angebot (Issue #148, Plan #150 E5).
 *
 * <p>Eigene Wege unter {@code /api/angebote/{angebotId}/anlagen} und keine Liste in {@code
 * AngebotResponse}, aus demselben Grund wie bei den Kommentaren: Die Angebots-Antwort kommt auch
 * nach dem Aendern und nach jedem Statuswechsel zurueck und truege die Anlagen sonst bei jedem
 * dieser Wege mit.
 *
 * <p>Der Controller entscheidet nichts (CLAUDE-java.md §6.3). Warum {@code POST} mit einem Rumpf
 * antwortet: Kennung, Vorschauart und Zeitpunkt entstehen erst im Anwendungsfall, und der Bereich
 * reiht die neue Zeile ohne zweiten Aufruf ein (Kriterium 2).
 *
 * <p><b>Hier sitzt die Sicherheitsgrenze der Auslieferung.</b> Eine Anlage ist eine Datei von
 * aussen, und sie darf im Browser nie als etwas anderes ankommen, als die Anwendung am Inhalt
 * festgestellt hat. Deshalb traegt {@link #inhalt} <b>immer</b> dieselben Kopfzeilen — {@code
 * attachment} statt Anzeige, die Sandbox-Regel als letzte Schranke, und einen {@code Content-Type},
 * der allein der gespeicherten {@link Vorschauart} folgt. Die beim Hochladen gemeldete Art liest
 * dieser Controller nirgends: Sie steht nicht in der Datenbank (E2), und hier wird sie nicht aus
 * dem {@link MultipartFile} genommen. {@code X-Content-Type-Options: nosniff} und {@code
 * X-Frame-Options: DENY} stehen nicht in dieser Klasse, weil sie aus den Spring-Security-Vorgaben
 * auf jede Antwort gehen; {@code AngebotAnlageIT} prueft, dass sie <b>auch auf diesem Weg</b>
 * ankommen.
 *
 * <p>Keine Aenderung an {@code SecurityConfig}: Die Wege fallen unter das bestehende {@code
 * /api/**} fuer angemeldete Benutzer (E6, E13).
 */
@RestController
@RequestMapping("/api/angebote/{angebotId}/anlagen")
public class AngebotAnlagenController {

  private final AngebotsanlageUseCase useCase;

  public AngebotAnlagenController(final AngebotsanlageUseCase useCase) {
    this.useCase = useCase;
  }

  /** Die Anlagen des Angebots, neueste zuerst (Kriterium 4). */
  @GetMapping
  public AngebotAnlagenResponse liste(@PathVariable final long angebotId) {
    return AngebotAnlagenResponse.of(useCase.liste(angebotId));
  }

  /**
   * Nimmt eine Datei als Anlage des Angebots an (Kriterium 2).
   *
   * <p>Der Dateiname geht <b>ungesaeubert</b> weiter — die Saeuberung entscheidet der
   * Anwendungsfall, und ein fehlender Name ist dort ein leerer. Ebenso die Bytes: Sie gehen als
   * Datenstrom weiter und nicht als Feld, weil eine Anlage bis zur Upload-Grenze gross sein darf.
   *
   * @param angebotId Kennung des Angebots
   * @param datei der Teil {@code datei} der Formulardaten
   * @throws IOException wenn sich der Datenstrom der hochgeladenen Datei nicht oeffnen laesst
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AngebotAnlageResponse hochladen(
      @PathVariable final long angebotId, @RequestPart("datei") final MultipartFile datei)
      throws IOException {
    return AngebotAnlageResponse.of(
        useCase.ladeHoch(
            angebotId,
            Objects.requireNonNullElse(datei.getOriginalFilename(), ""),
            datei.getInputStream(),
            datei.getSize()));
  }

  /**
   * Gibt den Inhalt einer Anlage heraus (Kriterium 9, E5).
   *
   * <p>Der Rumpf ist ein {@link InputStreamResource} und damit der Datenstrom aus dem
   * Objektspeicher: Er wird durchgereicht, nicht eingesammelt. Die Laenge steht aus der Zeile
   * daneben, damit der Konverter den Strom nicht zusaetzlich abmessen muss.
   */
  @GetMapping("/{anlageId}/inhalt")
  public ResponseEntity<Resource> inhalt(
      @PathVariable final long angebotId, @PathVariable final long anlageId) {
    final Anlageninhalt anlage = useCase.liesInhalt(angebotId, anlageId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, Anlagekopf.contentDisposition(anlage.dateiName()))
        .header(Anlagekopf.INHALTSREGEL, Anlagekopf.SANDKASTEN)
        .contentType(ausgabetyp(anlage.vorschauArt()))
        .contentLength(anlage.groesse())
        .body(new InputStreamResource(anlage.inhalt()));
  }

  /** Loescht die Anlage (Kriterium 10). */
  @DeleteMapping("/{anlageId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void loeschen(@PathVariable final long angebotId, @PathVariable final long anlageId) {
    useCase.loesche(angebotId, anlageId);
  }

  /*
   * Ohne erkannte Vorschauart geht die Anlage als undeuteter Bytestrom hinaus (E5). Das ist der
   * Fall der hochgeladenen HTML-Datei, die sich bericht.pdf nennt: Der Browser bekommt keinen Typ
   * genannt, unter dem er sie rendern koennte.
   */
  private static MediaType ausgabetyp(final @Nullable Vorschauart vorschauArt) {
    return vorschauArt == null
        ? MediaType.APPLICATION_OCTET_STREAM
        : MediaType.parseMediaType(vorschauArt.mimeTyp());
  }
}
