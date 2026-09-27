package org.mwolff.fbcrm.angebot.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Zu diesem Angebot gibt es kein archiviertes Dokument (Kriterium 14).
 *
 * <p>Das ist genau der Entwurf: Er traegt nach Kriterium 6 keine Nummer, keine Anschriftskopien und
 * kein PDF — die entstehen erst beim Versenden.
 *
 * <p>409 und nicht 404: Das Angebot gibt es, nur seinen Beleg nicht. Ein 404 laege ueber den
 * Bestand — der Anwender koennte glauben, das Angebot sei verschwunden —, und der Zustand kann
 * sich, wie bei {@link AngebotNichtAenderbar}, hinter seinem Ruecken geaendert haben: Ein Kollege
 * versendet, und dieselbe Anfrage liefert danach ein PDF.
 */
@ResponseStatus(
    code = HttpStatus.CONFLICT,
    reason = "Dieses Angebot ist ein Entwurf und hat noch kein Dokument.")
public final class AngebotOhneBeleg extends RuntimeException {}
