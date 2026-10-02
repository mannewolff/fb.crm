package org.mwolff.fbcrm.angebot.infrastructure;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import org.mwolff.fbcrm.angebot.domain.AnlageSpeicher;
import org.mwolff.fbcrm.angebot.domain.AnlageSpeicherAusfall;
import org.mwolff.fbcrm.config.MinioProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Setzt den Port {@link AnlageSpeicher} auf MinIO um (Plan #150, E3).
 *
 * <p>Den Client baut nicht dieser Adapter, sondern {@code config.S3Config} — er ist der eine Zugang
 * der Anwendung, den jedes Modul teilt. Von dort kommt auch der angelegte Eimer; dieser Adapter
 * setzt ihn voraus und kennt aus {@link MinioProperties} nur seinen Namen.
 *
 * <p>Gestreamt statt als Byte-Feld, anders als der Belegspeicher in {@code
 * rechnung.infrastructure}: Eine Anlage kommt von aussen und darf bis zur Upload-Grenze gross sein
 * — sie ganz in den Arbeitsspeicher zu heben, waere je Aufruf eine Kopie dieser Groesse. Darum
 * reicht {@code ablegen} den Datenstrom mit der bekannten Laenge weiter, und {@code lesen} gibt den
 * Strom des SDK heraus, den der Aufrufer schliesst.
 *
 * <p>Der Schluessel entsteht hier: {@code angebot/<angebotId>/anlage/<uuid>}. Das {@code angebot/}
 * davor trennt die Angebote von kuenftigen Belegarten, das {@code anlage/} die Anlagen von den
 * archivierten Belegen desselben Angebots, und der Zufallsname sorgt dafuer, dass zweimal dieselbe
 * Datei am selben Angebot zwei Objekte ergibt. Ein Teil des Dateinamens steht nicht darin: Er kommt
 * von aussen und waere im Schluessel eine Pfadangabe.
 *
 * <p>Die Ausnahmen des SDK bleiben in diesem Adapter: Jede von ihnen wird als {@link
 * AnlageSpeicherAusfall} weitergegeben, den der Port zusagt. Die Anwendungsschicht muss den
 * Fehlschlag des Loeschens fangen koennen (Plan #150, E9), und dafuer darf sie keine SDK-Klasse
 * kennen (CLAUDE-java.md §6.1). Die Meldung nennt nur den Vorgang, nie einen Dateinamen oder
 * Inhalt.
 */
@Component
class S3AnlageSpeicher implements AnlageSpeicher {

  private final S3Client s3;
  private final String bucket;

  S3AnlageSpeicher(final S3Client s3, final MinioProperties zugang) {
    this.s3 = s3;
    this.bucket = zugang.bucket();
  }

  @Override
  public String ablegen(final long angebotId, final InputStream inhalt, final long groesse) {
    final String objektSchluessel = "angebot/" + angebotId + "/anlage/" + UUID.randomUUID();
    try {
      s3.putObject(
          PutObjectRequest.builder().bucket(bucket).key(objektSchluessel).build(),
          RequestBody.fromInputStream(inhalt, groesse));
    } catch (final SdkException ausfall) {
      throw new AnlageSpeicherAusfall(
          "Der Objektspeicher hat die Anlage nicht angenommen.", ausfall);
    }
    return objektSchluessel;
  }

  @Override
  public Optional<InputStream> lesen(final String objektSchluessel) {
    try {
      return Optional.of(
          s3.getObject(GetObjectRequest.builder().bucket(bucket).key(objektSchluessel).build()));
    } catch (NoSuchKeyException _) {
      return Optional.empty();
    } catch (final SdkException ausfall) {
      throw new AnlageSpeicherAusfall("Der Objektspeicher gab die Anlage nicht heraus.", ausfall);
    }
  }

  @Override
  public void loeschen(final String objektSchluessel) {
    // S3 meldet das Loeschen eines unbekannten Schluessels als Erfolg; der Port sagt genau das zu.
    try {
      s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objektSchluessel).build());
    } catch (final SdkException ausfall) {
      throw new AnlageSpeicherAusfall("Der Objektspeicher hat das Objekt nicht entfernt.", ausfall);
    }
  }
}
